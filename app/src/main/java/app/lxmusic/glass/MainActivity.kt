package app.lxmusic.glass

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.lxmusic.glass.userapi.UserApiStore
import app.lxmusic.glass.userapi.UserApiRuntime
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101116)
private val Panel = Color(0xFF1B1D25)
private val Accent = Color(0xFFB6E3D0)
private val Muted = Color(0xFF9A9EAA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LXGlassApp() }
    }
}

private enum class Tab(val label: String) {
    Discover("发现"), Library("音乐库"), Search("搜索"), Settings("设置")
}

private data class Song(val title: String, val artist: String, val source: String, val uri: Uri? = null)
@Composable
private fun LXGlassApp() {
    var userApiStatus by remember { mutableStateOf("尚未启用自定义音源") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val userApiRuntime = remember {
        UserApiRuntime(context, object : UserApiRuntime.Listener {
            override fun onEvent(action: String, data: JSONObject) {
                userApiStatus = when (action) {
                    "loaded" -> "脚本已载入，等待初始化结果…"
                    "stopped" -> "自定义音源已停止"
                    "init" -> if (data.optBoolean("status")) {
                        val sources = data.optJSONObject("info")?.optJSONObject("sources")
                        val names = sources?.keys()?.asSequence()?.toList()?.joinToString("、")
                        "音源初始化成功${if (names.isNullOrBlank()) "（未声明可用源）" else "：$names"}"
                    } else "音源初始化失败：${data.optString("errorMessage", "未知错误")}"
                    "error" -> "音源错误：${data.optString("message", "未知错误")}"
                    "updateAlert" -> "音源有更新提示：${data.optString("log")}"
                    else -> userApiStatus
                }
            }
        })
    }
    DisposableEffect(userApiRuntime) { onDispose { userApiRuntime.close() } }
    var tab by remember { mutableStateOf(Tab.Discover) }
    var query by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var status by remember { mutableStateOf("导入本地音频即可开始播放") }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playing = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "本地音频"
            val song = Song(name.substringBeforeLast('.', name), "本地文件", "本地", uri)
            currentSong = song
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
            status = "正在播放本地文件"
        }
    }

    fun togglePlayback() {
        if (currentSong?.uri == null) {
            status = "请先导入一个本地音频文件"
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Accent,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF202A35), Ink, Color(0xFF18221F)))
        )) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                AnimatedContent(tab, label = "page") { page ->
                    when (page) {
                        Tab.Discover -> DiscoverPage(
                            currentSong = currentSong,
                            onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                            onSearch = { tab = Tab.Search }
                        )
                        Tab.Library -> LibraryPage(onPickAudio = { audioPicker.launch(arrayOf("audio/*")) }, currentSong = currentSong)
                        Tab.Search -> SearchPage(query, { query = it }, onPickAudio = { audioPicker.launch(arrayOf("audio/*")) })
                        Tab.Settings -> SettingsPage(status, userApiRuntime, userApiStatus)
                    }
                }
                Spacer(Modifier.weight(1f))
                MiniPlayer(currentSong, playing, ::togglePlayback, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                NavigationBar(
                    containerColor = Color(0xCC171920), contentColor = Muted,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item, onClick = { tab = item },
                            icon = {
                                Icon(when (item) {
                                    Tab.Discover -> Icons.Rounded.Explore
                                    Tab.Library -> Icons.Rounded.LibraryMusic
                                    Tab.Search -> Icons.Rounded.Search
                                    Tab.Settings -> Icons.Rounded.Settings
                                }, contentDescription = item.label)
                            }, label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent,
                                indicatorColor = Color(0x334D806D),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverPage(currentSong: Song?, onPickAudio: () -> Unit, onSearch: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LX MUSIC", color = Accent, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("音乐，随心而听。", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                GlassCircleIcon(Icons.Rounded.Search, onSearch)
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("为你推荐", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("把喜欢的音乐\n留在此刻。", color = Color.White, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Accent, shape = CircleShape, modifier = Modifier.clickable(onClick = onPickAudio)) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FolderOpen, null, tint = Ink)
                                Spacer(Modifier.width(5.dp))
                                Text("导入本地音乐", color = Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(currentSong?.title ?: "尚未选择歌曲", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { SectionTitle("开始使用", "本地播放") }
        item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前版本已接入 Android 本地音频播放", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("点击上方按钮选择 MP3、FLAC、AAC、M4A 等系统可解码的音频文件。", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(onPickAudio: () -> Unit, currentSong: Song?) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("音乐库", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        GlassPanel(Modifier.fillMaxWidth().clickable(onClick = onPickAudio)) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = Accent)
                Spacer(Modifier.width(14.dp))
                Text("导入本地音频", color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Add, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (currentSong != null) {
            Text("当前曲目", color = Muted, fontSize = 13.sp)
            SongRow(currentSong, onClick = {})
        }
        listOf("我喜欢的音乐（待迁移）", "最近播放（待迁移）", "下载管理（待迁移）", "在线歌单（待迁移）").forEachIndexed { index, label ->
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(listOf(Icons.Rounded.Favorite, Icons.Rounded.History, Icons.Rounded.Download, Icons.Rounded.Folder, Icons.Rounded.QueueMusic)[index], null, tint = Accent)
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun SearchPage(query: String, onQuery: (String) -> Unit, onPickAudio: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("搜索音乐", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索歌曲、歌手或专辑…", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Accent) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Color(0x447F8998),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = Accent
            )
        )
        Spacer(Modifier.height(18.dp))
        Text("在线搜索需要接入 LX Music 音源模块", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("当前搜索框为界面占位，不会返回伪造的搜索结果。", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onPickAudio) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("选择本地音频") }
    }
}

@Composable
private fun SettingsPage(status: String, runtime: UserApiRuntime, runtimeStatus: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { UserApiStore(context) }
    var userApis by remember { mutableStateOf(store.list()) }
    var importMessage by remember { mutableStateOf("可导入 LX Music 自定义音源脚本；启用前请确认来源可信") }
    val scriptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 2 * 1024 * 1024) { "音源脚本超过 2 MiB 限制" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("无法读取文件")
                val script = String(bytes, Charsets.UTF_8)
                store.importScript(script)
            }.onSuccess { info ->
                userApis = store.list()
                importMessage = "已导入：${info.name}。点击对应音源的「启用」运行。"
            }.onFailure { error -> importMessage = "导入失败：${error.message ?: "未知错误"}" }
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        listOf("播放设置", "歌词设置", "外观与主题", "关于 LX Music Glass").forEach {
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("自定义音源", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("兼容 LX Music 脚本文件头格式", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("使用 QuickJS 执行 LX Mobile 风格音源脚本；网络请求由 Android 原生 HTTP 层代理。仅启用你信任的脚本。", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { scriptPicker.launch(arrayOf("text/*", "application/javascript", "application/x-javascript", "*/*")) }) {
                        Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("导入音源脚本")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { runtime.stop() }) { Text("停止音源") }
                }
                Spacer(Modifier.height(8.dp))
                Text(importMessage, color = Muted, fontSize = 11.sp)
                Text(runtimeStatus, color = Accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (userApis.isEmpty()) Text("还没有导入音源", color = Muted, fontSize = 13.sp)
        userApis.forEach { api ->
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(api.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(listOf(api.author, api.version).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { api.description }, color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        runCatching { store.readScript(api.id) ?: error("脚本文件不存在") }
                            .onSuccess { runtime.load(api, it); importMessage = "正在启用 ${api.name}" }
                            .onFailure { importMessage = "启用失败：${it.message}" }
                    }) { Text("启用") }
                    IconButton(onClick = { runtime.stop(); store.remove(api.id); userApis = store.list(); importMessage = "已删除 ${api.name}" }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除音源", tint = Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("0.4.0 · $status", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MiniPlayer(song: Song?, playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF42665A), Color(0xFF263A4C)))
            ), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Album, null, tint = Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song?.title ?: "尚未播放", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(song?.artist ?: "导入本地音频开始播放", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Accent)
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF29343A)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(song.artist, color = Muted, fontSize = 12.sp)
        }
        Text(song.source, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(action, color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun GlassCircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0x553D454E))
            .border(1.dp, Color(0x557F9290), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White) }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Safe translucent surface fallback. Miuix Glass dependencies are included;
    // the dedicated Glass components are wired in after API-level integration.
    Column(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xAA303943), Color(0x88303640))))
            .border(1.dp, Color(0x337F9A91), RoundedCornerShape(24.dp)),
        content = content
    )
}
