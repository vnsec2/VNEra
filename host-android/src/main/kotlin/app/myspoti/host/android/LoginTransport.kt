package app.myspoti.host.android

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

internal class LoginTransport(private val view: WebView, private val cookieJar: () -> android.webkit.CookieManager) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build()

    fun install() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addWebMessageListener(view, "ReveraLoginNet", setOf(ORIGIN)) { _, message, origin, mainFrame, _ ->
            if (mainFrame && origin.toString() == ORIGIN) message.data?.let(::receive)
        }
        WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, setOf(ORIGIN))
    }

    private fun receive(raw: String) {
        val m = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val url = m.optString("url")
        val method = m.optString("method")
        if (!allowed(method, url) || m.optString("body").length > 65_536) return
        val id = m.optInt("id", -1)
        val doc = m.optString("doc")
        val given = m.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
            .header("User-Agent", view.settings.userAgentString)
            .header("Origin", ORIGIN)
            .header("Referer", view.url?.takeIf { it.startsWith("$ORIGIN/") } ?: "$ORIGIN/")
        for (key in given.keys()) {
            if (key.lowercase() in setOf("cookie", "host", "content-length", "x-requested-with", "user-agent", "origin", "referer")) continue
            val value = given.optString(key)
            if (value.isNotEmpty()) builder.header(key, value)
        }
        builder.header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("sec-ch-ua", m.optString("brands"))
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"Android\"")
        cookieJar().getCookie(url)?.let { builder.header("Cookie", it) }
        val request = builder.post(m.optString("body").toRequestBody("application/json".toMediaType())).build()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            PlatformStack.fetch(view.context, url, method, request.headers, m.optString("body")) { code, headers, text, _, _ ->
                finish(id, doc, code, text, headers.filter { it.first.equals("set-cookie", true) }.map { it.second })
            }
            return
        }
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = finish(id, doc, 0, "", emptyList())
            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val text = runCatching { r.body.string() }.getOrElse { "" }
                    finish(id, doc, r.code, text, r.headers.values("set-cookie"))
                }
            }
        })
    }

    private fun finish(id: Int, doc: String, status: Int, body: String, cookies: List<String>) {
        view.post {
            cookies.forEach { cookieJar().setCookie(ORIGIN, it) }
            cookieJar().flush()
            AppLog.d("login transport code request -> $status")
            val reply = JSONObject().put("id", id).put("doc", doc).put("status", status).put("body", body)
            view.evaluateJavascript("window.__reveraLoginReply && window.__reveraLoginReply($reply)", null)
        }
    }

    companion object {
        private const val ORIGIN = "https://accounts.spotify.com"
        fun allowed(method: String, url: String): Boolean = runCatching {
            val u = URI(url)
            method == "POST" && u.scheme == "https" && u.host == "accounts.spotify.com" &&
                u.port in listOf(-1, 443) && u.userInfo == null &&
                u.path in listOf("/login/code/send", "/login/code/verify")
        }.getOrDefault(false)

        private val SCRIPT = """
            (() => {
              if (window !== window.top || window.__reveraLoginReply || !window.ReveraLoginNet) return;
              const original = window.fetch.bind(window), pending = new Map();
              const doc = Math.random().toString(36).slice(2); let seq = 0;
              window.__reveraLoginReply = r => {
                if (r.doc !== doc) return;
                const p = pending.get(r.id); if (!p) return;
                clearTimeout(p.timer); pending.delete(r.id);
                if (!r.status) p.reject(new TypeError('Login network request failed'));
                else p.resolve(new Response(r.body, {status:r.status,headers:{'content-type':'application/json'}}));
              };
              window.fetch = async (input, init) => {
                const req = new Request(input, init), u = new URL(req.url);
                if (u.origin !== 'https://accounts.spotify.com' || req.method !== 'POST' ||
                    !['/login/code/send','/login/code/verify'].includes(u.pathname)) return original(input, init);
                const body = await req.clone().text();
                if (body.length > 65536) throw new TypeError('Login request too large');
                return new Promise((resolve,reject) => {
                  const id = ++seq;
                  const timer = setTimeout(() => {pending.delete(id);reject(new TypeError('Login request timed out'));},28000);
                  pending.set(id,{resolve,reject,timer});
                  ReveraLoginNet.postMessage(JSON.stringify({id,doc,url:req.url,method:req.method,headers:Object.fromEntries(req.headers),brands:(navigator.userAgentData?.brands||[]).map(b=>'"'+b.brand+'";v="'+b.version+'"').join(', '),body}));
                });
              };
            })();
        """.trimIndent()
    }
}
