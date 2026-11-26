package app.lxmusic.glass.userapi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.FormBody;

/** Runs LX Mobile user-API scripts in an isolated QuickJS context. */
public final class UserApiRuntime implements AutoCloseable {
    public interface Listener { void onEvent(String action, JSONObject data); }
    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService jsQueue = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build();
    private final ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<JSONObject>> apiResults = new ConcurrentHashMap<>();
    private volatile QuickJSContext js;
    private volatile String key;
    private volatile boolean closed;

    public UserApiRuntime(Context context, Listener listener) {
        this.app = context.getApplicationContext(); this.listener = listener;
    }

    public void load(UserApiInfo info, String script) {
        jsQueue.execute(() -> {
            cancelPending("音源已切换");
            destroyContext();
            try {
                QuickJSLoader.init(); key = UUID.randomUUID().toString(); js = QuickJSContext.create();
                js.setMemoryLimit(64 * 1024 * 1024);
                js.setMaxStackSize(512 * 1024);
                installNativeBridge();
                String preload = readAsset("script/user-api-preload.js");
                js.evaluate(preload);
                JSFunction setup = js.getGlobalObject().getJSFunction("lx_setup");
                setup.call(key, info.getId(), info.getName(), info.getDescription(), info.getVersion(), info.getAuthor(), info.getHomepage(), script);
                setup.release();
                js.evaluate(script);
                emit("loaded", new JSONObject().put("name", info.getName()));
            } catch (Throwable e) {
                emit("error", new JSONObject().put("message", safeMessage(e)));
                destroyContext();
            }
        });
    }

    public void stop() {
        jsQueue.execute(() -> { cancelPending("音源已停止"); destroyContext(); emit("stopped", new JSONObject()); });
    }

    private void cancelPending(String reason) {
        for (Call call : calls.values()) call.cancel();
        calls.clear();
        for (java.util.concurrent.CompletableFuture<JSONObject> future : apiResults.values()) {
            future.completeExceptionally(new IllegalStateException(reason));
        }
        apiResults.clear();
    }

    /** Call an upstream-compatible script action (musicUrl, lyric, pic). */
    public java.util.concurrent.CompletableFuture<JSONObject> request(String source, String action, JSONObject info) {
        String requestKey = UUID.randomUUID().toString();
        java.util.concurrent.CompletableFuture<JSONObject> future = new java.util.concurrent.CompletableFuture<>();
        apiResults.put(requestKey, future);
        jsQueue.execute(() -> {
            try {
                if (js == null) throw new IllegalStateException("音源尚未启用");
                JSONObject data = new JSONObject().put("source", source).put("action", action).put("info", info);
                callJs("request", new JSONObject().put("requestKey", requestKey).put("data", data));
            } catch (Throwable e) { apiResults.remove(requestKey); future.completeExceptionally(e); }
        });
        timers.schedule(() -> {
            java.util.concurrent.CompletableFuture<JSONObject> f = apiResults.remove(requestKey);
            if (f != null) f.completeExceptionally(new java.util.concurrent.TimeoutException("音源请求超时"));
        }, 25, TimeUnit.SECONDS);
        return future;
    }

    private void installNativeBridge() {
        js.getGlobalObject().setProperty("__lx_native_call__", (JSCallFunction) args -> {
            if (args == null || args.length < 3 || !key.equals(String.valueOf(args[0]))) return null;
            String action = String.valueOf(args[1]); String raw = String.valueOf(args[2]);
            handleNative(action, raw); return null;
        });
        js.getGlobalObject().setProperty("__lx_native_call__set_timeout", (JSCallFunction) args -> {
            long id = ((Number) args[0]).longValue(); long delay = Math.max(0, ((Number) args[1]).longValue());
            timers.schedule(() -> jsQueue.execute(() -> { try { callJs("__timeout__", new JSONObject().put("id", id)); } catch (Exception ignored) {} }), delay, TimeUnit.MILLISECONDS);
            return null;
        });
        js.getGlobalObject().setProperty("__lx_native_call__utils_str2b64", (JSCallFunction) args -> Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        js.getGlobalObject().setProperty("__lx_native_call__utils_b642buf", (JSCallFunction) args -> {
            byte[] b = Base64.decode(String.valueOf(args[0]), Base64.DEFAULT); org.json.JSONArray a = new org.json.JSONArray(); for (byte x : b) a.put(x & 255); return a.toString();
        });
        js.getGlobalObject().setProperty("__lx_native_call__utils_str2md5", (JSCallFunction) args -> md5(URLDecoder.decode(String.valueOf(args[0]), "UTF-8")));
        js.getGlobalObject().setProperty("__lx_native_call__utils_aes_encrypt", (JSCallFunction) args -> aes(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]), String.valueOf(args[3])));
        js.getGlobalObject().setProperty("__lx_native_call__utils_rsa_encrypt", (JSCallFunction) args -> rsa(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2])));
    }

    private void handleNative(String action, String raw) {
        try {
            JSONObject data = new JSONObject(raw);
            switch (action) {
                case "init": emit("init", data); break;
                case "request": startHttp(data); break;
                case "cancelRequest": {
                    String id = data.optString("requestKey"); Call call = calls.remove(id); if (call != null) call.cancel(); break;
                }
                case "response": {
                    String id = data.optString("requestKey"); java.util.concurrent.CompletableFuture<JSONObject> f = apiResults.remove(id);
                    if (f != null) { if (data.optBoolean("status")) f.complete(data.optJSONObject("result")); else f.completeExceptionally(new IllegalStateException(data.optString("errorMessage", "音源请求失败"))); }
                    emit("response", data); break;
                }
                case "showUpdateAlert": emit("updateAlert", data); break;
                default: emit("log", new JSONObject().put("message", "未知音源事件: " + action));
            }
        } catch (Throwable e) { emit("error", new JSONObject().put("message", safeMessage(e))); }
    }

    private void startHttp(JSONObject data) {
        String id = data.optString("requestKey"); String url = data.optString("url"); JSONObject opts = data.optJSONObject("options");
        try {
            if (!(url.startsWith("https://") || url.startsWith("http://"))) throw new IllegalArgumentException("仅允许 HTTP(S) 请求");
            String method = opts == null ? "GET" : opts.optString("method", "GET").toUpperCase();
            Request.Builder builder = new Request.Builder().url(url);
            JSONObject headers = opts == null ? null : opts.optJSONObject("headers");
            if (headers != null) { java.util.Iterator<String> it = headers.keys(); while (it.hasNext()) { String k = it.next(); builder.header(k, headers.optString(k)); } }
            RequestBody body = null;
            if (opts != null && opts.has("body") && !opts.isNull("body")) {
                Object value = opts.get("body"); String content = value instanceof String ? (String)value : value.toString();
                body = RequestBody.create(content, MediaType.parse("application/json; charset=utf-8"));
            } else if (opts != null && opts.has("form")) {
                JSONObject form = opts.getJSONObject("form"); FormBody.Builder fb = new FormBody.Builder(); java.util.Iterator<String> it = form.keys(); while (it.hasNext()) { String k=it.next(); fb.add(k, form.optString(k)); } body=fb.build();
            }
            if (!method.equals("GET") && !method.equals("HEAD") && body == null) body = RequestBody.create(new byte[0], null);
            builder.method(method, body);
            int timeout = opts == null ? 15000 : Math.min(60000, Math.max(1000, opts.optInt("timeout", 15000)));
            OkHttpClient client = http.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build();
            Call call = client.newCall(builder.build()); calls.put(id, call);
            call.enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call c, java.io.IOException e) { calls.remove(id); sendHttpResponse(id, e.getMessage(), null); }
                @Override public void onResponse(Call c, Response response) {
                    try (Response r = response) {
                        String text = r.body() == null ? "" : r.body().string(); Object bodyValue = text;
                        try { bodyValue = new JSONObject(text); } catch (Exception ignored) { try { bodyValue = new org.json.JSONArray(text); } catch (Exception ignored2) {} }
                        JSONObject out = new JSONObject().put("statusCode", r.code()).put("statusMessage", r.message()).put("headers", headersToJson(r)).put("body", bodyValue).put("url", r.request().url().toString());
                        calls.remove(id); sendHttpResponse(id, null, out);
                    } catch (Exception e) { calls.remove(id); sendHttpResponse(id, e.getMessage(), null); }
                }
            });
        } catch (Throwable e) { sendHttpResponse(id, safeMessage(e), null); }
    }

    private JSONObject headersToJson(Response r) throws Exception { JSONObject h = new JSONObject(); for (String n : r.headers().names()) h.put(n, r.header(n)); return h; }
    private void sendHttpResponse(String id, String error, JSONObject response) {
        jsQueue.execute(() -> { try { JSONObject payload = new JSONObject().put("requestKey", id).put("error", error == null ? JSONObject.NULL : error).put("response", response == null ? JSONObject.NULL : response); callJs("response", payload); } catch (Exception ignored) {} });
    }
    private void callJs(String action, JSONObject data) throws Exception {
        if (js == null) throw new IllegalStateException("QuickJS 未初始化");
        JSFunction fn = js.getGlobalObject().getJSFunction("__lx_native__");
        fn.call(key, action, data == null ? null : data.toString()); fn.release();
    }
    private String readAsset(String path) throws Exception { try (InputStream in=app.getAssets().open(path); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) out.write(b,0,n); return out.toString("UTF-8"); } }
    private void emit(String action, JSONObject data) { main.post(() -> { if (!closed && listener != null) listener.onEvent(action, data); }); }
    private void destroyContext() { if (js != null) { try { js.destroy(); } catch (Exception ignored) {} js=null; } }
    private static String safeMessage(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static String md5(String value) throws Exception { byte[] d=MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); }
    private static String aes(String input, String key, String iv, String mode) throws Exception {
        byte[] data=Base64.decode(input,Base64.DEFAULT), k=Base64.decode(key,Base64.DEFAULT); String trans=mode.contains("CBC")?"AES/CBC/PKCS5Padding":"AES/ECB/NoPadding"; Cipher c=Cipher.getInstance(trans); SecretKeySpec sk=new SecretKeySpec(k,"AES");
        if(trans.contains("CBC")) c.init(Cipher.ENCRYPT_MODE,sk,new IvParameterSpec(Base64.decode(iv,Base64.DEFAULT))); else c.init(Cipher.ENCRYPT_MODE,sk);
        return Base64.encodeToString(c.doFinal(data),Base64.NO_WRAP);
    }
    private static String rsa(String input, String key, String padding) throws Exception {
        byte[] der=Base64.decode(key.replaceAll("\\s", ""),Base64.DEFAULT); java.security.PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der)); Cipher c=Cipher.getInstance(padding); c.init(Cipher.ENCRYPT_MODE,pub); return Base64.encodeToString(c.doFinal(Base64.decode(input,Base64.DEFAULT)),Base64.NO_WRAP);
    }
    @Override public void close() { closed=true; jsQueue.execute(() -> { cancelPending("应用已关闭"); destroyContext(); }); jsQueue.shutdown(); timers.shutdownNow(); }
}
