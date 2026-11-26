# Architecture

```text
Compose UI (Kotlin)
   ├── current translucent Compose surfaces (Miuix Glass replacement pending)
   ├── Media3 ExoPlayer (local playback path)
   └── UserApiRuntime (QuickJS, single JS executor)
          ├── LX-style script preload / lx_setup
          ├── lx.on / lx.send / lx.request
          ├── native OkHttp request proxy
          └── musicUrl / lyric / pic action bridge

Not yet bundled:
   └── LX Music Mobile musicSdk + app core
          ├── online search / playlists / charts
          ├── result normalization / cache
          └── complete player and settings state
```

QuickJS contexts are created and destroyed on a dedicated single-thread executor. Network I/O is asynchronous and responses are posted back to that executor before calling JavaScript. Scripts are user-provided executable code: only enable trusted scripts. The bridge does not expose Android Context, filesystem, shell or root operations; scripts can still make network requests and a runaway synchronous script can occupy its JS thread.

The app currently has a working local-file playback path using Android's Storage Access Framework and Media3 ExoPlayer. The user-API runtime is a meaningful compatibility layer, but it is not a substitute for the upstream `musicSdk` and app core. UI search remains a placeholder until that engine is ported.
