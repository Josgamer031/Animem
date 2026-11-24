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
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val http11Client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
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
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", activeUserAgent)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", referer)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code in listOf(403, 429, 503) || isCloudflareHtml(text)) {
                    webFetchHandler?.invoke(url, referer)?.let { bridged ->
                        if (bridged.isNotBlank() && !isCloudflareHtml(bridged)) {
                            return@withContext bridged
                        }
                    }
                    throw CloudflareBlockedException(url, response.code)
                }
                if (!response.isSuccessful && text.isBlank()) {
                    throw IOException("HTTP ${response.code} en $url")
                }
                text
            }
        }

    fun buildMediaHeaders(referer: String = "$BASE_URL/"): Map<String, String> = mapOf(
        "User-Agent" to activeUserAgent,
        "Referer" to referer,
        "Origin" to (referer.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: BASE_URL)
    )
}
