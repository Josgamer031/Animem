package com.example.data

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object JsUnpacker {

    private val PACKER_REGEX = Pattern.compile(
        """eval\(function\(p,a,c,k,e,[dr]\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""",
        Pattern.DOTALL
    )

    private val M3U8_MP4_REGEX = Regex(
        """https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val FILE_PROP_REGEX = Regex(
        """(?:file|src|url|source)\s*[:=]\s*["'](https?://[^"'\s]+)["']""",
        RegexOption.IGNORE_CASE
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
        val matcher = PACKER_REGEX.matcher(html)
        while (matcher.find()) {
            val payload = matcher.group(1) ?: continue
            val radix = matcher.group(2)?.toIntOrNull() ?: continue
            val count = matcher.group(3)?.toIntOrNull() ?: continue
            val symtab = matcher.group(4)?.split('|') ?: continue
            val unpacked = unpack(payload, radix, count, symtab)
            if (unpacked.isNotBlank()) {
                sb.append('\n').append(unpacked)
            }
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

    fun extractDirectMediaUrls(rawContent: String): List<String> {
        val unpacked = unpackAll(rawContent).replace("\\/", "/")
        val results = linkedSetOf<String>()

        M3U8_MP4_REGEX.findAll(unpacked).forEach { match ->
            val candidate = match.value.trimEnd('"', '\'', ',', ';', ')', ']')
            if (!candidate.contains("preview", ignoreCase = true) &&
                !candidate.contains(".jpg", ignoreCase = true) &&
                !candidate.contains(".png", ignoreCase = true)
            ) {
                results.add(candidate)
            }
        }

        FILE_PROP_REGEX.findAll(unpacked).forEach { match ->
            val candidate = match.groupValues[1].trim()
            if (candidate.contains(".m3u8", ignoreCase = true) ||
                candidate.contains(".mp4", ignoreCase = true) ||
                candidate.contains("googlevideo", ignoreCase = true) ||
                candidate.contains("okcdn", ignoreCase = true)
            ) {
                results.add(candidate)
            }
        }

        return results.toList()
    }
}
