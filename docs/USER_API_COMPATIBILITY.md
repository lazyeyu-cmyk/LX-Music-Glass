# LX Music 自定义音源兼容性

## 已接入

- 按 LX Music Mobile 的头部 `/* ... */` 元信息约定导入脚本；保存原文，不改写脚本。
- QuickJS 独立执行上下文；每次启用先销毁旧上下文。
- 兼容脚本入口 `lx_setup(key, id, name, description, version, author, homepage, rawScript)`。
- `lx.on('request', handler)`、`lx.send('inited', info)`、`lx.send('updateAlert', info)`。
- `lx.request(url, options, callback)` 经 OkHttp 执行，回传状态码、状态文本、响应头和 JSON/文本响应体。
- `musicUrl`、`lyric`、`pic` 动作的 request/response 往返协议；超时、取消请求、`setTimeout`。
- MD5、Base64、AES 和 RSA 的基础原生辅助方法。
- JS 执行时不暴露 Android Context、文件系统、Shell、Root 或任意 Java 反射接口。

## 尚未达到原版 1:1 的部分

- 原版 `formData` multipart 上传和 `binary` 原始字节响应尚未完整兼容。
- 原版完整加密/Buffer 边界行为仍需用真实音源脚本逐个回归；AES/RSA 不应被视为所有脚本都已验证。
- 原版内置 musicSdk 的搜索、歌单、排行榜、热门搜索、音质列表等仍未迁移。
- 原版 React Native `global.fetch` / `AbortController` / Buffer polyfill 环境没有整体搬入；这里只实现了音源脚本常用的 `lx.request` 通道。
- 脚本的更新提醒只显示状态，不自动打开链接或自动更新脚本。

## 兼容原则

上游自定义音源脚本是可执行代码。只运行用户主动导入并点击启用的脚本；QuickJS 与宿主通过窄桥接通信，HTTP(S) 请求由原生网络层代理。脚本可访问其自身请求的网络地址，因此仍应只导入可信来源。运行时不提供任意文件读写、Shell、Root、应用私有数据访问能力。
