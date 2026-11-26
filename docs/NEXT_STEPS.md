# Remaining work after 0.4.0

## P0 — validate the user-API runtime
- [x] QuickJS context lifecycle and single-thread executor
- [x] LX-style script bootstrap and init status
- [x] Native HTTP request/response and cancellation bridge
- [x] `musicUrl` / `lyric` / `pic` action envelope
- [ ] Build with Android SDK and run on Android 17
- [ ] Regression-test real KW/KG/TX/WY/MG scripts, crypto and buffer behavior
- [ ] Add multipart and binary response compatibility

## P1 — migrate the upstream music business
- [ ] Pin LX Music Mobile 1.9.2 source commit and include all required license/notice files
- [ ] Port `musicSdk` provider modules and their crypto/runtime shims
- [ ] Implement online search, suggestions, playlists, charts and quality selection
- [ ] Connect search results → user-API URL resolution → Media3 playback
- [ ] Port lyric parsing, cover lookup and cache
- [ ] Migrate playlist, favorites, history and settings persistence

## P2 — Android-native experience
- [ ] Replace translucent fallback surfaces with actual Miuix Glass components
- [ ] Implement the two-level backdrop host and runtime-shader fallback per Mishka guide
- [ ] Add MediaSessionService, notification, lock-screen and headset controls
- [ ] Add download management and background task lifecycle
- [ ] Verify Android 17, performance, memory and battery behavior

## Build limitation

The available environment has JDK and Node but no Android SDK or Gradle installation/wrapper. JavaScript syntax was checked with Node; Android compilation and device tests have not been run. Do not treat this archive as a tested APK.
