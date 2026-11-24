package com.example.data

import com.example.model.AnimeDetail
import com.example.model.AnimeSummary
import com.example.model.DirectoryPage
import com.example.model.EpisodeItem
import com.example.model.EpisodePageRange
import com.example.model.HomeFeed
import com.example.model.LatestEpisode
import com.example.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object JkAnimeParser {

    private const val BASE_URL = "https://jkanime.net"

    private val NON_ANIME_SLUGS = setOf(
        "directorio", "buscar", "horario", "top", "genero", "letra",
        "estrenos", "peliculas", "ovas", "onas", "especiales", "anime",
        "login", "registro", "perfil", "historia", "terminos", "privacidad",
        "dmca", "contacto", "user", "ajax", "cdn-cgi"
    )

    fun parseHome(html: String): HomeFeed {
        val doc = Jsoup.parse(html, BASE_URL)

        // 1. Featured / Hero items
        val featured = mutableListOf<AnimeSummary>()
        doc.select("section.hero div.hero__items, div.hero__slider div.hero__items, div.carousel-item")
            .forEach { el ->
                val link = el.selectFirst("a[href]") ?: return@forEach
                val href = normalizeUrl(link.attr("href"))
                val slug = extractSlugFromAnimeUrl(href) ?: return@forEach
                val title = el.selectFirst("h2, h3, h5, .hero__text h2")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() } ?: link.text().trim()
                val cover = extractImageUrl(el)
                val snippet = el.selectFirst("p")?.text()?.trim().orEmpty()
                val type = el.selectFirst(".label, span.badge")?.text()?.trim().orEmpty()
                if (title.isNotBlank() && featured.none { it.slug == slug }) {
                    featured.add(
                        AnimeSummary(
                            slug = slug,
                            title = title,
                            url = "$BASE_URL/$slug/",
                            coverUrl = cover,
                            type = type,
                            synopsisSnippet = snippet
                        )
                    )
                }
            }

        // 2. Latest Episodes (supports both classic and updated JKAnime home layouts)
        val latestEpisodes = mutableListOf<LatestEpisode>()
        val episodeCards = doc.select(
            "div.trending__anime div.anime__item, " +
                "div.maximos a, " +
                "div.listadoanime-home a.bloqq, " +
                "section.contenido div.row div.col-lg-2 a, " +
                "div.anime_programacion a, " +
                "div.col-lg-8 div.anime__item, " +
                "div.programacion div.card, " +
                "div.anime_item"
        )
        episodeCards.forEach { card ->
            val anchor = if (card.tagName() == "a") card else card.selectFirst("a[href]")
            val href = normalizeUrl(anchor?.attr("href").orEmpty())
            val cleanHref = href.substringBefore("?").substringBefore("#")
            val epMatch = Regex("""https?://[^/]+/([^/]+)/(\d+)/?$""").find(cleanHref)
            if (epMatch != null) {
                val slug = epMatch.groupValues[1]
                val epNum = epMatch.groupValues[2].toIntOrNull() ?: return@forEach
                if (slug in NON_ANIME_SLUGS) return@forEach

                val parent = card.parent()
                val title = card.selectFirst("h5, h6, .anime__item__text h5 a, .title, strong")
                    ?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && !it.startsWith("Ep", true) }
                    ?: anchor?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
                    ?: parent?.selectFirst("h5, h6, .title")?.text()?.trim()
                        ?.takeIf { it.isNotBlank() && !it.startsWith("Ep", true) }
                    ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

                val epLabel = card.selectFirst(".ep, h6, span.badge, .anime__item__pic .ep")
                    ?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Episodio $epNum"

                val cover = extractImageUrl(card).ifBlank {
                    parent?.let { extractImageUrl(it) }.orEmpty()
                }
                val typeLabel = card.selectFirst("ul li, .tipo, .comment")?.text()?.trim().orEmpty()

                val existingIdx = latestEpisodes.indexOfFirst { it.animeSlug == slug && it.episodeNumber == epNum }
                if (existingIdx == -1) {
                    latestEpisodes.add(
                        LatestEpisode(
                            animeSlug = slug,
                            animeTitle = title,
                            episodeNumber = epNum,
                            episodeLabel = epLabel,
                            episodeUrl = cleanHref,
                            coverUrl = cover,
                            typeLabel = typeLabel
                        )
                    )
                } else if (latestEpisodes[existingIdx].coverUrl.isBlank() && cover.isNotBlank()) {
                    latestEpisodes[existingIdx] = latestEpisodes[existingIdx].copy(coverUrl = cover)
                }
            }
        }

        // Fallback if latestEpisodes still empty: scan all anchors matching /{slug}/{number}/
        if (latestEpisodes.isEmpty()) {
            doc.select("a[href]").forEach { a ->
                val href = normalizeUrl(a.attr("href")).substringBefore("?").substringBefore("#")
                val m = Regex("""https?://[^/]+/([^/]+)/(\d+)/?$""").find(href)
                    ?: return@forEach
                val slug = m.groupValues[1]
                val epNum = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (slug in NON_ANIME_SLUGS) return@forEach
                val parent = a.parent()
                val title = a.selectFirst("h5, h6, span")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && !it.startsWith("Ep", true) }
                    ?: a.attr("title").trim().takeIf { it.isNotBlank() }
                    ?: parent?.selectFirst("h5, h6, .title")?.text()?.trim()
                        ?.takeIf { it.isNotBlank() && !it.startsWith("Ep", true) }
                    ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
                val cover = extractImageUrl(a).ifBlank {
                    parent?.let { extractImageUrl(it) }.orEmpty()
                }
                val existingIdx = latestEpisodes.indexOfFirst { it.animeSlug == slug && it.episodeNumber == epNum }
                if (existingIdx == -1) {
                    latestEpisodes.add(
                        LatestEpisode(
                            animeSlug = slug,
                            animeTitle = title,
                            episodeNumber = epNum,
                            episodeLabel = "Ep. $epNum",
                            episodeUrl = href,
                            coverUrl = cover
                        )
                    )
                } else if (latestEpisodes[existingIdx].coverUrl.isBlank() && cover.isNotBlank()) {
                    latestEpisodes[existingIdx] = latestEpisodes[existingIdx].copy(coverUrl = cover)
                }
            }
        }

        // 3. Airing vs Recent Added: classify distinctly by section heading and container
        val airing = mutableListOf<AnimeSummary>()
        val recentAdded = mutableListOf<AnimeSummary>()

        val sectionContainers = doc.select(
            "section, div.trending__product, div.recent__product, div.popular__product, " +
                "div.anime__sidebar__comment, div.product__sidebar__comment, div.anime__sidebar__view, " +
                "div.side-box, div.col-lg-4 > div, div.col-lg-8 > div"
        )
        sectionContainers.forEach { container ->
            val heading = container.selectFirst("h2, h3, h4, h5, .section-title, .card-header")
                ?.text()?.trim()?.lowercase().orEmpty()
            val targetList = when {
                heading.contains("emisión") || heading.contains("emision") ||
                    heading.contains("programación") || heading.contains("programacion") ||
                    heading.contains("en curso") -> airing
                heading.contains("agregad") || heading.contains("recient") ||
                    heading.contains("últimos animes") || heading.contains("ultimos animes") ||
                    heading.contains("nuevos") || heading.contains("estreno") -> recentAdded
                else -> null
            } ?: return@forEach

                container.select("div.anime__item, div.product__item, div.card, div.anime__sidebar__comment__item, div.product__sidebar__comment__item, li, a[href]")
                    .forEach { el ->
                        parseAnimeSummaryElement(el)?.let { item ->
                            val existingIdx = targetList.indexOfFirst { it.slug == item.slug }
                            if (existingIdx == -1) {
                                targetList.add(item)
                            } else if (targetList[existingIdx].coverUrl.isBlank() && item.coverUrl.isNotBlank()) {
                                targetList[existingIdx] = targetList[existingIdx].copy(coverUrl = item.coverUrl)
                            }
                        }
                    }
        }

        // Explicit sidebar airing fallback (classic JKAnime sidebar)
        if (airing.isEmpty()) {
            doc.select(
                "div.anime__sidebar__comment__item, div.product__sidebar__comment__item, " +
                    "div.anime__sidebar__view__item, div.side-box a"
            ).forEach { el ->
                parseAnimeSummaryElement(el)?.let { item ->
                    val existingIdx = airing.indexOfFirst { it.slug == item.slug }
                    if (existingIdx == -1) {
                        airing.add(item)
                    } else if (airing[existingIdx].coverUrl.isBlank() && item.coverUrl.isNotBlank()) {
                        airing[existingIdx] = airing[existingIdx].copy(coverUrl = item.coverUrl)
                    }
                }
            }
        }

        // General recent anime cards fallback, strictly excluding items already placed in airing
        if (recentAdded.isEmpty()) {
            val airingSlugs = airing.map { it.slug }.toSet()
            doc.select("div.recent__product div.anime__item, div.anime__item, div.product__item, div.card")
                .forEach { el ->
                    parseAnimeSummaryElement(el)?.let { item ->
                        if (item.slug !in airingSlugs) {
                            val existingIdx = recentAdded.indexOfFirst { it.slug == item.slug }
                            if (existingIdx == -1) {
                                recentAdded.add(item)
                            } else if (recentAdded[existingIdx].coverUrl.isBlank() && item.coverUrl.isNotBlank()) {
                                recentAdded[existingIdx] = recentAdded[existingIdx].copy(coverUrl = item.coverUrl)
                            }
                        }
                    }
                }
        } else {
            // Also ensure recentAdded and airing don't display identical lists
            val airingSlugs = airing.map { it.slug }.toSet()
            val filteredRecent = recentAdded.filter { it.slug !in airingSlugs }
            if (filteredRecent.isNotEmpty()) {
                recentAdded.clear()
                recentAdded.addAll(filteredRecent)
            }
        }

        // Ensure featured has rich items even if hero slider wasn't rendered in static HTML
        if (featured.isEmpty()) {
            val fallbackFeatured = (airing + recentAdded).distinctBy { it.slug }.take(6)
            if (fallbackFeatured.isNotEmpty()) {
                featured.addAll(fallbackFeatured)
            } else {
                latestEpisodes.take(6).forEach { ep ->
                    if (featured.none { it.slug == ep.animeSlug }) {
                        featured.add(
                            AnimeSummary(
                                slug = ep.animeSlug,
                                title = ep.animeTitle,
                                url = "$BASE_URL/${ep.animeSlug}/",
                                coverUrl = ep.coverUrl,
                                episodeBadge = ep.episodeLabel,
                                type = ep.typeLabel
                            )
                        )
                    }
                }
            }
        }

        // Cross-enrich missing cover images across all Home sections by animeSlug
        val coverBySlug = buildMap {
            featured.forEach { if (it.coverUrl.isNotBlank()) put(it.slug, it.coverUrl) }
            airing.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.slug, it.coverUrl) }
            recentAdded.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.slug, it.coverUrl) }
            latestEpisodes.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.animeSlug, it.coverUrl) }
        }
        val enrichedEpisodes = latestEpisodes.map { ep ->
            if (ep.coverUrl.isBlank()) {
                val matchCover = coverBySlug[ep.animeSlug].orEmpty()
                if (matchCover.isNotBlank()) ep.copy(coverUrl = matchCover) else ep
            } else {
                ep
            }
        }
        val enrichedAiring = airing.map { item ->
            if (item.coverUrl.isBlank()) {
                val matchCover = coverBySlug[item.slug].orEmpty()
                if (matchCover.isNotBlank()) item.copy(coverUrl = matchCover) else item
            } else item
        }
        val enrichedRecent = recentAdded.map { item ->
            if (item.coverUrl.isBlank()) {
                val matchCover = coverBySlug[item.slug].orEmpty()
                if (matchCover.isNotBlank()) item.copy(coverUrl = matchCover) else item
            } else item
        }

        return HomeFeed(
            featured = featured.take(8),
            latestEpisodes = enrichedEpisodes.take(30),
            airing = enrichedAiring.take(18),
            recentAdded = enrichedRecent.take(24)
        )
    }

    fun parseDirectoryOrSearch(html: String, currentPage: Int): DirectoryPage {
        val doc = Jsoup.parse(html, BASE_URL)
        val items = mutableListOf<AnimeSummary>()

        // 1. Parse embedded JSON in <script> tags (used by JKAnime Vue/Laravel /directorio & /buscar)
        val embeddedMeta = parseEmbeddedDirectoryJson(html, items)

        // 2. Parse rendered DOM cards across classic and modern JKAnime themes
        val cards = doc.select(
            "div.anime__page__content div.anime__item, " +
                "div.results div.anime__item, " +
                "div.custom_item2, " +
                "div.dir__item, " +
                "div.anime__item, " +
                "div.product__item, " +
                "div.card, " +
                "article.anime, " +
                "div.row > div[class*=col-]"
        )

        cards.forEach { card ->
            parseAnimeSummaryElement(card)?.let { item ->
                if (items.none { it.slug == item.slug }) {
                    items.add(item)
                }
            }
        }

        // 3. Broad fallback scan for anime links with images or data-setbg
        if (items.isEmpty()) {
            doc.select("a[href]").forEach { a ->
                val href = normalizeUrl(a.attr("href"))
                val slug = extractSlugFromAnimeUrl(href) ?: return@forEach
                val container = a.parent()
                val cover = extractImageUrl(a).ifBlank {
                    container?.let { extractImageUrl(it) }.orEmpty()
                }
                val title = a.selectFirst("h5, h4, h3, .title, .card-title")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: a.attr("title").trim().takeIf { it.isNotBlank() }
                    ?: container?.selectFirst("h5, h4, h3, .title, .card-title")?.text()?.trim()
                        ?.takeIf { it.isNotBlank() }
                    ?: a.text().trim().takeIf { it.length > 2 && !it.equals("Ver", true) }
                    ?: ""
                if (cover.isNotBlank() && title.isNotBlank() && items.none { it.slug == slug }) {
                    items.add(
                        AnimeSummary(
                            slug = slug,
                            title = title,
                            url = "$BASE_URL/$slug/",
                            coverUrl = cover
                        )
                    )
                }
            }
        }

        val hasNextPage = embeddedMeta.hasNextPage || doc.selectFirst(
            "a.next, a[rel=next], a.next-page, div.anime__pagination a.nav-next, " +
                "ul.pagination li.next a, ul.pagination a[rel=next], " +
                "a.page-link[aria-label*=Next], a:contains(Siguiente), button:contains(Siguiente)"
        ) != null || doc.select("div.anime__pagination a, ul.pagination a, div.pagination a")
            .any { it.text().trim().toIntOrNull() == currentPage + 1 }

        return DirectoryPage(
            items = items,
            currentPage = currentPage,
            hasNextPage = hasNextPage
        )
    }

    private data class EmbeddedDirectoryMeta(val hasNextPage: Boolean = false)

    private fun parseEmbeddedDirectoryJson(
        html: String,
        out: MutableList<AnimeSummary>
    ): EmbeddedDirectoryMeta {
        var hasNextPage = false
        val lastPageMatch = Regex("""["']last_page["']\s*:\s*(\d+)""").find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val currentPageMatch = Regex("""["']current_page["']\s*:\s*(\d+)""").find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (lastPageMatch != null && currentPageMatch != null && currentPageMatch < lastPageMatch) {
            hasNextPage = true
        }
        val nextUrlMatch = Regex("""["']next_page_url["']\s*:\s*["']([^"']+)["']""").find(html)
        if (nextUrlMatch != null) {
            hasNextPage = true
        }

        val candidates = mutableListOf<String>()
        val arrayPatterns = listOf(
            Regex("""(?:var|let|const)\s+(?:animes|data|list|results)\s*=\s*(\[\s*\{.*?\}\s*\])\s*;""", RegexOption.DOT_MATCHES_ALL),
            Regex("""["']data["']\s*:\s*(\[\s*\{.*?\}\s*\])""", RegexOption.DOT_MATCHES_ALL),
            Regex(""":animes=["'](\[\s*\{.*?\}\s*\])["']""", RegexOption.DOT_MATCHES_ALL)
        )
        arrayPatterns.forEach { regex ->
            regex.findAll(html).forEach { m ->
                candidates.add(m.groupValues[1])
            }
        }

        for (raw in candidates) {
            val cleaned = raw.replace("&quot;", "\"")
            try {
                val arr = Json.parseToJsonElement(cleaned).jsonArray
                for (el in arr) {
                    val obj = el.jsonObject
                    val rawSlug = obj["slug"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val slug = rawSlug.ifBlank { extractSlugFromAnimeUrl(normalizeUrl(rawUrl)).orEmpty() }
                    if (slug.isBlank() || slug in NON_ANIME_SLUGS) continue
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?: continue
                    val rawImg = obj["image"]?.jsonPrimitive?.contentOrNull
                        ?: obj["cover"]?.jsonPrimitive?.contentOrNull
                        ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val cover = when {
                        rawImg.isBlank() -> ""
                        rawImg.startsWith("http") || rawImg.startsWith("/") -> normalizeUrl(rawImg)
                        else -> "https://cdn.jkdesa.com/assets/images/animes/image/$rawImg"
                    }
                    val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (title.isNotBlank() && out.none { it.slug == slug }) {
                        out.add(
                            AnimeSummary(
                                slug = slug,
                                title = title,
                                url = "$BASE_URL/$slug/",
                                coverUrl = cover,
                                type = type,
                                status = status
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return EmbeddedDirectoryMeta(hasNextPage = hasNextPage)
    }

    fun parseAnimeDetail(slug: String, html: String): AnimeDetail {
        val doc = Jsoup.parse(html, BASE_URL)

        val title = doc.selectFirst("div.anime__details__title h3, h1.anime__title, h1")
            ?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

        val altTitle = doc.selectFirst("div.anime__details__title span")
            ?.text()?.trim().orEmpty()

        val coverUrl = doc.selectFirst("div.anime__details__pic, div.anime_pic")
            ?.let { extractImageUrl(it) }
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let(::normalizeUrl).orEmpty()

        val synopsis = doc.select("div.anime__details__text p, div.sinopsis p")
            .joinToString("\n\n") { it.text().trim() }
            .trim()
            .ifBlank {
                doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                    ?: "Sin sinopsis disponible."
            }

        var status = ""
        var type = ""
        var airedInfo = ""
        var totalEpisodesText = ""
        val genres = mutableListOf<String>()

        doc.select("div.anime__details__widget ul li, ul.anime_info li").forEach { li ->
            val raw = li.text().trim()
            val lower = raw.lowercase()
            val value = raw.substringAfter(":", "").trim()
            when {
                lower.startsWith("tipo") -> type = value
                lower.startsWith("estado") -> status = value
                lower.startsWith("episodios") -> totalEpisodesText = value
                lower.startsWith("emitido") || lower.startsWith("fecha") -> airedInfo = value
                lower.startsWith("genero") || lower.startsWith("género") -> {
                    li.select("a").forEach { a ->
                        val g = a.text().trim()
                        if (g.isNotBlank() && g !in genres) genres.add(g)
                    }
                    if (genres.isEmpty() && value.isNotBlank()) {
                        value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            .forEach { if (it !in genres) genres.add(it) }
                    }
                }
            }
        }

        if (genres.isEmpty()) {
            doc.select("a[href*=/genero/]").forEach { a ->
                val g = a.text().trim()
                if (g.isNotBlank() && g !in genres) genres.add(g)
            }
        }

        val animeId = extractAnimeId(html, doc)

        val pageRanges = mutableListOf<EpisodePageRange>()
        var sequentialIdx = 1
        doc.select("div.anime__pagination a.numbers, div.anime__pagination a, ul.pagination a, .episodes-pagination a")
            .forEach { a ->
                val label = a.text().trim()
                if (label.isBlank() ||
                    label == "«" || label == "»" ||
                    label == "<" || label == ">" ||
                    label.equals("Anterior", true) ||
                    label.equals("Siguiente", true)
                ) {
                    return@forEach
                }
                val hrefPage = Regex("""#pag(\d+)|page=(\d+)""").find(a.attr("href"))
                    ?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }
                val dataPage = a.attr("data-page").toIntOrNull()
                val rangeStartPage = Regex("""^(\d+)\s*[-–]\s*\d+$""").find(label)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?.let { ((it - 1).coerceAtLeast(0) / 12) + 1 }
                val pageNum = hrefPage ?: dataPage ?: rangeStartPage ?: label.toIntOrNull() ?: sequentialIdx++
                if (pageRanges.none { it.page == pageNum }) {
                    pageRanges.add(EpisodePageRange(page = pageNum, label = label))
                }
            }

        val directEpisodes = parseDirectEpisodeLinks(slug, doc)

        return AnimeDetail(
            slug = slug,
            animeId = animeId,
            title = title,
            alternativeTitle = altTitle,
            coverUrl = coverUrl,
            synopsis = synopsis,
            status = status,
            type = type,
            airedInfo = airedInfo,
            totalEpisodesText = totalEpisodesText,
            genres = genres,
            episodes = directEpisodes,
            pageRanges = pageRanges
        )
    }

    data class EpisodePageResult(
        val episodes: List<EpisodeItem>,
        val currentPage: Int = 1,
        val lastPage: Int? = null,
        val total: Int? = null
    )

    fun parseEpisodesJsonOrHtml(slug: String, responseBody: String): List<EpisodeItem> =
        parseEpisodesPageResult(slug, responseBody).episodes

    fun parseEpisodesPageResult(slug: String, responseBody: String): EpisodePageResult {
        val trimmed = responseBody.trim()
        val list = mutableListOf<EpisodeItem>()

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val root = Json.parseToJsonElement(trimmed)
                var currentPage = 1
                var lastPage: Int? = null
                var total: Int? = null

                val arr = when {
                    trimmed.startsWith("[") -> root.jsonArray
                    else -> {
                        val obj = root.jsonObject
                        currentPage = obj["current_page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                        lastPage = obj["last_page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        total = obj["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val dataEl = obj["data"] ?: obj["episodes"] ?: obj["capitulos"]
                        when {
                            dataEl == null -> null
                            dataEl.toString().trimStart().startsWith("[") -> dataEl.jsonArray
                            else -> dataEl.jsonObject["data"]?.jsonArray
                        }
                    }
                }

                if (arr != null) {
                    for (element in arr) {
                        val obj = element.jsonObject
                        val numStr = obj["number"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: obj["episodio"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: obj["ep"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: obj["num"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: obj["id_episodio"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val num = numStr.filter { it.isDigit() }.toIntOrNull() ?: continue
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: "Episodio $num"
                        val rawImg = obj["image"]?.jsonPrimitive?.contentOrNull
                            ?: obj["thumb"]?.jsonPrimitive?.contentOrNull
                            ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull
                            ?: obj["img"]?.jsonPrimitive?.contentOrNull
                            ?: ""
                        val img = normalizeUrl(rawImg)
                        if (list.none { it.number == num }) {
                            list.add(
                                EpisodeItem(
                                    number = num,
                                    label = title,
                                    url = "$BASE_URL/$slug/$num/",
                                    thumbnailUrl = img
                                )
                            )
                        }
                    }
                    return EpisodePageResult(
                        episodes = list.sortedBy { it.number },
                        currentPage = currentPage,
                        lastPage = lastPage,
                        total = total
                    )
                }
            } catch (_: Throwable) {
                // Fall through to HTML parsing
            }
        }

        val doc = Jsoup.parse(trimmed, BASE_URL)
        return EpisodePageResult(episodes = parseDirectEpisodeLinks(slug, doc))
    }

    fun parseEpisodeServers(html: String, episodeUrl: String): List<VideoServer> {
        val doc = Jsoup.parse(html, episodeUrl)
        val servers = mutableListOf<VideoServer>()

        fun addServer(name: String, rawUrl: String) {
            val cleanedRaw = rawUrl.replace("\\/", "/").trim()
            if (cleanedRaw.isBlank()) return
            val fullRaw = normalizeUrl(cleanedRaw)
            val decoded = JsUnpacker.unwrapJkRedirectUrl(fullRaw)
            val isJkInternal = decoded.contains("jkanime.net/", ignoreCase = true) ||
                fullRaw.contains("um.php", ignoreCase = true) ||
                fullRaw.contains("um2.php", ignoreCase = true) ||
                fullRaw.contains("jk.php", ignoreCase = true) ||
                fullRaw.contains("jkokru.php", ignoreCase = true) ||
                fullRaw.contains("jkfembed.php", ignoreCase = true)

            val cleanName = name.trim().ifBlank { inferServerName(decoded) }
            if (servers.none { it.decodedEmbedUrl == decoded }) {
                servers.add(
                    VideoServer(
                        name = cleanName,
                        rawUrl = fullRaw,
                        decodedEmbedUrl = decoded,
                        isInternalJkPlayer = isJkInternal
                    )
                )
            }
        }

        // 1. Parse `var servers = [...]` JSON array embedded in JKAnime script
        Regex("""var\s+servers\s*=\s*(\[.*?\])\s*;""", RegexOption.DOT_MATCHES_ALL).find(html)?.let { match ->
            try {
                val arr = Json.parseToJsonElement(match.groupValues[1]).jsonArray
                for (element in arr) {
                    val obj = element.jsonObject
                    val serverName = obj["server"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val remote = obj["remote"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val urlField = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val candidate = when {
                        remote.startsWith("http") -> remote
                        remote.isNotBlank() -> JsUnpacker.decodeBase64(remote) ?: remote
                        urlField.isNotBlank() -> urlField
                        else -> ""
                    }
                    if (candidate.isNotBlank()) {
                        addServer(serverName, candidate)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // 2. Parse `var video = []` array entries: video[0] = '<iframe ... src="..."></iframe>';
        val serverButtonLabels = doc.select(
            "div.bg-servers a, #servers a, a.btn-server, .server-item, [data-video]"
        ).mapNotNull { el ->
            val dataVideo = el.attr("data-video").trim()
            val label = el.text().trim()
            if (dataVideo.isNotBlank()) {
                addServer(label, dataVideo)
            }
            label.takeIf { it.isNotBlank() }
        }

        Regex("""video\[(\d+)\]\s*=\s*['"](.*?)['"]\s*;""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .forEach { match ->
                val idx = match.groupValues[1].toIntOrNull() ?: 0
                val fragment = match.groupValues[2].replace("\\/", "/")
                val iframeSrc = Jsoup.parse(fragment).selectFirst("iframe")?.attr("src")
                    ?: Regex("""src=["']([^"']+)["']""").find(fragment)?.groupValues?.getOrNull(1)
                    ?: ""
                if (iframeSrc.isNotBlank()) {
                    val label = serverButtonLabels.getOrNull(idx) ?: "Opción ${idx + 1}"
                    addServer(label, iframeSrc)
                }
            }

        // 3. Parse any visible iframes in player container
        doc.select("div.anime__video__player iframe, div.video-player iframe, iframe[src]")
            .forEachIndexed { idx, iframe ->
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

    private fun parseDirectEpisodeLinks(slug: String, doc: Document): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        val escapedSlug = Regex.escape(slug)
        val epRegex = Regex("""/$escapedSlug/(\d+)/?$""")
        doc.select("a[href]").forEach { a ->
            val rawHref = normalizeUrl(a.attr("href"))
            val cleanHref = rawHref.substringBefore("?").substringBefore("#")
            val match = epRegex.find(cleanHref) ?: return@forEach
            val num = match.groupValues[1].toIntOrNull() ?: return@forEach
            val label = a.selectFirst("h5, h6, span, p")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: a.text().trim().takeIf { it.isNotBlank() }
                ?: "Episodio $num"
            val thumb = extractImageUrl(a).ifBlank {
                a.parent()?.let { extractImageUrl(it) }.orEmpty()
            }
            if (list.none { it.number == num }) {
                list.add(
                    EpisodeItem(
                        number = num,
                        label = label,
                        url = "$BASE_URL/$slug/$num/",
                        thumbnailUrl = thumb
                    )
                )
            }
        }
        return list.sortedBy { it.number }
    }

    private fun extractAnimeId(html: String, doc: Document): String {
        val patterns = listOf(
            Regex("""ajax/pagination_episodes/(\d+)"""),
            Regex("""ajax/last_episode/(\d+)"""),
            Regex("""(?:var|let|const)\s+(?:anime_id|id_anime|animeId)\s*=\s*["']?(\d+)["']?"""),
            Regex("""data-anime(?:-id)?=["'](\d+)["']"""),
            Regex("""["']?(?:anime_id|id_anime)["']?\s*[:=]\s*["']?(\d+)["']?""")
        )
        for (regex in patterns) {
            val found = regex.find(html)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) return found
        }
        doc.selectFirst("[data-anime], [data-anime-id], [data-id]")?.let { el ->
            val attr = el.attr("data-anime-id")
                .ifBlank { el.attr("data-anime") }
                .ifBlank { el.attr("data-id") }
            if (attr.all { it.isDigit() } && attr.isNotBlank()) return attr
        }
        return ""
    }

    private fun parseAnimeSummaryElement(el: Element): AnimeSummary? {
        val anchors = if (el.tagName() == "a") listOf(el) else el.select("a[href]")
        val matchedPair = anchors.firstNotNullOfOrNull { a ->
            val href = normalizeUrl(a.attr("href"))
            val slug = extractSlugFromAnimeUrl(href)
            if (slug != null) a to slug else null
        } ?: return null

        val (anchor, slug) = matchedPair

        val enclosingCard = if (el.tagName() == "a") findSingleAnimeParent(el, slug) else null
        val scope = enclosingCard ?: el

        val title = scope.selectFirst("h5 a, h5, h4, h3, .anime__item__text h5, .title, .card-title")
            ?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: anchor.attr("title").trim().takeIf { it.isNotBlank() }
            ?: scope.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.length > 1 }
            ?: anchors.mapNotNull { it.text().trim().takeIf { t -> t.length > 1 && !t.equals("Ver", true) } }
                .firstOrNull()
            ?: return null

        val coverUrl = extractImageUrl(el).ifBlank {
            enclosingCard?.let { extractImageUrl(it) }.orEmpty()
        }
        val badges = scope.select("ul li, .ep, .comment, .view, .badge, .type")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        val statusOrType = badges.firstOrNull().orEmpty()
        val epBadge = scope.selectFirst(".ep")?.text()?.trim().orEmpty()
        val snippet = scope.selectFirst("p")?.text()?.trim().orEmpty()

        return AnimeSummary(
            slug = slug,
            title = title,
            url = "$BASE_URL/$slug/",
            coverUrl = coverUrl,
            type = statusOrType,
            episodeBadge = epBadge,
            synopsisSnippet = snippet
        )
    }

    private fun findSingleAnimeParent(anchor: Element, targetSlug: String): Element? {
        var cur: Element? = anchor.parent()
        repeat(3) {
            val candidate = cur ?: return null
            val slugsInside = candidate.select("a[href]")
                .mapNotNull { extractSlugFromAnimeUrl(normalizeUrl(it.attr("href"))) }
                .distinct()
            if (slugsInside.size == 1 && slugsInside.first() == targetSlug && extractImageUrl(candidate).isNotBlank()) {
                return candidate
            }
            cur = candidate.parent()
        }
        return null
    }

    private fun extractSlugFromAnimeUrl(url: String): String? {
        val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
        val match = Regex("""^https?://[^/]+/([^/]+)$""").find(clean)
            ?: return null
        val host = clean.substringAfter("://").substringBefore("/").lowercase()
        if (!host.contains("jkanime")) return null
        val slug = match.groupValues[1]
        val lower = slug.lowercase()
        if (slug.isBlank() ||
            slug in NON_ANIME_SLUGS ||
            lower.endsWith(".php") ||
            lower.endsWith(".html") ||
            lower.endsWith(".js") ||
            lower.endsWith(".css") ||
            lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".svg") ||
            lower.endsWith(".ico")
        ) {
            return null
        }
        return slug
    }

    fun extractImageUrl(el: Element): String {
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

        el.selectFirst("picture source[srcset]")?.attr("srcset")
            ?.substringBefore(",")?.substringBefore(" ")?.trim()?.let {
                if (!isPlaceholder(it)) return normalizeUrl(it)
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
            "desu" in lower || "um.php" in lower || "um2.php" in lower -> "Desu"
            "streamwish" in lower || "sfastwish" in lower || "wishembed" in lower -> "StreamWish"
            "filemoon" in lower -> "Filemoon"
            "voe." in lower -> "VOE"
            "vidhide" in lower || "dhtpre" in lower -> "VidHide"
            "mixdrop" in lower || "mdbekjwqa" in lower -> "MixDrop"
            "ok.ru" in lower || "jkokru" in lower -> "Ok.ru"
            "mp4upload" in lower -> "MP4Upload"
            "mega.nz" in lower -> "MEGA"
            "jk.php" in lower -> "JKPlayer"
            else -> "Servidor"
        }
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
