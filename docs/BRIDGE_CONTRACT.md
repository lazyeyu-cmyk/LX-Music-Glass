# QuickJS user-API bridge

This describes the implemented user-API runtime in 0.4.0, not a full migration of the LX Music app core.

## Implemented path

1. Kotlin reads a stored script and its parsed metadata.
2. `UserApiRuntime` creates a QuickJS context on one dedicated executor thread.
3. The host installs a narrow native bridge and evaluates the bundled preload.
4. The preload calls `lx_setup(key, id, name, description, version, author, homepage, rawScript)`; the original script is then evaluated.
5. Script `lx.send('inited', info)` is surfaced to the Compose settings UI.
6. Kotlin can call a source action (`musicUrl`, `lyric`, `pic`) through `UserApiRuntime.request`.
7. When script code calls `lx.request`, the host performs HTTP(S) with OkHttp and sends the response back into the same JS context.

## Implemented message actions

- JS → host: `init`, `request`, `cancelRequest`, `response`, `showUpdateAlert`.
- host → JS: `request`, `response`, `__timeout__`.
- Script API: `lx.on('request', handler)`, `lx.send('inited', info)`, `lx.send('updateAlert', info)`, `lx.request(url, options, callback)`.

## Boundaries / not implemented

- The full LX Mobile `musicSdk` search / playlist / chart modules are not included yet.
- `formData` multipart and raw binary response handling are not 1:1.
- Some crypto/Buffer edge cases need validation against real scripts.
- No arbitrary Java reflection, Android context, filesystem, shell or root bridge is provided.
- The JS engine is single-threaded; a malicious infinite synchronous loop cannot currently be preempted by a JS execution deadline.
