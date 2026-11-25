package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CloudflareBlockedException(
    val targetUrl: String,
    val statusCode: Int,
    message: String = "jkanime.net requiere verificación de Cloudflare (HTTP $statusCode)."
) : IOException(message)

class JkAnimeHttpClient {

    companion object {
        const val BASE_URL = "https://jkanime.net"
        const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val USER_AGENT = DEFAULT_MOBILE_UA
    }

    @Volatile
    var baseUrl: String = BASE_URL

    @Volatile
    var activeUserAgent: String = DEFAULT_MOBILE_UA

    @Volatile
    var webFetchHandler: (suspend (url: String, referer: String) -> String?)? = null

    @Volatile
    var webRenderedHandler: (suspend (url: String, referer: String, hasRenderedContent: (String) -> Boolean) -> String?)? = null

    @Volatile
    var webSniffHandler: (suspend (url: String, referer: String) -> Pair<String, Map<String, String>>?)? = null

    fun updateBaseUrl(newUrl: String) {
        val cleaned = newUrl.trim().trimEnd('/')
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            baseUrl = cleaned
        }
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val unlockedHtmlCache = ConcurrentHashMap<String, Pair<Long, String>>()

    val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val existing = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(existing) {
                cookies.forEach { newCookie ->
                    existing.removeAll { it.name == newCookie.name }
                    existing.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val result = mutableListOf<Cookie>()
            cookieStore[url.host]?.let { list ->
                synchronized(list) { result.addAll(list) }
            }
            return result
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val http11Client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun injectCookiesFromHeader(url: String, rawCookieHeader: String?, webViewUserAgent: String? = null) {
        if (!webViewUserAgent.isNullOrBlank()) {
            activeUserAgent = webViewUserAgent
        }
        if (rawCookieHeader.isNullOrBlank()) return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val host = httpUrl.host
        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existing) {
            rawCookieHeader.split(";").forEach { part ->
                val eqIdx = part.indexOf('=')
                if (eqIdx > 0) {
                    val name = part.substring(0, eqIdx).trim()
                    val value = part.substring(eqIdx + 1).trim()
                    if (name.isNotBlank()) {
                        existing.removeAll { it.name == name }
                        existing.add(
                            Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(host)
                                .path("/")
                                .build()
                        )
                    }
                }
            }
        }
    }

    fun putUnlockedHtml(url: String, html: String) {
        if (html.isNotBlank()) {
            unlockedHtmlCache[normalizeCacheKey(url)] = System.currentTimeMillis() to html
        }
    }

    private fun consumeUnlockedHtml(url: String): String? {
        val key = normalizeCacheKey(url)
        val entry = unlockedHtmlCache[key] ?: return null
        val ageMs = System.currentTimeMillis() - entry.first
        return if (ageMs <= 120_000L) {
            unlockedHtmlCache.remove(key)
            entry.second
        } else {
            unlockedHtmlCache.remove(key)
            null
        }
    }

    private fun normalizeCacheKey(url: String): String =
        url.trim().trimEnd('/')

    suspend fun fetchHtml(url: String, referer: String = ""): String =
        withContext(Dispatchers.IO) {
            consumeUnlockedHtml(url)?.let { return@withContext it }

            val firstAttempt = executeGet(okHttpClient, url, referer, activeUserAgent, isMobile = true)
            if (!firstAttempt.isBlocked) {
                return@withContext firstAttempt.bodyOrThrow(url)
            }

            // Retry once with HTTP/1.1 + desktop browser headers before automatic Cloudflare verification
            val secondAttempt = executeGet(http11Client, url, referer, DESKTOP_UA, isMobile = false)
            if (!secondAttempt.isBlocked) {
                return@withContext secondAttempt.bodyOrThrow(url)
            }

            // Automatically solve Cloudflare via attached WebView bridge without user intervention
            webFetchHandler?.invoke(url, referer)?.let { bridgedHtml ->
                if (bridgedHtml.isNotBlank() && !isCloudflareHtml(bridgedHtml)) {
                    return@withContext bridgedHtml
                }
            }

            throw CloudflareBlockedException(
                targetUrl = url,
                statusCode = secondAttempt.statusCode
            )
        }

    suspend fun fetchRenderedHtml(
        url: String,
        referer: String = "",
        hasRenderedContent: (String) -> Boolean
    ): String? = withContext(Dispatchers.IO) {
        webRenderedHandler?.invoke(url, referer, hasRenderedContent)
    }

    suspend fun sniffDirectMediaUrl(
        url: String,
        referer: String = ""
    ): Pair<String, Map<String, String>>? = withContext(Dispatchers.IO) {
        webSniffHandler?.invoke(url, referer)
    }

    private data class HttpAttempt(
        val statusCode: Int,
        val isSuccessful: Boolean,
        val body: String,
        val isBlocked: Boolean
    ) {
        fun bodyOrThrow(url: String): String {
            if (!isSuccessful && body.isBlank()) {
                throw IOException("HTTP $statusCode al cargar $url")
            }
            if (body.isBlank()) {
                throw IOException("Respuesta vacía de $url")
            }
            return body
        }
    }

    private fun executeGet(
        client: OkHttpClient,
        url: String,
        referer: String,
        userAgent: String,
        isMobile: Boolean
    ): HttpAttempt {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-CH-UA", "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            .header("Sec-CH-UA-Mobile", if (isMobile) "?1" else "?0")
            .header("Sec-CH-UA-Platform", if (isMobile) "\"Android\"" else "\"Windows\"")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", if (referer.isNotBlank()) "same-origin" else "none")
            .header("Sec-Fetch-User", "?1")

        if (referer.isNotBlank()) {
            builder.header("Referer", referer)
        }

        val request = builder.build()

        return client.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()
            val blocked = code in listOf(403, 429, 503) || isCloudflareHtml(body)
            HttpAttempt(code, response.isSuccessful, body, blocked)
        }
    }

    fun isCloudflareHtml(html: String): Boolean {
        if (html.isBlank()) return false
        return html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("Just a moment...", ignoreCase = true) ||
            html.contains("Un momento...", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true) ||
            html.contains("Attention Required! | Cloudflare", ignoreCase = true) ||
            html.contains("cdn-cgi/challenge-platform", ignoreCase = true) ||
            html.contains("__cf_chl_", ignoreCase = true) ||
            html.contains("cf-turnstile", ignoreCase = true)
    }

    suspend fun fetchJsonOrText(url: String, referer: String = "$BASE_URL/"): String =
        withContext(Dispatchers.IO) {
            consumeUnlockedHtml(url)?.let { return@withContext it }

            fun executeAjaxGet(client: OkHttpClient, userAgent: String): Pair<Int, String> {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", referer)
                    .build()
                return client.newCall(request).execute().use { response ->
                    response.code to response.body?.string().orEmpty()
                }
            }

            val (code1, text1) = executeAjaxGet(okHttpClient, activeUserAgent)
            val blocked1 = code1 in listOf(403, 429, 503) || isCloudflareHtml(text1)
            if (!blocked1 && (code1 in 200..299 || text1.isNotBlank())) {
                return@withContext text1
            }

            val (code2, text2) = executeAjaxGet(http11Client, DESKTOP_UA)
            val blocked2 = code2 in listOf(403, 429, 503) || isCloudflareHtml(text2)
            if (!blocked2 && (code2 in 200..299 || text2.isNotBlank())) {
                return@withContext text2
            }

            if (blocked2) {
                webFetchHandler?.invoke(url, referer)?.let { bridged ->
                    if (bridged.isNotBlank() && !isCloudflareHtml(bridged)) {
                        return@withContext bridged
                    }
                }
                throw CloudflareBlockedException(url, code2)
            }

            if (text2.isBlank()) {
                throw IOException("HTTP $code2 en $url")
            }
            text2
        }

    data class StreamProbeResult(
        val finalUrl: String,
        val isHls: Boolean
    )

    suspend fun probePlayableStream(
        url: String,
        headers: Map<String, String>
    ): StreamProbeResult? = withContext(Dispatchers.IO) {
        try {
            val isLikelyHls = url.contains(".m3u8", ignoreCase = true) ||
                url.contains("master.txt", ignoreCase = true)
            val builder = Request.Builder().url(url)
            if (!isLikelyHls) {
                builder.header("Range", "bytes=0-1023")
            }
            headers.forEach { (k, v) ->
                if (k.equals("User-Agent", true) ||
                    k.equals("Referer", true) ||
                    k.equals("Origin", true) ||
                    k.equals("Cookie", true)
                ) {
                    builder.header(k, v)
                }
            }
            if (!headers.keys.any { it.equals("User-Agent", true) }) {
                builder.header("User-Agent", activeUserAgent)
            }

            okHttpClient.newCall(builder.build()).execute().use { response ->
                val code = response.code
                if (code in listOf(404, 410, 451, 500, 502, 503, 504)) {
                    return@withContext null
                }

                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty().lowercase()

                // If CDN rejects probe with 403/405/416 (e.g. strict Range/method checks) on an explicit media URL, allow ExoPlayer to attempt native playback
                if (code !in 200..299 && code != 206) {
                    val urlIsExplicitMedia = finalUrl.contains(".m3u8", ignoreCase = true) ||
                        finalUrl.contains(".mp4", ignoreCase = true) ||
                        finalUrl.contains(".mpd", ignoreCase = true) ||
                        finalUrl.contains("master.txt", ignoreCase = true) ||
                        finalUrl.contains("okcdn.ru", ignoreCase = true)
                    return@withContext if (urlIsExplicitMedia && !contentType.contains("text/html")) {
                        StreamProbeResult(
                            finalUrl = finalUrl,
                            isHls = finalUrl.contains(".m3u8", ignoreCase = true) ||
                                finalUrl.contains("master.txt", ignoreCase = true)
                        )
                    } else {
                        null
                    }
                }

                val snippetBytes = ByteArray(1024)
                val readCount = response.body?.byteStream()?.use { stream ->
                    var total = 0
                    while (total < snippetBytes.size) {
                        val n = stream.read(snippetBytes, total, snippetBytes.size - total)
                        if (n <= 0) break
                        total += n
                    }
                    total
                } ?: 0

                val headText = if (readCount > 0) {
                    String(snippetBytes, 0, readCount, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                } else {
                    ""
                }
                val headLower = headText.lowercase()

                val isHtmlOrJson = contentType.contains("text/html") ||
                    contentType.contains("application/json") ||
                    contentType.startsWith("image/") ||
                    headLower.startsWith("<!doctype") ||
                    headLower.startsWith("<html") ||
                    headLower.startsWith("<head") ||
                    headLower.startsWith("<script") ||
                    headLower.startsWith("{") ||
                    isCloudflareHtml(headText)

                if (isHtmlOrJson) {
                    return@withContext null
                }

                val isHls = headText.startsWith("#EXTM3U") ||
                    contentType.contains("mpegurl") ||
                    finalUrl.contains(".m3u8", ignoreCase = true) ||
                    finalUrl.contains("master.txt", ignoreCase = true)

                StreamProbeResult(
                    finalUrl = finalUrl,
                    isHls = isHls
                )
            }
        } catch (err: Throwable) {
            val isDnsOrOffline = generateSequence<Throwable>(err) { it.cause }
                .any { it is java.net.UnknownHostException || it is java.net.ConnectException }
            if (isDnsOrOffline) {
                return@withContext null
            }
            val fallbackHls = url.contains(".m3u8", ignoreCase = true) ||
                url.contains("master.txt", ignoreCase = true)
            if (fallbackHls || url.contains(".mp4", ignoreCase = true)) {
                StreamProbeResult(finalUrl = url, isHls = fallbackHls)
            } else {
                null
            }
        }
    }

    fun buildMediaHeaders(referer: String = "$BASE_URL/"): Map<String, String> = mapOf(
        "User-Agent" to activeUserAgent,
        "Referer" to referer,
        "Origin" to (referer.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: BASE_URL)
    )
}
