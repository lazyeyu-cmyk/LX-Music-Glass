# LX Music 1.9.2 源码入口映射

已通过上游 `master` 源码树与文件内容核对。`package.json` 标注版本 1.9.2，核心依赖包括 React Native 0.73.11、React 18.2.0、`react-native-track-player`（LX fork）以及多项 RN 原生模块。

| 原业务 | 上游文件 | 迁移说明 |
|---|---|---|
| 统一音乐 URL / 图片 / 歌词入口 | `src/core/music/index.ts` | `getMusicUrl` 按下载、本地、在线三类数据分派；是业务适配层的重要入口 |
| 在线播放地址解析与缓存 | `src/core/music/online.ts` | 依赖 `musicSdk`、设置状态与缓存存储；不能简单复制为独立 Kotlin 文件 |
| 音乐搜索 | `src/core/search/music.ts` | 依赖 `musicSdk[source].musicSearch.search` 和搜索状态 |
| 搜索历史 | `src/core/search/search.ts` | 读写设置状态与持久化数据 |
| 播放服务事件 | `src/plugins/player/service.ts` | 注册远程播放/暂停/上一首/下一首/seek 与播放状态事件 |
| 播放初始化与状态同步 | `src/core/init/player/player.ts` | 负责播放器状态事件、播放结束、自动下一首等 |
| 音源切换 | `src/core/apiSource.ts` | 初始化 musicSdk / 用户 API，并更新全局音源状态 |
| 应用启动 | `src/app.ts` | 动态导入 core/init，初始化设置、导航、播放器等 |

## 重要结论

LX Music 的在线业务并不是几个独立 HTTP 请求。它依赖全局 `global.lx`、`musicSdk`、store、缓存、用户 API 与事件总线；播放服务还依赖 TrackPlayer 的远程控制事件。因此，直接把 Compose UI 接到 ExoPlayer 并不能自动保留原版音源解析、播放列表、音质选择和歌词逻辑。

建议迁移策略：先将音源 SDK 与数据模型抽离为独立 JS engine，再通过稳定的消息协议向 Kotlin 暴露 search / resolve / lyric / playlist / settings 能力；Kotlin 侧负责 Compose UI 与 Media3。对于 TrackPlayer 的原生职责，逐项映射到 Media3 Player + MediaSession，而不是保留 RN UI。

## 本轮完成的验证

- 已读取上游 `package.json`，确认 1.9.2 / RN 0.73.11 / React 18.2.0。
- 已读取上述关键 TS 文件，确认函数入口与关键依赖。
- 尚未取得完整源码工作副本，尚未执行端到端迁移或 Android 构建。

## 自定义音源导入：已核对的上游行为

对照上游 `src/utils/data.ts`、`src/core/userApi.ts`、`src/core/init/userApi/index.ts`、`src/core/init/userApi/request.js` 与 Android `userApi/QuickJS.java`：

- 音源文件不是任意 JSON；上游 `addUserApi()` 要求文件以 `/* ... */` 元信息块开头，读取 `@name`、`@description`、`@author`、`@version`、`@homepage`。
- 上游将脚本原文与音源索引分开存储；索引包含 ID 和元信息，脚本以独立 key 存储。
- 用户脚本由 Android QuickJS wrapper 执行，预加载 `script/user-api-preload.js` 后调用 `lx_setup(...)`。脚本通过 `__lx_native_call__` 发出动作；宿主再处理 request/response、超时和取消。
- 上游请求层使用 fetch 兼容的参数格式，默认 headers、POST form/json、binary response、timeout 等均属于兼容性范围。
- 音源初始化结果会声明 `sources`、每个 source 的 `actions` 和 `qualitys`；核心业务据此构建 `musicUrl`、`lyric`、`pic` 等能力。

0.4.0 已增加 QuickJS 执行器、脚本初始化事件、`lx.request` 的 OkHttp 代理、`musicUrl` / `lyric` / `pic` 请求响应通道和定时器。它仍不是完整移植：原版 `musicSdk` 搜索/歌单等业务、binary/formData 请求和真实音源脚本回归尚未完成。
