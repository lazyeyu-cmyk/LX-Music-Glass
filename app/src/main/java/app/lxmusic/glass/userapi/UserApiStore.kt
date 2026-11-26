package app.lxmusic.glass.userapi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Storage/import compatibility layer for LX Music Mobile user API scripts.
 * Import format follows upstream's leading block-comment metadata convention.
 * Scripts are stored verbatim and are NOT executed by this class.
 */
data class UserApiInfo(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val homepage: String,
    val allowShowUpdateAlert: Boolean = true,
)

class UserApiStore(context: Context) {
    private val root = File(context.filesDir, "lx-user-apis").apply { mkdirs() }
    private val indexFile = File(root, "index.json")

    @Synchronized
    fun list(): List<UserApiInfo> {
        if (!indexFile.exists()) return emptyList()
        val arr = JSONArray(indexFile.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            UserApiInfo(
                id = o.optString("id"), name = o.optString("name"),
                description = o.optString("description"), author = o.optString("author"),
                version = o.optString("version"), homepage = o.optString("homepage"),
                allowShowUpdateAlert = o.optBoolean("allowShowUpdateAlert", true)
            )
        }
    }

    @Synchronized
    fun importScript(script: String): UserApiInfo {
        require(script.toByteArray(Charsets.UTF_8).size <= MAX_SCRIPT_BYTES) { "音源脚本超过 2 MiB 限制" }
        val header = HEADER.find(script)?.value ?: throw IllegalArgumentException("不是 LX Music 音源脚本：缺少文件开头的 /* 元信息 */")
        val meta = mutableMapOf<String, String>()
        HEADER_FIELD.findAll(header).forEach { m ->
            val key = m.groupValues[1]
            if (key in META_LIMITS) meta[key] = m.groupValues[2].trim().take(META_LIMITS.getValue(key))
        }
        val id = "user_api_${UUID.randomUUID()}"
        val info = UserApiInfo(
            id = id,
            name = meta["name"].orEmpty().ifBlank { "自定义音源" },
            description = meta["description"].orEmpty(), author = meta["author"].orEmpty(),
            version = meta["version"].orEmpty(), homepage = meta["homepage"].orEmpty()
        )
        File(root, "$id.js").writeText(script, Charsets.UTF_8)
        val all = list().toMutableList().apply { add(info) }
        writeIndex(all)
        return info
    }

    @Synchronized
    fun readScript(id: String): String? {
        require(id.matches(Regex("user_api_[a-f0-9-]{36}"))) { "无效音源 ID" }
        return File(root, "$id.js").takeIf { it.isFile }?.readText(Charsets.UTF_8)
    }

    @Synchronized
    fun remove(id: String) {
        val updated = list().filterNot { it.id == id }
        File(root, "$id.js").delete()
        writeIndex(updated)
    }

    private fun writeIndex(items: List<UserApiInfo>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("description", item.description)
                put("author", item.author); put("version", item.version); put("homepage", item.homepage)
                put("allowShowUpdateAlert", item.allowShowUpdateAlert)
            })
        }
        val temp = File(root, "index.json.tmp")
        temp.writeText(arr.toString(), Charsets.UTF_8)
        if (!temp.renameTo(indexFile)) {
            indexFile.writeText(arr.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    companion object {
        private const val MAX_SCRIPT_BYTES = 2 * 1024 * 1024
        private val HEADER = Regex("^/\\*[\\s\\S]+?\\*/")
        private val HEADER_FIELD = Regex("(?m)^\\s?\\*\\s?@(\\w+)\\s+(.+)$")
        private val META_LIMITS = mapOf("name" to 24, "description" to 36, "author" to 56, "homepage" to 1024, "version" to 36)
    }
}
