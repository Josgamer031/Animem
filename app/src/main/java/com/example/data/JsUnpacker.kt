package com.example.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object JsUnpacker {

    private val PACKER_REGEX = Pattern.compile(
        """eval\(function\(p,a,c,k,e,[dr]\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""",
        Pattern.DOTALL
    )

    private val M3U8_MP4_REGEX = Regex(
        """(?:https?:)?//[^\s"'<>\\]+?\.(?:m3u8|mp4|mpd)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val FILE_PROP_REGEX = Regex(
        """(?:file|src|url|source|hls|hls2|hls3|hls4|wurl|sd|hd)\s*[:=]\s*["']((?:https?:)?//[^"'\s<>\\]+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val NON_MEDIA_EXTENSIONS = listOf(
        ".js", ".css", ".html", ".htm", ".php", ".png", ".jpg", ".jpeg",
        ".gif", ".webp", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".vtt", ".srt"
    )

    private val INVALID_HOSTS = listOf(
        "example.com", "example.org", "localhost", "127.0.0.1", "0.0.0.0",
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "facebook.com", "googletagmanager.com", "popads.net", "adsterra.com"
    )

    private val BASE64_INV = IntArray(256) { -1 }.apply {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { idx, ch -> this[ch.code] = idx }
    }

    fun decodeBase64(encoded: String): String? {
        return try {
            val cleaned = encoded.trim()
                .replace('-', '+')
                .replace('_', '/')
                .replace("=", "")
                .filter { it.code < 256 && BASE64_INV[it.code] >= 0 }
            if (cleaned.isEmpty()) return null
            val out = ByteArray((cleaned.length * 3) / 4)
            var outIdx = 0
            var buffer = 0
            var bits = 0
            for (ch in cleaned) {
                val val6 = BASE64_INV[ch.code]
                buffer = (buffer shl 6) or val6
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    if (outIdx < out.size) {
                        out[outIdx++] = ((buffer shr bits) and 0xFF).toByte()
                    }
                }
            }
            String(out, 0, outIdx, StandardCharsets.UTF_8).trim()
        } catch (_: Throwable) {
            null
        }
    }

    fun unwrapJkRedirectUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        val uParam = Regex("""[?&]u=([^&]+)""").find(rawUrl)?.groupValues?.getOrNull(1)
        if (!uParam.isNullOrBlank()) {
            val urlDecoded = try {
                URLDecoder.decode(uParam, "UTF-8")
            } catch (_: Throwable) {
                uParam
            }
            if (urlDecoded.startsWith("http")) return urlDecoded
            val b64 = decodeBase64(urlDecoded)
            if (!b64.isNullOrBlank() && b64.startsWith("http")) return b64
        }
        return rawUrl
    }

    fun unpackAll(html: String): String {
        val sb = StringBuilder(html)
        var passInput = html
        repeat(2) {
            val matcher = PACKER_REGEX.matcher(passInput)
            val passOutput = StringBuilder()
            var foundAny = false
            while (matcher.find()) {
                val payload = matcher.group(1) ?: continue
                val radix = matcher.group(2)?.toIntOrNull() ?: continue
                val count = matcher.group(3)?.toIntOrNull() ?: continue
                val symtab = matcher.group(4)?.split('|') ?: continue
                val unpacked = unpack(payload, radix, count, symtab)
                if (unpacked.isNotBlank()) {
                    foundAny = true
                    sb.append('\n').append(unpacked)
                    passOutput.append('\n').append(unpacked)
                }
            }
            if (!foundAny) return sb.toString()
            passInput = passOutput.toString()
        }
        return sb.toString()
    }

    private fun unpack(payload: String, radix: Int, count: Int, symtab: List<String>): String {
        if (symtab.size < count) return ""
        val unescPayload = payload.replace("\\'", "'").replace("\\\\", "\\")
        val tokenRegex = Regex("""\b\w+\b""")
        return tokenRegex.replace(unescPayload) { match ->
            val word = match.value
            val index = parseRadix(word, radix)
            if (index in 0 until symtab.size && symtab[index].isNotEmpty()) {
                symtab[index]
            } else {
                word
            }
        }
    }

    private fun parseRadix(token: String, radix: Int): Int {
        if (radix <= 36) {
            return token.toIntOrNull(radix) ?: -1
        }
        var value = 0
        for (ch in token) {
            val digit = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'z' -> ch - 'a' + 10
                in 'A'..'Z' -> ch - 'A' + 36
                else -> return -1
            }
            if (digit >= radix) return -1
            value = value * radix + digit
        }
        return value
    }

    fun isValidPlayableHttpUrl(rawUrl: String): Boolean {
        val normalized = normalizeStreamUrl(rawUrl)
        val parsed = normalized.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
        if (host.isBlank() || !host.contains('.')) return false
        if (INVALID_HOSTS.any { host == it || host.endsWith(".$it") }) return false
        val path = parsed.encodedPath.lowercase()
        if (NON_MEDIA_EXTENSIONS.any { path.endsWith(it) }) return false
        if (normalized.contains("preview", ignoreCase = true) ||
            normalized.contains("blank.", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    fun unescapeJsString(raw: String): String {
        var out = raw
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003f", "?")
            .replace("\\u003a", ":")
            .replace("\\u002f", "/")
        out = Regex("""\\x([0-9a-fA-F]{2})""").replace(out) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        return out
    }

    fun normalizeStreamUrl(rawUrl: String): String {
        val trimmed = unescapeJsString(rawUrl.trim())
            .trimEnd('"', '\'', ',', ';', ')', ']')
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
    }

    fun extractDirectMediaUrls(rawContent: String): List<String> {
        val unpacked = unescapeJsString(unpackAll(rawContent))
        val results = linkedSetOf<String>()

        M3U8_MP4_REGEX.findAll(unpacked).forEach { match ->
            val candidate = normalizeStreamUrl(match.value)
            if (isValidPlayableHttpUrl(candidate)) {
                results.add(candidate)
            }
        }

        FILE_PROP_REGEX.findAll(unpacked).forEach { match ->
            val candidate = normalizeStreamUrl(match.groupValues[1])
            val lower = candidate.lowercase()
            val isMediaLike = lower.contains(".m3u8") ||
                lower.contains(".mp4") ||
                lower.contains(".mpd") ||
                lower.contains("googlevideo") ||
                lower.contains("okcdn") ||
                lower.contains("master.txt") ||
                lower.contains("get_video") ||
                lower.contains("videoplayback")
            if (isMediaLike && isValidPlayableHttpUrl(candidate)) {
                results.add(candidate)
            }
        }

        // Decode inline atob("...") base64 stream URLs
        Regex("""atob\(\s*["']([A-Za-z0-9+/=_-]{16,})["']\s*\)""").findAll(unpacked).forEach { m ->
            decodeBase64(m.groupValues[1])?.let { decoded ->
                val candidate = normalizeStreamUrl(decoded)
                if (isValidPlayableHttpUrl(candidate) &&
                    (candidate.contains(".m3u8", true) ||
                        candidate.contains(".mp4", true) ||
                        candidate.contains("video", true))
                ) {
                    results.add(candidate)
                }
            }
        }

        return results.toList()
    }
}
