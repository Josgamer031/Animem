package com.example.data

import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.AnimeSchedule
import com.example.model.AnimeSummary
import com.example.model.DirectoryPage
import com.example.model.EpisodeItem
import com.example.model.HomeFeed
import com.example.model.LatestEpisode
import com.example.model.ScheduleDay
import com.example.model.ScheduleItem
import com.example.model.VideoServer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object LatanimeParser {

    const val BASE_URL = "https://latanime.org"

    private val NON_ANIME_PATHS = setOf(
        "animes", "emision", "buscar", "calendario", "genero", "generos",
        "letra", "top", "peliculas", "ovas", "login", "registro", "perfil", "ver"
    )

    fun parseHome(html: String): HomeFeed {
        val doc = Jsoup.parse(html, BASE_URL)

        // 1. Featured / Hero carousel
        val featured = mutableListOf<AnimeSummary>()
        doc.select("#carouselExampleCaptions .carousel-item, div.carousel-item, section.hero .item, div.slider .item")
            .forEach { el ->
                val anchor = el.selectFirst("a[href*='/anime/']") ?: el.selectFirst("a[href]")
                val href = normalizeUrl(anchor?.attr("href").orEmpty())
                val slug = extractAnimeSlug(href) ?: return@forEach
                val title = el.selectFirst("h1, h2, h3, h5, .carousel-caption h5, .carousel-caption h2, .title")
                    ?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: anchor?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
                    ?: slugToTitle(slug)
                val cover = extractImageUrl(el)
                val snippet = el.selectFirst("p, .carousel-caption p")?.text()?.trim().orEmpty()
                if (featured.none { it.slug == slug }) {
                    featured.add(
                        AnimeSummary(
                            slug = slug,
                            title = title,
                            url = "$BASE_URL/anime/$slug",
                            coverUrl = cover,
                            type = "Latino",
                            synopsisSnippet = snippet,
                            provider = AnimeProvider.LATANIME
                        )
                    )
                }
            }

        // 2. Latest Episodes (links to /ver/{episodeSlug})
        val latestEpisodes = mutableListOf<LatestEpisode>()
        val episodeCandidates = doc.select(
            "div.row a[href*='/ver/'], div.cap-layout, div.col-6 a[href*='/ver/'], a[href*='/ver/']"
        )
        episodeCandidates.forEach { el ->
            val anchor = if (el.tagName() == "a") el else el.selectFirst("a[href*='/ver/']")
            val rawHref = normalizeUrl(anchor?.attr("href").orEmpty())
            val epUrl = rawHref.substringBefore("?").substringBefore("#")
            if (!epUrl.contains("/ver/")) return@forEach

            val epSlug = epUrl.substringAfter("/ver/").trimEnd('/')
            if (epSlug.isBlank()) return@forEach

            val container = findCardContainer(el) ?: el
            val epNum = extractEpisodeNumber(epSlug, container.text())
            val animeSlug = deriveAnimeSlugFromEpisode(epSlug)
            val rawTitle = container.selectFirst(".serietitle, h2, h3, h4, h5, h6, .title, span.font-weight-bold, p")
                ?.text()?.trim()
                ?.takeIf { it.isNotBlank() && !it.startsWith("Ep", true) && !it.startsWith("Cap", true) }
                ?: anchor?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
                ?: container.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: slugToTitle(animeSlug)
            val cleanTitle = cleanEpisodeTitle(rawTitle, epNum)
            val cover = extractImageUrl(container).ifBlank { extractImageUrl(el) }
            val epLabel = "Ep. $epNum"

            val existingIdx = latestEpisodes.indexOfFirst { it.episodeUrl == epUrl }
            if (existingIdx == -1) {
                latestEpisodes.add(
                    LatestEpisode(
                        animeSlug = animeSlug,
                        animeTitle = cleanTitle,
                        episodeNumber = epNum,
                        episodeLabel = epLabel,
                        episodeUrl = epUrl,
                        coverUrl = cover,
                        typeLabel = "Latino",
                        provider = AnimeProvider.LATANIME
                    )
                )
            } else if (latestEpisodes[existingIdx].coverUrl.isBlank() && cover.isNotBlank()) {
                latestEpisodes[existingIdx] = latestEpisodes[existingIdx].copy(coverUrl = cover)
            }
        }

        // 3. Separate Airing vs Recently Added anime sections (/anime/{slug})
        val airing = mutableListOf<AnimeSummary>()
        val recentAdded = mutableListOf<AnimeSummary>()

        doc.select("section, div.container, div.row, div.col-lg-4, div.col-lg-8, div.col-12")
            .forEach { container ->
                val heading = container.selectFirst("h1, h2, h3, h4, h5, .section-title, .card-header")
                    ?.text()?.trim()?.lowercase().orEmpty()
                val targetList = when {
                    heading.contains("emisión") || heading.contains("emision") ||
                        heading.contains("en curso") || heading.contains("temporada") -> airing
                    heading.contains("agregad") || heading.contains("recient") ||
                        heading.contains("últimos") || heading.contains("ultimos") ||
                        heading.contains("animes") || heading.contains("catálogo") ||
                        heading.contains("catalogo") -> recentAdded
                    else -> null
                } ?: return@forEach

                container.select("a[href*='/anime/']").forEach { a ->
                    val card = findCardContainer(a) ?: a
                    parseAnimeSummaryCard(a, card)?.let { item ->
                        addOrUpgradeAnime(targetList, item)
                    }
                }
            }

        if (recentAdded.isEmpty()) {
            val airingSlugs = airing.map { it.slug }.toSet()
            doc.select("a[href*='/anime/']").forEach { a ->
                val card = findCardContainer(a) ?: a
                parseAnimeSummaryCard(a, card)?.let { item ->
                    if (item.slug !in airingSlugs) {
                        addOrUpgradeAnime(recentAdded, item)
                    }
                }
            }
        } else if (airing.isNotEmpty()) {
            val airingSlugs = airing.map { it.slug }.toSet()
            val filtered = recentAdded.filter { it.slug !in airingSlugs }
            if (filtered.isNotEmpty()) {
                recentAdded.clear()
                recentAdded.addAll(filtered)
            }
        }

        if (featured.isEmpty()) {
            val fallback = (airing + recentAdded).distinctBy { it.slug }.take(6)
            if (fallback.isNotEmpty()) {
                featured.addAll(fallback)
            } else {
                latestEpisodes.take(6).forEach { ep ->
                    if (featured.none { it.slug == ep.animeSlug }) {
                        featured.add(
                            AnimeSummary(
                                slug = ep.animeSlug,
                                title = ep.animeTitle,
                                url = "$BASE_URL/anime/${ep.animeSlug}",
                                coverUrl = ep.coverUrl,
                                type = "Latino",
                                episodeBadge = ep.episodeLabel,
                                provider = AnimeProvider.LATANIME
                            )
                        )
                    }
                }
            }
        }

        val coverBySlug = (featured + airing + recentAdded)
            .filter { it.coverUrl.isNotBlank() }
            .associate { it.slug to it.coverUrl }

        val enrichedEpisodes = latestEpisodes.map { ep ->
            if (ep.coverUrl.isBlank()) {
                ep.copy(coverUrl = coverBySlug[ep.animeSlug].orEmpty())
            } else {
                ep
            }
        }

        return HomeFeed(
            featured = featured.take(8),
            latestEpisodes = enrichedEpisodes.take(30),
            airing = airing.take(18),
            recentAdded = recentAdded.take(24),
            provider = AnimeProvider.LATANIME
        )
    }

    fun parseDirectoryOrSearch(html: String, currentPage: Int): DirectoryPage {
        val doc = Jsoup.parse(html, BASE_URL)
        val items = mutableListOf<AnimeSummary>()

        doc.select("div.row a[href*='/anime/'], div.seriestu a[href*='/anime/'], a[href*='/anime/']")
            .forEach { a ->
                val card = findCardContainer(a) ?: a
                parseAnimeSummaryCard(a, card)?.let { item ->
                    addOrUpgradeAnime(items, item)
                }
            }

        val hasNextPage = doc.selectFirst(
            "ul.pagination a[rel=next], a.page-link[rel=next], " +
                "a.page-link[aria-label*=Next], a.page-link[aria-label*=Siguiente], " +
                "a:contains(Siguiente), a:contains(›), a:contains(»)"
        )?.let { nextEl ->
            !nextEl.parent()?.hasClass("disabled").let { it == true }
        } == true || doc.select("ul.pagination a, div.pagination a")
            .any { it.text().trim().toIntOrNull() == currentPage + 1 }

        return DirectoryPage(
            items = items,
            currentPage = currentPage,
            hasNextPage = hasNextPage
        )
    }

    fun parseAnimeDetail(slug: String, html: String): AnimeDetail {
        val doc = Jsoup.parse(html, BASE_URL)

        val title = doc.selectFirst("div.row h2, h1, div.serie_info h2, .seriedetails h2")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: slugToTitle(slug)

        val coverUrl = doc.selectFirst("div.serieimg, div.seriedetails, div.col-md-4, div.col-lg-3")
            ?.let { extractImageUrl(it) }
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let(::normalizeUrl).orEmpty()

        val synopsis = doc.select("p.my-2.opacity-75, div.chapter_sinopsis p, p.sinopsis, div.sinopsis p")
            .joinToString("\n\n") { it.text().trim() }
            .trim()
            .ifBlank {
                doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                    ?: "Sin sinopsis disponible."
            }

        val genres = mutableListOf<String>()
        doc.select("a[href*='/genero/'], a[href*='genero='], div.generos a, span.genero")
            .forEach { el ->
                val g = el.text().trim()
                if (g.isNotBlank() && g !in genres) genres.add(g)
            }

        var status = ""
        var type = "Latino"
        var airedInfo = ""

        doc.select("div.row span.badge, div.serie_info span, div.col-md-8 p, ul li").forEach { el ->
            val raw = el.text().trim()
            val lower = raw.lowercase()
            when {
                lower.contains("en emisión") || lower.contains("en emision") -> status = "En emisión"
                lower.contains("finalizado") -> status = "Finalizado"
                lower.contains("próximamente") || lower.contains("estreno") -> status = "Por estrenar"
                lower.startsWith("estado") -> status = raw.substringAfter(":", "").trim().ifBlank { status }
                lower.startsWith("tipo") -> type = raw.substringAfter(":", "").trim().ifBlank { type }
                lower.startsWith("año") || lower.startsWith("fecha") || lower.startsWith("estreno") -> {
                    airedInfo = raw.substringAfter(":", "").trim()
                }
                lower == "película" || lower == "pelicula" -> type = "Película"
                lower == "ova" -> type = "OVA"
            }
        }

        val episodes = mutableListOf<EpisodeItem>()
        doc.select("a[href*='/ver/']").forEachIndexed { idx, a ->
            val href = normalizeUrl(a.attr("href")).substringBefore("?").substringBefore("#")
            if (!href.contains("/ver/")) return@forEachIndexed
            val epSlug = href.substringAfter("/ver/").trimEnd('/')
            val rawText = a.text().trim()
            val num = extractEpisodeNumber(epSlug, rawText, fallback = idx + 1)
            val label = rawText.takeIf { it.isNotBlank() } ?: "Episodio $num"
            val thumb = extractImageUrl(a).ifBlank {
                findCardContainer(a)?.let { extractImageUrl(it) }.orEmpty()
            }
            if (episodes.none { it.number == num }) {
                episodes.add(
                    EpisodeItem(
                        number = num,
                        label = label,
                        url = href,
                        thumbnailUrl = thumb,
                        provider = AnimeProvider.LATANIME
                    )
                )
            }
        }

        val sortedEpisodes = episodes.sortedBy { it.number }

        return AnimeDetail(
            slug = slug,
            animeId = "",
            title = title,
            alternativeTitle = "Español Latino",
            coverUrl = coverUrl,
            synopsis = synopsis,
            status = status,
            type = type,
            airedInfo = airedInfo,
            totalEpisodesText = if (sortedEpisodes.isNotEmpty()) "${sortedEpisodes.size}" else "",
            genres = genres,
            episodes = sortedEpisodes,
            pageRanges = emptyList(),
            provider = AnimeProvider.LATANIME
        )
    }

    fun parseEpisodeServers(html: String, episodeUrl: String): List<VideoServer> {
        val doc = Jsoup.parse(html, episodeUrl)
        val servers = mutableListOf<VideoServer>()

        fun addServer(name: String, rawCandidate: String) {
            val trimmed = rawCandidate.trim().replace("\\/", "/")
            if (trimmed.isBlank() || trimmed == "#") return

            val decoded = if (trimmed.startsWith("http://", true) ||
                trimmed.startsWith("https://", true) ||
                trimmed.startsWith("//") ||
                trimmed.startsWith("/")
            ) {
                normalizeUrl(trimmed)
            } else {
                val base64Decoded = JsUnpacker.decodeBase64(trimmed)?.trim().orEmpty()
                when {
                    base64Decoded.startsWith("http://", true) ||
                        base64Decoded.startsWith("https://", true) ||
                        base64Decoded.startsWith("//") ||
                        base64Decoded.startsWith("/") -> normalizeUrl(base64Decoded)
                    else -> normalizeUrl(trimmed)
                }
            }

            if (decoded.isBlank() || !decoded.startsWith("http")) return
            val label = name.trim()
                .takeIf { it.isNotBlank() && !it.startsWith("opci", true) }
                ?: inferServerName(decoded).takeIf { it != "Servidor" }
                ?: name.trim().ifBlank { "Servidor ${servers.size + 1}" }

            val isInternal = decoded.contains("latanime.org/", ignoreCase = true)
            if (servers.none { it.decodedEmbedUrl == decoded }) {
                servers.add(
                    VideoServer(
                        name = label,
                        rawUrl = trimmed,
                        decodedEmbedUrl = decoded,
                        isInternalJkPlayer = isInternal
                    )
                )
            }
        }

        // 1. Primary Latanime player buttons: [data-player], #play-video, .cap_repro
        doc.select("[data-player], #play-video a, ul.cap_repro li a, a.play-video, [data-video]")
            .forEach { el ->
                val token = el.attr("data-player")
                    .ifBlank { el.attr("data-video") }
                    .ifBlank { el.attr("data-url") }
                    .ifBlank { el.attr("data-src") }
                    .ifBlank { el.attr("href") }
                addServer(el.text(), token)
            }

        // 2. Fallback: embedded iframes on the episode page
        doc.select("iframe[src], iframe[data-src]").forEachIndexed { idx, iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank() &&
                !src.contains("facebook.com", ignoreCase = true) &&
                !src.contains("disqus", ignoreCase = true) &&
                !src.contains("google", ignoreCase = true)
            ) {
                addServer(inferServerName(src).ifBlank { "Servidor ${idx + 1}" }, src)
            }
        }

        return servers
    }

    fun parseHasNextEpisode(html: String, episodeUrl: String, episodeNumber: Int): Boolean? {
        val doc = Jsoup.parse(html, episodeUrl)
        val nextEp = episodeNumber + 1
        val verLinks = doc.select("a[href*='/ver/']")
        if (verLinks.isEmpty()) return null

        val hasNextLink = verLinks.any { a ->
            val href = normalizeUrl(a.attr("href")).substringBefore("?").substringBefore("#")
            val text = a.text().trim().lowercase()
            val epSlug = href.substringAfter("/ver/").trimEnd('/')
            val num = extractEpisodeNumber(epSlug, text, fallback = -1)
            num == nextEp || text.contains("siguiente") || text.contains("próximo") || text.contains("proximo")
        }
        if (hasNextLink) return true

        val hasPrevOrListNav = doc.select("a:contains(Anterior), a:contains(Lista), a[href*='/anime/']").isNotEmpty()
        return if (hasPrevOrListNav) false else null
    }

    private fun parseAnimeSummaryCard(anchor: Element, card: Element): AnimeSummary? {
        val href = normalizeUrl(anchor.attr("href"))
        val slug = extractAnimeSlug(href) ?: return null

        val title = card.selectFirst(".serietitle, h3, h4, h5, .card-title, .title, span.font-weight-bold, p")
            ?.text()?.trim()?.takeIf { it.length > 1 }
            ?: anchor.attr("title").trim().takeIf { it.length > 1 }
            ?: card.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.length > 1 }
            ?: anchor.text().trim().takeIf { it.length > 1 && !it.equals("Ver", true) }
            ?: return null

        val coverUrl = extractImageUrl(card).ifBlank { extractImageUrl(anchor) }
        val badges = card.select("span.badge, .tipo, .badge, .estado, .status")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        val badgeText = badges.firstOrNull() ?: "Latino"
        val statusText = badges.firstOrNull {
            val l = it.lowercase()
            "emision" in l || "emisión" in l || "finalizado" in l || "estreno" in l
        }.orEmpty()

        return AnimeSummary(
            slug = slug,
            title = title,
            url = "$BASE_URL/anime/$slug",
            coverUrl = coverUrl,
            type = badgeText,
            status = statusText,
            provider = AnimeProvider.LATANIME
        )
    }

    private fun addOrUpgradeAnime(list: MutableList<AnimeSummary>, item: AnimeSummary) {
        val idx = list.indexOfFirst { it.slug == item.slug }
        if (idx == -1) {
            list.add(item)
        } else if (list[idx].coverUrl.isBlank() && item.coverUrl.isNotBlank()) {
            list[idx] = list[idx].copy(coverUrl = item.coverUrl)
        }
    }

    private fun findCardContainer(el: Element): Element? {
        var cur: Element? = el
        repeat(4) {
            val candidate = cur ?: return null
            val animeLinks = candidate.select("a[href*='/anime/'], a[href*='/ver/']")
                .map { it.attr("href").substringBefore("?").substringBefore("#").trimEnd('/') }
                .distinct()
            if ((candidate.hasClass("card") ||
                    candidate.hasClass("seriestu") ||
                    candidate.hasClass("cap-layout") ||
                    candidate.tagName() == "article" ||
                    candidate.tagName() == "li" ||
                    candidate.className().contains("col-")) &&
                animeLinks.size <= 1
            ) {
                return candidate
            }
            cur = candidate.parent()
        }
        return el.parent()
    }

    private fun extractAnimeSlug(url: String): String? {
        val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
        val match = Regex("""^https?://[^/]+/anime/([^/]+)$""", RegexOption.IGNORE_CASE).find(clean)
            ?: return null
        val slug = match.groupValues[1].trim()
        if (slug.isBlank() || slug.lowercase() in NON_ANIME_PATHS) return null
        return slug
    }

    private fun deriveAnimeSlugFromEpisode(epSlug: String): String {
        return epSlug
            .replace(Regex("""-(?:episodio|capitulo|cap|ep)-\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""-\d+$"""), "")
            .ifBlank { epSlug }
    }

    private fun extractEpisodeNumber(epSlug: String, text: String, fallback: Int = 1): Int {
        Regex("""(?:episodio|capitulo|cap|ep)[-\s_]*(\d+)""", RegexOption.IGNORE_CASE)
            .find(epSlug)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""-(\d+)$""").find(epSlug)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""(?:episodio|capitulo|cap|ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""\b(\d+)\b""").findAll(text).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.let { return it }
        return fallback
    }

    private fun cleanEpisodeTitle(raw: String, epNum: Int): String {
        return raw.replace(Regex("""\s*(?:Episodio|Capítulo|Capitulo|Cap\.?)\s*$epNum\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { raw }
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

    private fun extractImageUrl(el: Element): String {
        fun isPlaceholder(raw: String): Boolean {
            val l = raw.lowercase()
            return l.isBlank() ||
                l.startsWith("data:") ||
                "blank." in l ||
                "loading" in l ||
                "spinner" in l ||
                "1x1" in l
        }

        val bgAttrs = listOf("data-cfsrc", "data-setbg", "data-bg", "data-background", "data-bgsrc")
        for (attr in bgAttrs) {
            val found = if (el.hasAttr(attr)) el else el.selectFirst("[$attr]")
            val valStr = found?.attr(attr)?.trim().orEmpty()
            if (!isPlaceholder(valStr)) return normalizeUrl(valStr)
        }

        val imgs = if (el.tagName() == "img") listOf(el) else el.select("img")
        for (img in imgs) {
            val candidates = listOf(
                img.attr("data-src"),
                img.attr("data-cfsrc"),
                img.attr("data-lazy-src"),
                img.attr("data-lazy"),
                img.attr("data-original"),
                img.attr("data-echo"),
                img.attr("srcset").substringBefore(",").substringBefore(" "),
                img.attr("src")
            )
            for (c in candidates) {
                val trimmed = c.trim()
                if (!isPlaceholder(trimmed)) return normalizeUrl(trimmed)
            }
        }

        el.select("noscript").forEach { ns ->
            val frag = Jsoup.parseBodyFragment(ns.html())
            frag.selectFirst("img")?.attr("src")?.trim()?.let { src ->
                if (!isPlaceholder(src)) return normalizeUrl(src)
            }
        }

        val styledEls = listOf(el) + el.select("[style*=url]")
        for (styled in styledEls) {
            val style = styled.attr("style")
            Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.getOrNull(1)?.let {
                val trimmed = it.trim()
                if (!isPlaceholder(trimmed)) return normalizeUrl(trimmed)
            }
        }
        return ""
    }

    private fun inferServerName(url: String): String {
        val lower = url.lowercase()
        return when {
            "streamwish" in lower || "sfastwish" in lower || "wishembed" in lower || "awish" in lower -> "StreamWish"
            "filemoon" in lower -> "Filemoon"
            "voe." in lower -> "VOE"
            "vidhide" in lower || "dhtpre" in lower -> "VidHide"
            "mixdrop" in lower || "mdbekjwqa" in lower -> "MixDrop"
            "ok.ru" in lower -> "Ok.ru"
            "mp4upload" in lower -> "MP4Upload"
            "mega.nz" in lower -> "MEGA"
            "dood" in lower -> "DoodStream"
            "uqload" in lower -> "Uqload"
            "streamtape" in lower -> "StreamTape"
            else -> "Servidor"
        }
    }

    fun parseSchedule(html: String): AnimeSchedule {
        val doc = Jsoup.parse(html, BASE_URL)
        val dayDefs = listOf(
            "lunes" to "Lunes",
            "martes" to "Martes",
            "miercoles" to "Miércoles",
            "jueves" to "Jueves",
            "viernes" to "Viernes",
            "sabado" to "Sábado",
            "domingo" to "Domingo"
        )
        val buckets = dayDefs.associate { it.first to mutableListOf<ScheduleItem>() }

        fun normalizeDay(raw: String): String? {
            val clean = raw.lowercase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .trim()
            return dayDefs.firstOrNull { (key, _) ->
                Regex("""\b$key\b""").containsMatchIn(clean) || clean.startsWith(key)
            }?.first
        }

        fun collectCards(container: Element, target: MutableList<ScheduleItem>) {
            container.select("a[href*='/anime/']").forEach { anchor ->
                val card = findCardContainer(anchor) ?: anchor
                val summary = parseAnimeSummaryCard(anchor, card) ?: return@forEach
                val timeText = card.selectFirst(".hora, .time, span.badge, time")
                    ?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: Regex("""\b\d{1,2}:\d{2}\b""").find(card.text())?.value.orEmpty()
                val idx = target.indexOfFirst { it.slug == summary.slug }
                if (idx == -1) {
                    target.add(
                        ScheduleItem(
                            slug = summary.slug,
                            title = summary.title,
                            url = summary.url,
                            coverUrl = summary.coverUrl,
                            releaseTime = timeText,
                            episodeLabel = summary.type,
                            provider = AnimeProvider.LATANIME
                        )
                    )
                } else if (target[idx].coverUrl.isBlank() && summary.coverUrl.isNotBlank()) {
                    target[idx] = target[idx].copy(coverUrl = summary.coverUrl)
                }
            }
        }

        // 1. Bootstrap tab links mapping #paneId -> weekday
        val paneToDay = mutableMapOf<String, String>()
        doc.select("a[href^=#], button[data-bs-target], [data-target]").forEach { tab ->
            val dayKey = normalizeDay(tab.text()) ?: return@forEach
            val targetSelector = tab.attr("href")
                .ifBlank { tab.attr("data-bs-target") }
                .ifBlank { tab.attr("data-target") }
                .trimStart('#')
            if (targetSelector.isNotBlank()) {
                paneToDay[targetSelector] = dayKey
            }
        }
        paneToDay.forEach { (paneId, dayKey) ->
            val paneEl = doc.getElementById(paneId) ?: return@forEach
            val target = buckets[dayKey] ?: return@forEach
            collectCards(paneEl, target)
        }

        // 2. Sections/containers with weekday id, class, or heading
        if (buckets.values.all { it.isEmpty() }) {
            doc.select("div.tab-pane, section, div.card, div.container, div.row > div").forEach { container ->
                val idOrClass = "${container.id()} ${container.className()}"
                val heading = container.selectFirst("h1, h2, h3, h4, h5, .card-header, .title")?.text().orEmpty()
                val dayKey = normalizeDay(idOrClass) ?: normalizeDay(heading) ?: return@forEach
                val childDays = container.select("h2, h3, h4, h5").mapNotNull { normalizeDay(it.text()) }.distinct()
                if (childDays.size > 1) return@forEach
                buckets[dayKey]?.let { collectCards(container, it) }
            }
        }

        // 3. Sibling heading -> grid pattern
        if (buckets.values.all { it.isEmpty() }) {
            doc.select("h2, h3, h4, h5").forEach { heading ->
                val dayKey = normalizeDay(heading.text()) ?: return@forEach
                val target = buckets[dayKey] ?: return@forEach
                var sibling = heading.nextElementSibling()
                while (sibling != null) {
                    val siblingDay = if (sibling.tagName() in listOf("h2", "h3", "h4", "h5")) {
                        normalizeDay(sibling.text())
                    } else null
                    if (siblingDay != null) break
                    collectCards(sibling, target)
                    if (sibling.select("a[href*='/anime/']").isNotEmpty()) break
                    sibling = sibling.nextElementSibling()
                }
            }
        }

        return AnimeSchedule(
            provider = AnimeProvider.LATANIME,
            days = dayDefs.map { (key, label) ->
                ScheduleDay(dayKey = key, dayLabel = label, items = buckets[key].orEmpty())
            }
        )
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.isBlank() -> ""
            t.startsWith("//") -> "https:$t"
            t.startsWith("/") -> "$BASE_URL$t"
            t.startsWith("http://") || t.startsWith("https://") -> t
            else -> "$BASE_URL/$t"
        }
    }
}
