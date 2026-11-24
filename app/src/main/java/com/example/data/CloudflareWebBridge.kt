package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CloudflareWebBridge(
    private val httpClient: JkAnimeHttpClient
) {
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingJsFetches = ConcurrentHashMap<String, CompletableDeferred<Pair<Int, String>>>()

    private var webView: WebView? = null
    private var webViewAttachedSignal = CompletableDeferred<WebView?>()

    @Volatile
    private var hasVerifiedOrigin = false
    @Volatile
    private var rendererDisabled = false
    @Volatile
    private var userDismissedInteractive = false

    private val _bridgeActive = MutableStateFlow(false)
    val bridgeActive: StateFlow<Boolean> = _bridgeActive.asStateFlow()

    private val _needsInteractiveChallenge = MutableStateFlow(false)
    val needsInteractiveChallenge: StateFlow<Boolean> = _needsInteractiveChallenge.asStateFlow()

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainOrCreateWebView(context: Context): WebView? {
        if (rendererDisabled) return null
        webView?.let {
            if (!webViewAttachedSignal.isCompleted) webViewAttachedSignal.complete(it)
            return it
        }

        return try {
            WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
                try {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                } catch (_: Throwable) {
                }

                addJavascriptInterface(JsFetchCallback(), "KitsuneBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        rendererDisabled = true
                        hasVerifiedOrigin = false
                        _needsInteractiveChallenge.value = false
                        _bridgeActive.value = false
                        webView = null
                        pendingJsFetches.values.forEach { it.complete(0 to "") }
                        pendingJsFetches.clear()
                        if (!webViewAttachedSignal.isCompleted) {
                            webViewAttachedSignal.complete(null)
                        }
                        return true
                    }
                }
                webView = this
                if (!webViewAttachedSignal.isCompleted) {
                    webViewAttachedSignal.complete(this)
                }
            }
        } catch (_: Throwable) {
            rendererDisabled = true
            _bridgeActive.value = false
            if (!webViewAttachedSignal.isCompleted) {
                webViewAttachedSignal.complete(null)
            }
            null
        }
    }

    fun dismissInteractiveChallenge() {
        userDismissedInteractive = true
        _needsInteractiveChallenge.value = false
    }

    fun destroy() {
        mainHandler.post {
            try {
                webView?.let { wv ->
                    wv.webViewClient = WebViewClient()
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Throwable) {
            }
            webView = null
            hasVerifiedOrigin = false
            _needsInteractiveChallenge.value = false
            _bridgeActive.value = false
        }
    }

    suspend fun fetchAutomatically(url: String, referer: String): String? =
        mutex.withLock {
            if (rendererDisabled) return@withLock null

            val wv = ensureWebViewAttached() ?: return@withLock null

            // 1. Fast path: if origin already passed Cloudflare, try in-page window.fetch()
            if (hasVerifiedOrigin && isSameOrigin(wv, url)) {
                val fastResult = executeInPageFetch(wv, url)
                if (!fastResult.isCloudflare) {
                    return@withLock fastResult.body.takeIf { fastResult.status in 200..299 && it.isNotBlank() }
                }
            }

            hasVerifiedOrigin = false
            // 2. Navigate WebView to url and wait for Cloudflare challenge to clear automatically
            val navigatedHtml = navigateAndAwaitClearance(wv, url, referer)
            if (navigatedHtml != null) {
                hasVerifiedOrigin = true
                syncCookiesAndUa(wv, url)
            }
            _needsInteractiveChallenge.value = false
            navigatedHtml
        }

    suspend fun fetchRenderedPageHtml(
        url: String,
        referer: String,
        hasRenderedContent: (String) -> Boolean
    ): String? = mutex.withLock {
        if (rendererDisabled) return@withLock null

        val wv = ensureWebViewAttached() ?: return@withLock null
        val renderedHtml = navigateAndAwaitClearance(
            wv = wv,
            url = url,
            referer = referer,
            hasRenderedContent = hasRenderedContent
        )
        if (renderedHtml != null) {
            hasVerifiedOrigin = true
            syncCookiesAndUa(wv, url)
        }
        _needsInteractiveChallenge.value = false
        renderedHtml
    }

    private suspend fun ensureWebViewAttached(): WebView? {
        if (webView == null) {
            if (webViewAttachedSignal.isCompleted) {
                webViewAttachedSignal = CompletableDeferred()
            }
            _bridgeActive.value = true
            withTimeoutOrNull(4_000L) { webViewAttachedSignal.await() }
        }
        return withContext(Dispatchers.Main) { webView }
    }

    private suspend fun isSameOrigin(wv: WebView, targetUrl: String): Boolean =
        withContext(Dispatchers.Main) {
            val currentHost = wv.url?.toHttpUrlOrNull()?.host ?: return@withContext false
            val targetHost = targetUrl.toHttpUrlOrNull()?.host ?: return@withContext false
            currentHost.equals(targetHost, ignoreCase = true)
        }

    private data class InPageFetchResult(
        val status: Int,
        val body: String,
        val isCloudflare: Boolean
    )

    private suspend fun executeInPageFetch(wv: WebView, url: String): InPageFetchResult {
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Pair<Int, String>>()
        pendingJsFetches[reqId] = deferred

        val escapedUrl = url.replace("\\", "\\\\").replace("'", "\\'")
        val script = """
            (function(){
              try {
                fetch('$escapedUrl', { credentials: 'include' })
                  .then(function(r){ return r.text().then(function(t){ window.KitsuneBridge.onFetchResult('$reqId', r.status, t); }); })
                  .catch(function(){ window.KitsuneBridge.onFetchResult('$reqId', 0, ''); });
              } catch(e) {
                window.KitsuneBridge.onFetchResult('$reqId', 0, '');
              }
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            try {
                wv.evaluateJavascript(script, null)
            } catch (_: Throwable) {
                pendingJsFetches.remove(reqId)
                deferred.complete(0 to "")
            }
        }

        val result = withTimeoutOrNull(10_000L) { deferred.await() }
        pendingJsFetches.remove(reqId)
        val (status, body) = result ?: return InPageFetchResult(0, "", isCloudflare = true)
        val blocked = status == 0 || status in listOf(403, 429, 503) || httpClient.isCloudflareHtml(body)
        return InPageFetchResult(status = status, body = body, isCloudflare = blocked)
    }

    private suspend fun navigateAndAwaitClearance(
        wv: WebView,
        url: String,
        referer: String,
        hasRenderedContent: ((String) -> Boolean)? = null
    ): String? {
        userDismissedInteractive = false
        var interactiveShownThisAttempt = false

        withContext(Dispatchers.Main) {
            try {
                val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
                wv.loadUrl(url, headers)
            } catch (_: Throwable) {
            }
        }

        val startMs = System.currentTimeMillis()
        var firstClearedAt = 0L
        var latestNonChallengeSnapshot: String? = null

        while (System.currentTimeMillis() - startMs < 24_000L) {
            if (rendererDisabled || userDismissedInteractive) {
                if (userDismissedInteractive) {
                    withContext(Dispatchers.Main) {
                        runCatching { wv.stopLoading() }
                    }
                }
                return null
            }
            delay(500L)

            val snapshot = readDomSnapshot(wv)
            if (snapshot != null) {
                val isChallenge = httpClient.isCloudflareHtml(snapshot)
                if (!isChallenge && snapshot.length > 600) {
                    latestNonChallengeSnapshot = snapshot
                    if (hasRenderedContent == null || hasRenderedContent(snapshot)) {
                        return snapshot
                    }
                    if (firstClearedAt == 0L) {
                        firstClearedAt = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - firstClearedAt >= 5_000L) {
                        return snapshot
                    }
                }
                if (isChallenge &&
                    !interactiveShownThisAttempt &&
                    !userDismissedInteractive &&
                    System.currentTimeMillis() - startMs > 5_000L
                ) {
                    interactiveShownThisAttempt = true
                    _needsInteractiveChallenge.value = true
                }
            }
        }
        return latestNonChallengeSnapshot
    }

    private suspend fun readDomSnapshot(wv: WebView): String? =
        withTimeoutOrNull(2_000L) {
            withContext(Dispatchers.Main) {
                val d = CompletableDeferred<String?>()
                try {
                    wv.evaluateJavascript(
                        "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})();"
                    ) { raw ->
                        d.complete(unescapeJsHtml(raw))
                    }
                } catch (_: Throwable) {
                    d.complete(null)
                }
                d.await()
            }
        }

    private suspend fun syncCookiesAndUa(wv: WebView, url: String) {
        withContext(Dispatchers.Main) {
            try {
                val ua = wv.settings.userAgentString
                val cookies = CookieManager.getInstance().getCookie(url)
                    ?: CookieManager.getInstance().getCookie(httpClient.baseUrl)
                httpClient.injectCookiesFromHeader(url, cookies, ua)
                CookieManager.getInstance().flush()
            } catch (_: Throwable) {
            }
        }
    }

    private inner class JsFetchCallback {
        @JavascriptInterface
        fun onFetchResult(requestId: String, status: Int, body: String) {
            pendingJsFetches.remove(requestId)?.complete(status to body)
        }
    }

    private fun unescapeJsHtml(raw: String?): String {
        if (raw == null || raw == "null") return ""
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
