'use strict';
// LX Music Mobile user API compatibility preload, adapted for the standalone host.
globalThis.lx_setup = (key, id, name, description, version, author, homepage, rawScript) => {
  const native = globalThis.__lx_native_call__;
  const nativeFuncs = {
    setTimeout: globalThis.__lx_native_call__set_timeout,
    str2b64: globalThis.__lx_native_call__utils_str2b64,
    b642buf: globalThis.__lx_native_call__utils_b642buf,
    md5: globalThis.__lx_native_call__utils_str2md5,
    aes: globalThis.__lx_native_call__utils_aes_encrypt,
    rsa: globalThis.__lx_native_call__utils_rsa_encrypt
  };
  ['__lx_native_call__set_timeout','__lx_native_call__utils_str2b64','__lx_native_call__utils_b642buf','__lx_native_call__utils_str2md5','__lx_native_call__utils_aes_encrypt','__lx_native_call__utils_rsa_encrypt'].forEach(n=>delete globalThis[n]);
  const callNative = (action, data) => native(key, action, JSON.stringify(data ?? null));
  const callbacks = new Map(); let timerId = 0;
  const nativeSetTimeout = nativeFuncs.setTimeout;
  const requestQueue = new Map();
  const sources = ['kw','kg','tx','wy','mg','local'];
  const quality = ['128k','320k','flac','flac24bit'];
  const events = { request: null };
  const eventNames = { request:'request', inited:'inited', updateAlert:'updateAlert' };
  const nativeRequest = (url, options, callback) => {
    const requestKey = String(Math.random()).slice(2) + Date.now();
    requestQueue.set(requestKey, callback);
    callNative('request', { requestKey, url, options });
    return () => { requestQueue.delete(requestKey); callNative('cancelRequest', { requestKey }); };
  };
  const utf8 = (s) => { const out=[]; for (let i=0;i<s.length;i++) { const c=s.charCodeAt(i); if(c<128) out.push(c); else if(c<2048) out.push((c>>6)|192,(c&63)|128); else out.push((c>>12)|224,((c>>6)&63)|128,(c&63)|128); } return new Uint8Array(out); };
  const b64 = (s) => nativeFuncs.str2b64(s);
  const bytesB64 = (data) => { const a = typeof data === 'string' ? utf8(data) : new Uint8Array(data); const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'; let out=''; for(let i=0;i<a.length;i+=3){ const n=(a[i]<<16)|((a[i+1]||0)<<8)|(a[i+2]||0); out+=chars[(n>>18)&63]+chars[(n>>12)&63]+(i+1<a.length?chars[(n>>6)&63]:'=')+(i+2<a.length?chars[n&63]:'='); } return out; };
  const b64Bytes = (s) => new Uint8Array(JSON.parse(nativeFuncs.b642buf(s)));
  const bytesText = (data) => { const a=Array.from(data); let binary=''; for(let i=0;i<a.length;i+=0x4000) binary+=String.fromCharCode(...a.slice(i,i+0x4000)); return decodeURIComponent(escape(binary)); };
  const lx = {
    EVENT_NAMES: eventNames,
    version: '2.0.0', env: 'mobile',
    currentScriptInfo: { name, description, version, author, homepage, rawScript },
    on(event, handler) {
      if (event !== 'request' || typeof handler !== 'function') return Promise.reject(new Error('Unsupported event'));
      events.request = handler; return Promise.resolve();
    },
    send(event, data) {
      if (event === 'inited') { callNative('init', { status:true, info:data }); return Promise.resolve(); }
      if (event === 'updateAlert') { callNative('showUpdateAlert', data); return Promise.resolve(); }
      return Promise.reject(new Error('Unsupported event: ' + event));
    },
    request(url, opts = {}, callback = () => {}) {
      return nativeRequest(url, opts, callback);
    },
    utils: {
      crypto: {
        md5: (s) => nativeFuncs.md5(encodeURIComponent(s)),
        randomBytes: (n) => Uint8Array.from({length:n}, () => Math.floor(Math.random()*256)),
        aesEncrypt: (data, mode, key, iv) => b64Bytes(nativeFuncs.aes(bytesB64(data), bytesB64(key), bytesB64(iv || ''), mode)),
        rsaEncrypt: (data, key) => b64Bytes(nativeFuncs.rsa(bytesB64(data), key, 'RSA/ECB/NoPadding'))
      },
      buffer: {
        from(input, encoding) {
          if (Array.isArray(input)) return new Uint8Array(input);
          if (encoding === 'base64') return b64Bytes(input);
          if (encoding === 'hex') return new Uint8Array((input.match(/.{1,2}/g)||[]).map(x=>parseInt(x,16)));
          return utf8(String(input));
        },
        bufToString(buf, format) {
          const a = Array.from(buf);
          if (format === 'hex') return a.map(x=>x.toString(16).padStart(2,'0')).join('');
          if (format === 'base64') return bytesB64(a);
          return bytesText(a);
        }
      }
    }
  };
  globalThis.lx = lx;
  globalThis.setTimeout = (fn, ms=0, ...args) => {
    const id = timerId++; callbacks.set(id, () => fn(...args));
    nativeSetTimeout(id, ms); return id;
  };
  globalThis.clearTimeout = (id) => { callbacks.delete(id); };
  globalThis.__lx_native__ = (_key, action, raw) => {
    if (_key !== key) return 'Invalid key';
    const data = raw ? JSON.parse(raw) : null;
    if (action === 'response') {
      const cb = requestQueue.get(data.requestKey); requestQueue.delete(data.requestKey);
      if (cb) cb(data.error ? new Error(data.error) : null, data.response, data.response?.body);
    } else if (action === 'request') {
      if (!events.request) { callNative('response', {requestKey:data.requestKey,status:false,errorMessage:'Request event is not defined'}); return; }
      Promise.resolve().then(()=>events.request.call(lx, data.data)).then(result=>{
        let out = result;
        if (data.data.action === 'musicUrl' || data.data.action === 'pic') {
          if (typeof result !== 'string' || !/^https?:/.test(result) || result.length > 2048) throw new Error('Invalid URL result');
          out = data.data.action === 'musicUrl' ? {source:data.data.source,action:data.data.action,data:{type:data.data.info.type,url:result}} : {source:data.data.source,action:data.data.action,data:result};
        }
        callNative('response', {requestKey:data.requestKey,status:true,result:out});
      }).catch(e=>callNative('response',{requestKey:data.requestKey,status:false,errorMessage:String(e?.message||e)}));
    } else if (action === '__timeout__') { const cb=callbacks.get(data.id); callbacks.delete(data.id); cb?.(); }
  };
  Object.freeze(lx);
  globalThis.eval = () => { throw new Error('eval is not available'); };
  const OriginalFunction = globalThis.Function;
  const blockedFunction = new Proxy(OriginalFunction, { apply(){ throw new Error('Dynamic code execution is not allowed.'); }, construct(){ throw new Error('Dynamic code execution is not allowed.'); } });
  try { Object.defineProperty(OriginalFunction.prototype, 'constructor', { value: blockedFunction, writable:false, configurable:false }); } catch (_) {}
  globalThis.Function = blockedFunction;
};
