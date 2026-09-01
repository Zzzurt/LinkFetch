package com.linkfetch.app.data.parser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 抖音 WebView 兜底提取器。
 *
 * 背景：抖音分享页的 `window._ROUTER_DATA` 已不再内嵌作品数据，且所有免 Cookie 的
 * HTTP 接口（分享页 / PC 详情 / SEO / iteminfo / slidesinfo）均被风控拦截；
 * 作品数据现在只出现在桌面版详情页的 SSR `__pace_f` RSC 数据流中，
 * 而该页面要求浏览器执行 `__ac_nonce` 挑战后获得的 ttwid 等 Cookie 才返回。
 *
 * 方案：用隐形 WebView（真浏览器引擎，自动通过 JS 挑战）加载桌面版详情页，
 * 注入 JS 从 `__pace_f` 中括号配平提取 `aweme` 对象（camelCase 结构），
 * 经 encodeURIComponent 返回，由 [DouyinParser] 解析。
 */
class DouyinWebViewExtractor(private val context: Context) {

    /** 加载页面并提取作品 JSON，成功返回 aweme 对象 JSON 字符串，失败返回 null。 */
    suspend fun extractAwemeJson(pageUrl: String, cookie: String? = null): String? =
        withTimeoutOrNull(TIMEOUT_MS) { extractInternal(pageUrl, cookie) }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractInternal(pageUrl: String, cookie: String?): String? =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            val done = AtomicBoolean(false)

            fun complete(result: String?) {
                if (done.compareAndSet(false, true)) {
                    cont.resume(result)
                }
            }

            val task = object : Runnable {
                lateinit var webView: WebView
                var polls = 0
                var pageFinished = false

                fun destroy() {
                    main.removeCallbacks(this)
                    if (this::webView.isInitialized) {
                        runCatching {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                }

                /** 页面加载完成后轮询提取；超时或成功后销毁 WebView。 */
                override fun run() {
                    if (done.get()) return
                    if (!pageFinished) {
                        Log.d(TAG, "waiting pageFinished url=$pageUrl")
                        main.postDelayed(this, POLL_INTERVAL_MS)
                        return
                    }
                    polls++
                    if (polls > MAX_POLLS) {
                        Log.w(TAG, "max polls reached, giving up url=$pageUrl")
                        destroy()
                        complete(null)
                        return
                    }
                    webView.evaluateJavascript(EXTRACT_JS) { value ->
                        val payload = decodeEvaluateResult(value)
                        Log.d(TAG, "extract poll=$polls rawPayload=${payload != null} evalLen=${value?.length ?: -1}")
                        if (payload != null) {
                            Log.d(TAG, "PAYLOAD_START=${payload.take(300)}")
                            Log.d(TAG, "PAYLOAD_HAS_DESC=${payload.contains("\"desc\"")} HAS_IMAGES=${payload.contains("\"images\"")} HAS_VIDEO=${payload.contains("\"video\"")}")
                            destroy()
                            complete(payload)
                        } else {
                            main.postDelayed(this, POLL_INTERVAL_MS)
                        }
                    }
                }
            }

            main.post {
                if (done.get()) return@post
                val webView = try {
                    WebView(context)
                } catch (e: Exception) {
                    Log.e(TAG, "webview create failed", e)
                    complete(null)
                    return@post
                }
                webView.apply {
                    @Suppress("DEPRECATION")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.blockNetworkImage = true
                    settings.userAgentString = DESKTOP_UA
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "pageFinished url=$url")
                            task.pageFinished = true
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?,
                        ) {
                            Log.w(TAG, "onReceivedError code=$errorCode desc=$description url=$failingUrl")
                        }
                    }
                }
                Log.d(TAG, "loading url=$pageUrl")
                task.webView = webView
                // 用户配置的抖音 Cookie 注入 WebView，提升过风控成功率
                if (!cookie.isNullOrBlank()) {
                    runCatching {
                        android.webkit.CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setCookie("https://www.douyin.com", cookie)
                            setCookie("https://www.iesdouyin.com", cookie)
                            flush()
                        }
                    }
                }
                webView.loadUrl(pageUrl)
                main.postDelayed(task, POLL_INTERVAL_MS)
            }

            cont.invokeOnCancellation {
                main.post { task.destroy() }
            }
        }

    private fun decodeEvaluateResult(value: String?): String? {
        // evaluateJavascript 把 JS 返回值编码为 JSON：null → "null"，字符串 → 带引号的转义串。
        // 提取结果只含百分号编码字符（encodeURIComponent），直接去引号后 URL 解码即可。
        if (value == null || value == "null") return null
        val quoted = value.trim()
        if (quoted.length < 2 || quoted.first() != '"') return null
        val encoded = quoted.substring(1, quoted.length - 1)
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
    }

    private companion object {
        private const val TAG = "DouyinWebView"

        const val TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 600L
        const val MAX_POLLS = 40

        val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** 从 __pace_f RSC 流中提取 "aweme":{"statusCode":..,"detail":{...}} 对象。 */
        const val EXTRACT_JS = """
            (function() {
                try {
                    var chunks = window.__pace_f || [];
                    var raw = '';
                    for (var i = 0; i < chunks.length; i++) {
                        var c = chunks[i];
                        if (c && c.length > 1 && typeof c[1] === 'string') raw += c[1];
                    }
                    if (!raw) return null;
                    var marker = '"aweme":{"statusCode":';
                    var idx = raw.indexOf(marker);
                    if (idx < 0) return null;
                    var start = raw.indexOf('{', idx + 9);
                    if (start < 0) return null;
                    var depth = 0, inStr = false, esc = false;
                    for (var j = start; j < raw.length; j++) {
                        var ch = raw.charAt(j);
                        if (esc) { esc = false; continue; }
                        if (ch === '\\') { esc = true; continue; }
                        if (ch === '"') { inStr = !inStr; continue; }
                        if (inStr) continue;
                        if (ch === '{') depth++;
                        else if (ch === '}') {
                            depth--;
                            if (depth === 0) return encodeURIComponent(raw.substring(start, j + 1));
                        }
                    }
                    return null;
                } catch (e) { return null; }
            })()
        """
    }
}
