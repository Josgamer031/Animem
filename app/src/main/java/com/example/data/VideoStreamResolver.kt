package com.example.data

import com.example.model.ResolvedVideo
import com.example.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class VideoStreamResolver(
    private val httpClient: JkAnimeHttpClient
) {

    suspend fun resolve(
        server: VideoServer,
        episodePageUrl: String,
        providerBaseUrl: String = JkAnimeHttpClient.BASE_URL
    ): ResolvedVideo =
        withContext(Dispatchers.IO) {
            val targetEmbed = server.decodedEmbedUrl.ifBlank { server.rawUrl }
            val referer = if (server.isInternalJkPlayer) episodePageUrl else "${providerBaseUrl.trimEnd('/')}/"

            // 1. Try direct HTTP + P.A.C.K.E.R. JS resolution for native ExoPlayer
            val directAttempt = tryDirectHttpExtract(targetEmbed, referer, providerBaseUrl)
            if (directAttempt != null) {
                return@withContext ResolvedVideo(
                    serverName = server.name,
                    streamUrl = directAttempt.first,
                    embedUrl = targetEmbed,
                    isDirectStream = true,
                    headers = httpClient.buildMediaHeaders(directAttempt.second)
                )
            }

            // 2. If embed itself is already a direct .m3u8/.mp4 media URL
            if (isDirectMediaUrl(targetEmbed)) {
                return@withContext ResolvedVideo(
                    serverName = server.name,
                    streamUrl = targetEmbed,
                    embedUrl = targetEmbed,
                    isDirectStream = true,
                    headers = httpClient.buildMediaHeaders(referer)
                )
            }

            // 3. Fallback to in-app Ad-Blocking WebView Theater
            ResolvedVideo(
                serverName = server.name,
                streamUrl = null,
                embedUrl = targetEmbed,
                isDirectStream = false,
                headers = httpClient.buildMediaHeaders(referer)
            )
        }

    private suspend fun tryDirectHttpExtract(
        embedUrl: String,
        referer: String,
        providerBaseUrl: String
    ): Pair<String, String>? {
        return try {
            val html = httpClient.fetchHtml(embedUrl, referer = referer)

            // Check for nested iframe inside JKAnime/Latanime wrapper (e.g. um.php / reproductor)
            val doc = Jsoup.parse(html, embedUrl)
            val nestedIframe = doc.selectFirst("iframe[src], iframe[data-src]")
                ?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                ?.takeIf { it.isNotBlank() && it != embedUrl }
            if (nestedIframe != null && !nestedIframe.contains("google") && !nestedIframe.contains("facebook")) {
                val fullNested = normalizeWithBase(nestedIframe, providerBaseUrl)
                val nestedCandidates = tryExtractFromHtml(fullNested, referer = embedUrl)
                if (nestedCandidates != null) return nestedCandidates to fullNested
            }

            // Special Ok.ru data-options JSON handling
            if (embedUrl.contains("ok.ru", ignoreCase = true) || embedUrl.contains("jkokru", ignoreCase = true)) {
                extractOkRuStream(html)?.let { return it to embedUrl }
            }

            // Special VOE base64 / HLS handling
            if (embedUrl.contains("voe.", ignoreCase = true)) {
                extractVoeStream(html)?.let { return it to embedUrl }
            }

            // General P.A.C.K.E.R. + regex media extraction
            val direct = JsUnpacker.extractDirectMediaUrls(html)
                .sortedByDescending { it.contains(".m3u8", ignoreCase = true) }
                .firstOrNull()
            if (direct != null) {
                return direct to embedUrl
            }

            // Check <video><source src="...">
            val sourceTag = doc.selectFirst("video source[src], video[src]")?.attr("src")
            if (!sourceTag.isNullOrBlank()) {
                return normalizeWithBase(sourceTag, embedUrl) to embedUrl
            }

            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeWithBase(raw: String, baseUrl: String): String {
        val t = raw.trim()
        val cleanBase = baseUrl.trimEnd('/')
        return when {
            t.isBlank() -> ""
            t.startsWith("//") -> "https:$t"
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.startsWith("/") -> {
                val origin = Regex("""^(https?://[^/]+)""").find(cleanBase)?.groupValues?.getOrNull(1)
                    ?: cleanBase
                "$origin$t"
            }
            else -> "$cleanBase/$t"
        }
    }

    private suspend fun tryExtractFromHtml(url: String, referer: String): String? {
        return try {
            val html = httpClient.fetchHtml(url, referer = referer)
            JsUnpacker.extractDirectMediaUrls(html)
                .sortedByDescending { it.contains(".m3u8", ignoreCase = true) }
                .firstOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractOkRuStream(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            val dataOptions = doc.selectFirst("[data-options]")?.attr("data-options").orEmpty()
            if (dataOptions.isBlank()) return null
            val root = JSONObject(dataOptions)
            val flashvars = root.optJSONObject("flashvars") ?: return null
            val metadataRaw = flashvars.optString("metadata")
            if (metadataRaw.isBlank()) return null
            val metadata = JSONObject(metadataRaw)
            val ondemandHls = metadata.optString("hlsManifestUrl")
            if (ondemandHls.isNotBlank()) return ondemandHls
            val videos: JSONArray = metadata.optJSONArray("videos") ?: return null
            var bestUrl: String? = null
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val name = v.optString("name")
                val url = v.optString("url")
                if (url.isNotBlank()) {
                    bestUrl = url
                    if (name == "hd" || name == "full") break
                }
            }
            bestUrl
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractVoeStream(html: String): String? {
        val hlsMatch = Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.getOrNull(1)
        if (!hlsMatch.isNullOrBlank()) {
            if (hlsMatch.startsWith("http")) return hlsMatch
            JsUnpacker.decodeBase64(hlsMatch)?.takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    companion object {
        fun isDirectMediaUrl(url: String): Boolean {
            if (!url.startsWith("http")) return false
            val lower = url.lowercase()
            val pathPart = lower.substringBefore("?")
            return pathPart.endsWith(".m3u8") ||
                pathPart.endsWith(".mp4") ||
                lower.contains(".m3u8?") ||
                lower.contains(".mp4?") ||
                lower.contains("master.txt")
        }

        fun isAdOrTrackerUrl(url: String): Boolean {
            val lower = url.lowercase()
            return listOf(
                "doubleclick", "googlesyndication", "adservice", "popads",
                "popcash", "propellerads", "exoclick", "juicyads", "adsterra",
                "onclickalgo", "monetag", "histats", "analytics"
            ).any { it in lower }
        }
    }
}
