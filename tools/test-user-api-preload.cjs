const fs = require('fs');
const path = require('path');
const vm = require('vm');
const events = [];
const key = 'smoke-test-key';
const ctx = vm.createContext({
  console,
  __lx_native_call__: (k, action, raw) => {
    if (k !== key) throw new Error('native key mismatch');
    events.push({ action, data: JSON.parse(raw) });
  },
  __lx_native_call__set_timeout: () => {},
  __lx_native_call__utils_str2b64: s => Buffer.from(s).toString('base64'),
  __lx_native_call__utils_b642buf: s => JSON.stringify([...Buffer.from(s, 'base64')]),
  __lx_native_call__utils_str2md5: s => require('crypto').createHash('md5').update(decodeURIComponent(s)).digest('hex'),
  __lx_native_call__utils_aes_encrypt: () => '',
  __lx_native_call__utils_rsa_encrypt: () => ''
});
const preload = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/script/user-api-preload.js'), 'utf8');
vm.runInContext(preload, ctx);
ctx.lx_setup(key, 'user_api_test', 'Test API', '', '1.0', 'test', '', '');
vm.runInContext(`
  lx.on('request', async ({source, action, info}) => 'https://example.com/audio.mp3');
  lx.send('inited', {sources:{kw:{type:'music',actions:['musicUrl'],qualitys:['320k']}}});
`, ctx);
if (events[0]?.action !== 'init') throw new Error('init handshake failed');
ctx.__lx_native__(key, 'request', JSON.stringify({requestKey:'music-1',data:{source:'kw',action:'musicUrl',info:{type:'320k'}}}));
setTimeout(() => {
  const music = events.find(e => e.action === 'response' && e.data.requestKey === 'music-1');
  if (!music?.data.status || music.data.result?.data?.url !== 'https://example.com/audio.mp3') throw new Error('musicUrl bridge failed');
  vm.runInContext(`lx.request('https://example.com/test', {}, (err, response) => { globalThis.requestCallback = !err && response.statusCode === 200 && response.body === 'ok'; });`, ctx);
  const request = events.find(e => e.action === 'request');
  if (!request || request.data.url !== 'https://example.com/test') throw new Error('lx.request native request failed');
  ctx.__lx_native__(key, 'response', JSON.stringify({requestKey:request.data.requestKey,error:null,response:{statusCode:200,statusMessage:'OK',headers:{},body:'ok'}}));
  if (ctx.requestCallback !== true) throw new Error('lx.request callback failed');
  console.log('QuickJS preload protocol smoke test passed: init, musicUrl, native HTTP callback');
}, 25);
