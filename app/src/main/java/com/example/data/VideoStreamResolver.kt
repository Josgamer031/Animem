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
        providerBaseUrl: String = JkAnimeHttpClient.BASE_URL,
        allowWebSniff: Boolean = false,
        forceWebSniff: Boolean = false
    ): ResolvedVideo =
        withContext(Dispatchers.IO) {
            val targetEmbed = JsUnpacker.normalizeStreamUrl(
                server.decodedEmbedUrl.ifBlank { server.rawUrl }
            )
            val referer = if (server.isInternalJkPlayer) episodePageUrl else "${providerBaseUrl.trimEnd('/')}/"

            // 1. If embed itself is already a direct .m3u8/.mp4 media URL
            if (!forceWebSniff && isDirectMediaUrl(targetEmbed) && JsUnpacker.isValidPlayableHttpUrl(targetEmbed)) {
                val headers = httpClient.buildMediaHeaders(referer)
                val probe = httpClient.probePlayableStream(targetEmbed, headers)
                if (probe != null) {
                    return@withContext ResolvedVideo(
                        serverName = server.name,
                        streamUrl = probe.finalUrl,
                        embedUrl = targetEmbed,
                        isDirectStream = true,
                        headers = headers,
                        usedWebSniff = false,
                        isHls = probe.isHls
                    )
                }
            }

            // 2. Fast static HTTP + specialized host & P.A.C.K.E.R. extraction
            if (!forceWebSniff) {
                val directAttempt = tryDirectHttpExtract(targetEmbed, referer, providerBaseUrl)
                if (directAttempt != null && JsUnpacker.isValidPlayableHttpUrl(directAttempt.first)) {
                    val headers = httpClient.buildMediaHeaders(directAttempt.second)
                    val probe = httpClient.probePlayableStream(directAttempt.first, headers)
                    if (probe != null) {
                        return@withContext ResolvedVideo(
                            serverName = server.name,
                            streamUrl = probe.finalUrl,
                            embedUrl = targetEmbed,
                            isDirectStream = true,
                            headers = headers,
                            usedWebSniff = false,
                            isHls = probe.isHls
                        )
                    }
                }
            }

            // 3. Live WebView network & DOM stream sniffer so JS-obfuscated hosts also play natively
            if (allowWebSniff || forceWebSniff) {
                val sniffed = httpClient.sniffDirectMediaUrl(targetEmbed, referer)
                if (sniffed != null && JsUnpacker.isValidPlayableHttpUrl(sniffed.first)) {
                    val probe = httpClient.probePlayableStream(sniffed.first, sniffed.second)
                    if (probe != null) {
                        return@withContext ResolvedVideo(
                            serverName = server.name,
                            streamUrl = probe.finalUrl,
                            embedUrl = targetEmbed,
                            isDirectStream = true,
                            headers = sniffed.second,
                            usedWebSniff = true,
                            isHls = probe.isHls
                        )
                    }
                }
            }

            // 4. Fallback to in-app Ad-Blocking WebView Theater
            ResolvedVideo(
                serverName = server.name,
                streamUrl = null,
                embedUrl = targetEmbed,
                isDirectStream = false,
                headers = httpClient.buildMediaHeaders(referer),
                usedWebSniff = allowWebSniff || forceWebSniff
            )
        }

    private suspend fun tryDirectHttpExtract(
        embedUrl: String,
        referer: String,
        providerBaseUrl: String,
        depth: Int = 0
    ): Pair<String, String>? {
        if (depth > 3) return null
        return try {
            val html = httpClient.fetchHtml(embedUrl, referer = referer)
            val doc = Jsoup.parse(html, embedUrl)

            // Special Ok.ru data-options JSON handling
            if (embedUrl.contains("ok.ru", ignoreCase = true) || embedUrl.contains("jkokru", ignoreCase = true)) {
                extractOkRuStream(html)?.let { return JsUnpacker.normalizeStreamUrl(it) to embedUrl }
            }

            // Special VOE HLS / MP4 / base64 handling
            if (embedUrl.contains("voe.", ignoreCase = true)) {
                extractVoeStream(html)?.let { return JsUnpacker.normalizeStreamUrl(it) to embedUrl }
            }

            // Special MixDrop MDCore.wurl handling (even inside packed JS)
            if (embedUrl.contains("mixdrop", ignoreCase = true) || embedUrl.contains("mdbekjwqa", ignoreCase = true)) {
                extractMixDropStream(html)?.let { return it to embedUrl }
            }

            // Special DoodStream pass_md5 handling
            if (embedUrl.contains("dood", ignoreCase = true) || embedUrl.contains("ds2play", ignoreCase = true)) {
                extractDoodStream(html, embedUrl)?.let { return it to embedUrl }
            }

            // Special StreamTape get_video handling
            if (embedUrl.contains("streamtape", ignoreCase = true)) {
                extractStreamTapeStream(html)?.let { return it to embedUrl }
            }

            // Check direct <video><source src="...">
            val sourceTag = doc.selectFirst("video source[src], video[src]")?.attr("src")
            if (!sourceTag.isNullOrBlank()) {
                val resolvedSrc = normalizeWithBase(sourceTag, embedUrl)
                if (JsUnpacker.isValidPlayableHttpUrl(resolvedSrc)) {
                    return resolvedSrc to embedUrl
                }
            }

            // General P.A.C.K.E.R. + atob + regex media extraction
            val direct = JsUnpacker.extractDirectMediaUrls(html)
                .sortedByDescending { it.contains(".m3u8", ignoreCase = true) }
                .firstOrNull()
            if (direct != null) {
                return direct to embedUrl
            }

            // Check for nested iframe inside JKAnime/Latanime wrapper (e.g. um.php / jk.php / reproductor)
            val nestedIframe = doc.selectFirst("iframe[src], iframe[data-src]")
                ?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                ?.takeIf { it.isNotBlank() && it != embedUrl }
            if (nestedIframe != null && !nestedIframe.contains("google") && !nestedIframe.contains("facebook")) {
                val fullNested = normalizeWithBase(nestedIframe, providerBaseUrl)
                tryDirectHttpExtract(fullNested, referer = embedUrl, providerBaseUrl = providerBaseUrl, depth = depth + 1)
                    ?.let { return it }
            }

            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractMixDropStream(html: String): String? {
        val unpacked = JsUnpacker.unpackAll(html).replace("\\/", "/")
        val wurl = Regex("""MDCore\.wurl\s*=\s*["']([^"']+)["']""")
            .find(unpacked)?.groupValues?.getOrNull(1)
            ?: Regex("""wurl\s*=\s*["']((?:https?:)?//[^"']+)["']""")
                .find(unpacked)?.groupValues?.getOrNull(1)
        return wurl?.let(JsUnpacker::normalizeStreamUrl)?.takeIf(JsUnpacker::isValidPlayableHttpUrl)
    }

    private suspend fun extractDoodStream(html: String, embedUrl: String): String? {
        return try {
            val passMatch = Regex("""['"](/pass_md5/[^'"]+)['"]""").find(html)?.groupValues?.getOrNull(1)
                ?: return null
            val token = passMatch.substringAfterLast("/")
            val passUrl = normalizeWithBase(passMatch, embedUrl)
            val prefix = httpClient.fetchJsonOrText(passUrl, referer = embedUrl).trim()
            if (!prefix.startsWith("http")) return null
            val suffix = "abcdefghij?token=$token&expiry=${System.currentTimeMillis()}"
            val candidate = prefix + suffix
            candidate.takeIf(JsUnpacker::isValidPlayableHttpUrl)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractStreamTapeStream(html: String): String? {
        val linkRegex = Regex("""robotlink['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*\(?['"]([^'"]+)['"]""")
        val match = linkRegex.find(html) ?: return null
        val part1 = match.groupValues[1]
        val part2 = match.groupValues[2]
        val suffix = if (part2.length > 3 && part2.startsWith("xcd")) part2.substring(3) else part2
        val combined = JsUnpacker.normalizeStreamUrl(part1 + suffix)
        return combined.takeIf(JsUnpacker::isValidPlayableHttpUrl)
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
        val unpacked = JsUnpacker.unpackAll(html)
        val hlsMatch = Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(unpacked)?.groupValues?.getOrNull(1)
        if (!hlsMatch.isNullOrBlank()) {
            if (hlsMatch.startsWith("http")) return hlsMatch
            JsUnpacker.decodeBase64(hlsMatch)?.takeIf { it.startsWith("http") }?.let { return it }
        }
        val mp4Match = Regex("""['"]mp4['"]\s*:\s*['"]([^'"]+)['"]""").find(unpacked)?.groupValues?.getOrNull(1)
        if (!mp4Match.isNullOrBlank()) {
            if (mp4Match.startsWith("http")) return mp4Match
            JsUnpacker.decodeBase64(mp4Match)?.takeIf { it.startsWith("http") }?.let { return it }
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
                pathPart.endsWith(".mpd") ||
                lower.contains(".m3u8?") ||
                lower.contains(".mp4?") ||
                lower.contains("master.txt")
        }

        private val AD_URL_PATTERNS = listOf(
            "doubleclick", "googlesyndication", "googleadservices", "adservice",
            "popads", "popcash", "propellerads", "propush", "exoclick", "exosrv",
            "realsrv", "juicyads", "adsterra", "topcreativeformat", "highperformanceformat",
            "onclickalgo", "onclkds", "monetag", "alwingulla", "hilltopads",
            "admaven", "clickadu", "trafficjunky", "magsrv", "tsyndicate", "acscdn",
            "adtng", "adsco", "adskeeper", "mgid", "taboola", "outbrain", "histats",
            "analytics", "clarity.ms", "mc.yandex", "bet365", "1xbet", "caliente.",
            "pin-up", "mostbet", "stake.com", "roobet", "chaturbate", "stripchat",
            "camsoda", "bongacams", "livejasmin", "pushame", "notifpush", "pushtimize",
            "wpadmngr", "bannerflow", "bidswitch", "pubmatic", "rubiconproject",
            "openx.net", "criteo", "adnxs", "smartadserver", "richaudience",
            "vidoomy", "teads.tv", "sharethrough", "improvedigital", "triplelift",
            "spotxchange", "springserve", "lkqd", "aniview", "brightcom",
            "pubfuture", "mediavine", "ezoic", "revenuehits", "adcash", "bidvertiser",
            "infolinks", "ero-advertising", "plugrush", "trafficfactory", "traffichaus",
            "popunder", "clicktrack", "/vast", "vpaid", "adsystem", "ads-twitter",
            "facebook.com/tr", "connect.facebook.net", "luckyorange", "hotjar",
            "shorte.st", "adf.ly", "ouo.io", "linkvertise", "sub2unlock",
            "whourray", "dolohen", "pushwhy", "o333o", "zergnet", "adnium",
            "betteradsystem", "2mdn.net", "moatads", "scorecardresearch"
        )

        fun isAdOrTrackerUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.startsWith("intent://") ||
                lower.startsWith("market://") ||
                lower.startsWith("mailto:") ||
                lower.startsWith("tel:")
            ) {
                return true
            }
            return AD_URL_PATTERNS.any { it in lower }
        }

        fun isTrustedEmbedNavigation(targetUrl: String, currentEmbedUrl: String): Boolean {
            val lower = targetUrl.lowercase()
            if (isAdOrTrackerUrl(lower)) return false
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
            val targetHost = extractHost(lower)
            val embedHost = extractHost(currentEmbedUrl.lowercase())
            if (targetHost.isBlank() || embedHost.isBlank()) return false
            if (targetHost == embedHost) return true
            val targetRoot = rootDomain(targetHost)
            val embedRoot = rootDomain(embedHost)
            if (targetRoot == embedRoot) return true
            // Allow transitions directly into nested legitimate video player hosts
            val knownPlayerHosts = listOf(
                "jkanime", "latanime", "streamwish", "wish", "filemoon", "voe",
                "vidhide", "dhtpre", "mixdrop", "ok.ru", "mp4upload", "dood",
                "ds2play", "uqload", "streamtape", "sendvid", "yourupload", "fembed"
            )
            return knownPlayerHosts.any { it in targetHost }
        }

        private fun extractHost(url: String): String =
            url.substringAfter("://", "").substringBefore("/").substringBefore(":").lowercase()

        private fun rootDomain(host: String): String {
            val parts = host.split(".").filter { it.isNotBlank() }
            return if (parts.size >= 2) "${parts[parts.size - 2]}.${parts.last()}" else host
        }
    }
}
