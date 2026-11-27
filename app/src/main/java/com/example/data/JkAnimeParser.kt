package com.example.data

import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.AnimeSchedule
import com.example.model.AnimeSummary
import com.example.model.DirectoryPage
import com.example.model.EpisodeItem
import com.example.model.EpisodePageRange
import com.example.model.HomeFeed
import com.example.model.LatestEpisode
import com.example.model.ScheduleDay
import com.example.model.ScheduleItem
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
        "animes", "emision", "finalizado", "finalizados", "calendario",
        "noticias", "favoritos", "mi-lista", "login", "registro", "perfil",
        "historia", "terminos", "terminos-y-condiciones", "privacidad",
        "politica-de-privacidad", "dmca", "contacto", "user", "ajax", "cdn-cgi"
    )

    private val GENERIC_ACTION_LABELS = setOf(
        "ver", "ver detalles", "ver detalle", "detalles", "detalle",
        "ver anime", "ver más", "ver mas", "ver todo", "inicio", "directorio",
        "ver capítulo", "ver capitulo", "ver episodio", "ver ahora", "leer más", "leer mas"
    )

    private fun isGenericActionLabel(raw: String): Boolean {
        val clean = raw.trim().lowercase().replace(Regex("""\s+"""), " ")
        return clean.length <= 1 || clean in GENERIC_ACTION_LABELS
    }

    private fun formatSlugTitle(slug: String): String =
        slug.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { slug }

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

        // 3. Airing vs Recent Added: classify distinctly by heading, sibling grid, sidebar, and scripts
        val airing = mutableListOf<AnimeSummary>()
        val recentAdded = mutableListOf<AnimeSummary>()

        fun normalizeHeading(raw: String): String =
            raw.lowercase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .trim()

        fun classifySectionKey(norm: String): String? = when {
            norm.length > 80 -> null
            norm.contains("emision") ||
                norm.contains("programacion") ||
                norm.contains("en curso") ||
                norm.contains("transmision") -> "airing"
            norm.contains("agregad") ||
                norm.contains("recient") ||
                norm.contains("ultimos animes") ||
                (norm.contains("ultim") && !norm.contains("episod") && !norm.contains("capitul")) ||
                norm.contains("nuevos") ||
                norm.contains("estreno") -> "recent"
            else -> null
        }

        fun targetListForKey(key: String?): MutableList<AnimeSummary>? = when (key) {
            "airing" -> airing
            "recent" -> recentAdded
            else -> null
        }

        fun addSummaryToTarget(el: Element, targetList: MutableList<AnimeSummary>) {
            parseAnimeSummaryElement(el)?.let { item ->
                val existingIdx = targetList.indexOfFirst { it.slug == item.slug }
                if (existingIdx == -1) {
                    targetList.add(item)
                } else {
                    val current = targetList[existingIdx]
                    val currentLooksFallback = isGenericActionLabel(current.title) ||
                        current.title == formatSlugTitle(current.slug)
                    val newIsRichTitle = !isGenericActionLabel(item.title) &&
                        item.title != formatSlugTitle(item.slug)
                    targetList[existingIdx] = current.copy(
                        title = if (currentLooksFallback && newIsRichTitle) item.title else current.title,
                        coverUrl = current.coverUrl.ifBlank { item.coverUrl }
                    )
                }
            }
        }

        // 3A. Heading-anchored scan (handles section wrappers AND sibling heading -> .row layouts)
        doc.select("h1, h2, h3, h4, h5, .section-title, .card-header").forEach { headingEl ->
            val norm = normalizeHeading(headingEl.text())
            val sectionKey = classifySectionKey(norm)
            val targetList = targetListForKey(sectionKey) ?: return@forEach

            // Try dedicated enclosing container that owns only this heading
            val enclosing = generateSequence(headingEl.parent()) { it.parent() }
                .take(2)
                .firstOrNull { parent ->
                    val distinctSectionTypes = parent.select("h1, h2, h3, h4, h5, .section-title, .card-header")
                        .mapNotNull { classifySectionKey(normalizeHeading(it.text())) }
                        .distinct()
                    distinctSectionTypes.size <= 1 &&
                        parent.select("div.anime__item, div.product__item, div.card, div.anime__sidebar__comment__item, li, a[href]")
                            .any { extractSlugFromAnimeUrl(normalizeUrl(if (it.tagName() == "a") it.attr("href") else it.selectFirst("a[href]")?.attr("href").orEmpty())) != null }
                }

            if (enclosing != null) {
                enclosing.select("div.anime__item, div.product__item, div.card, div.anime__sidebar__comment__item, div.product__sidebar__comment__item, div.anime__sidebar__view__item, li, a[href]")
                    .forEach { addSummaryToTarget(it, targetList) }
            }

            // Also scan sibling container right after heading (or its .section-title/.col wrapper)
            val blockRoot = if (headingEl.parent()?.hasClass("section-title") == true ||
                headingEl.parent()?.className()?.contains("col-") == true
            ) {
                headingEl.parent() ?: headingEl
            } else {
                headingEl
            }
            var sibling = blockRoot.nextElementSibling()
            var steps = 0
            while (sibling != null && steps < 3) {
                if (sibling.select("h1, h2, h3, h4, h5, .section-title, .card-header")
                        .any { classifySectionKey(normalizeHeading(it.text())) != null }
                ) {
                    break
                }
                val beforeSize = targetList.size
                sibling.select("div.anime__item, div.product__item, div.card, div.anime__sidebar__comment__item, li, a[href]")
                    .forEach { addSummaryToTarget(it, targetList) }
                if (targetList.size > beforeSize) break
                sibling = sibling.nextElementSibling()
                steps++
            }
        }

        // 3B. Explicit container-class fallback (classic & updated JKAnime themes)
        val sectionContainers = doc.select(
            "section, div.trending__product, div.recent__product, div.popular__product, " +
                "div.last_animes, div.latest__animes, div.anime__sidebar__comment, " +
                "div.product__sidebar__comment, div.anime__sidebar__view, " +
                "div.side-box, div.col-lg-4 > div, div.col-lg-8 > div"
        )
        sectionContainers.forEach { container ->
            val heading = container.selectFirst("h1, h2, h3, h4, h5, .section-title, .card-header")
                ?.text()?.let(::normalizeHeading).orEmpty()
            val targetList = targetListForKey(classifySectionKey(heading)) ?: return@forEach
            val distinctChildSections = container.select("h1, h2, h3, h4, h5, .section-title, .card-header")
                .mapNotNull { classifySectionKey(normalizeHeading(it.text())) }
                .distinct()
            if (distinctChildSections.size > 1) return@forEach
            container.select("div.anime__item, div.product__item, div.card, div.anime__sidebar__comment__item, div.product__sidebar__comment__item, li, a[href]")
                .forEach { addSummaryToTarget(it, targetList) }
        }

        // 3C. Embedded JSON arrays in home <script> tags if either list is still empty
        if (airing.isEmpty() || recentAdded.isEmpty()) {
            val emisionPattern = Regex(
                """(?:(?:var|let|const)\s+|window\.)?(?:emision|en_emision|airing|programacion)\s*=\s*(\[\s*\{.*?\}\s*\])""",
                RegexOption.DOT_MATCHES_ALL
            )
            val recentPattern = Regex(
                """(?:(?:var|let|const)\s+|window\.)?(?:recientes|agregados|recent|ultimos|ultimos_animes|animes)\s*=\s*(\[\s*\{.*?\}\s*\])""",
                RegexOption.DOT_MATCHES_ALL
            )
            if (airing.isEmpty()) {
                emisionPattern.findAll(html).forEach { m ->
                    runCatching {
                        val arr = Json.parseToJsonElement(m.groupValues[1].replace("&quot;", "\"")).jsonArray
                        parseAnimeJsonArray(arr, airing)
                    }
                }
            }
            if (recentAdded.isEmpty()) {
                recentPattern.findAll(html).forEach { m ->
                    runCatching {
                        val arr = Json.parseToJsonElement(m.groupValues[1].replace("&quot;", "\"")).jsonArray
                        parseAnimeJsonArray(arr, recentAdded)
                    }
                }
            }
        }

        // 3D. Explicit sidebar airing fallback (classic JKAnime sidebar / aside)
        if (airing.isEmpty()) {
            doc.select(
                "div.anime__sidebar__comment__item, div.product__sidebar__comment__item, " +
                    "div.anime__sidebar__view__item, div.side-box a, aside a[href], div.col-lg-4 a[href]"
            ).forEach { el -> addSummaryToTarget(el, airing) }
        }

        // 3E. Explicit recent-added container fallback ONLY (never grab unrelated popular classics)
        if (recentAdded.isEmpty()) {
            val airingSlugs = airing.map { it.slug }.toSet()
            doc.select("div.recent__product div.anime__item, div.last_animes div.anime__item, div.last_animes a[href], div.latest__animes div.anime__item")
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
        } else if (airing.isNotEmpty()) {
            // Ensure recentAdded and airing don't display identical lists
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

        // Cross-enrich missing cover images and fallback titles across all Home sections by animeSlug
        val richTitleBySlug = buildMap {
            (featured + recentAdded + airing).forEach { item ->
                if (!isGenericActionLabel(item.title) && item.title != formatSlugTitle(item.slug)) {
                    putIfAbsent(item.slug, item.title)
                }
            }
            latestEpisodes.forEach { ep ->
                if (!isGenericActionLabel(ep.animeTitle) && ep.animeTitle != formatSlugTitle(ep.animeSlug)) {
                    putIfAbsent(ep.animeSlug, ep.animeTitle)
                }
            }
        }
        val coverBySlug = buildMap {
            featured.forEach { if (it.coverUrl.isNotBlank()) put(it.slug, it.coverUrl) }
            airing.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.slug, it.coverUrl) }
            recentAdded.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.slug, it.coverUrl) }
            latestEpisodes.forEach { if (it.coverUrl.isNotBlank()) putIfAbsent(it.animeSlug, it.coverUrl) }
        }
        val enrichedFeatured = featured.map { item ->
            val resolvedTitle = if (isGenericActionLabel(item.title) || item.title == formatSlugTitle(item.slug)) {
                richTitleBySlug[item.slug] ?: item.title
            } else item.title
            val resolvedCover = item.coverUrl.ifBlank { coverBySlug[item.slug].orEmpty() }
            item.copy(title = resolvedTitle, coverUrl = resolvedCover)
        }
        val enrichedEpisodes = latestEpisodes.map { ep ->
            val resolvedCover = ep.coverUrl.ifBlank { coverBySlug[ep.animeSlug].orEmpty() }
            if (resolvedCover != ep.coverUrl) ep.copy(coverUrl = resolvedCover) else ep
        }
        val enrichedAiring = airing.map { item ->
            val resolvedTitle = if (isGenericActionLabel(item.title) || item.title == formatSlugTitle(item.slug)) {
                richTitleBySlug[item.slug] ?: item.title
            } else item.title
            val resolvedCover = item.coverUrl.ifBlank { coverBySlug[item.slug].orEmpty() }
            item.copy(title = resolvedTitle, coverUrl = resolvedCover)
        }
        val enrichedRecent = recentAdded.map { item ->
            val resolvedTitle = if (isGenericActionLabel(item.title) || item.title == formatSlugTitle(item.slug)) {
                richTitleBySlug[item.slug] ?: item.title
            } else item.title
            val resolvedCover = item.coverUrl.ifBlank { coverBySlug[item.slug].orEmpty() }
            item.copy(title = resolvedTitle, coverUrl = resolvedCover)
        }

        return HomeFeed(
            featured = enrichedFeatured.take(8),
            latestEpisodes = enrichedEpisodes.take(30),
            airing = enrichedAiring.take(18),
            recentAdded = enrichedRecent.take(24)
        )
    }

    fun parseDirectoryOrSearch(html: String, currentPage: Int): DirectoryPage {
        val trimmed = html.trim()
        val items = mutableListOf<AnimeSummary>()

        // 0. Raw JSON payload (e.g. from /ajax/buscar or /ajax/search)
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val rawMeta = parseRawDirectoryJson(trimmed, items)
            if (items.isNotEmpty()) {
                return DirectoryPage(
                    items = items,
                    currentPage = currentPage,
                    hasNextPage = rawMeta.hasNextPage
                )
            }
        }

        val doc = Jsoup.parse(html, BASE_URL)

        // 1. Parse embedded JSON in <script> tags (used by JKAnime Vue/Laravel /directorio & /buscar)
        val embeddedMeta = parseEmbeddedDirectoryJson(html, items)

        val bodyTextLower = doc.body()?.text()?.lowercase().orEmpty()
        val explicitEmpty = bodyTextLower.contains("no se encontraron") ||
            bodyTextLower.contains("sin resultados") ||
            bodyTextLower.contains("no hay animes")

        // 2. Parse rendered DOM cards across classic and modern JKAnime themes (excluding sidebars/nav/footer)
        val hasMainDirectoryGrid = doc.selectFirst("div.anime__page__content, div.results, div.dir__page") != null
        val cards = if (hasMainDirectoryGrid) {
            doc.select(
                "div.anime__page__content div.anime__item, " +
                    "div.anime__page__content div.custom_item2, " +
                    "div.anime__page__content div.dir__item, " +
                    "div.anime__page__content div.card, " +
                    "div.results div.anime__item, " +
                    "div.results div.custom_item2, " +
                    "div.dir__page div.anime__item, " +
                    "div.dir__page div.custom_item2, " +
                    "div.dir__page div.dir__item"
            )
        } else if (!explicitEmpty) {
            doc.select(
                "div.custom_item2, " +
                    "div.dir__item, " +
                    "div.anime__item, " +
                    "div.product__item, " +
                    "div.card, " +
                    "article.anime, " +
                    "div.row > div[class*=col-]:not(:has(div.anime__item, div.product__item, div.card, div.custom_item2, div.dir__item))"
            ).filterNot { el ->
                el.parents().any { p ->
                    p.tagName() in setOf("aside", "nav", "header", "footer") ||
                        p.hasClass("anime__sidebar__comment") ||
                        p.hasClass("anime__sidebar__view") ||
                        p.hasClass("side-box")
                }
            }
        } else {
            emptyList()
        }

        cards.forEach { card ->
            // Skip wide page-wrapper columns that contain several distinct anime links
            val distinctSlugsInCard = card.select("a[href]")
                .mapNotNull { extractSlugFromAnimeUrl(normalizeUrl(it.attr("href"))) }
                .distinct()
            if (distinctSlugsInCard.size > 1) return@forEach

            parseAnimeSummaryElement(card)?.let { item ->
                val idx = items.indexOfFirst { it.slug == item.slug }
                if (idx == -1) {
                    items.add(item)
                } else if (items[idx].coverUrl.isBlank() && item.coverUrl.isNotBlank()) {
                    items[idx] = items[idx].copy(coverUrl = item.coverUrl)
                }
            }
        }

        // 3. Scoped fallback scan for anime links (avoid header/footer nav links if page says empty)
        if (items.isEmpty() && !explicitEmpty) {
            val contentRoots = doc.select(
                "section.contenido, div.anime__page__content, div.dir__page, main, div.container div.row"
            ).ifEmpty { doc.select("body") }
            contentRoots.select("a[href]").forEach { a ->
                val href = normalizeUrl(a.attr("href"))
                val slug = extractSlugFromAnimeUrl(href) ?: return@forEach
                val container = findSingleAnimeParent(a, slug) ?: a.parent()
                val cover = extractImageUrl(a).ifBlank {
                    container?.let { extractImageUrl(it) }.orEmpty()
                }
                val title = a.selectFirst("h5, h4, h3, h6, .title, .card-title, span")?.text()?.trim()
                    ?.takeIf { !isGenericActionLabel(it) }
                    ?: a.attr("title").trim().takeIf { !isGenericActionLabel(it) }
                    ?: container?.selectFirst("h5, h4, h3, h6, .title, .card-title, strong")?.text()?.trim()
                        ?.takeIf { !isGenericActionLabel(it) }
                    ?: a.selectFirst("img")?.attr("alt")?.trim()?.takeIf { !isGenericActionLabel(it) }
                    ?: container?.selectFirst("img")?.attr("alt")?.trim()?.takeIf { !isGenericActionLabel(it) }
                    ?: a.text().trim().takeIf { !isGenericActionLabel(it) }
                    ?: formatSlugTitle(slug)
                if (title.isNotBlank()) {
                    val idx = items.indexOfFirst { it.slug == slug }
                    if (idx == -1) {
                        items.add(
                            AnimeSummary(
                                slug = slug,
                                title = title,
                                url = "$BASE_URL/$slug/",
                                coverUrl = cover
                            )
                        )
                    } else if (items[idx].coverUrl.isBlank() && cover.isNotBlank()) {
                        items[idx] = items[idx].copy(coverUrl = cover)
                    }
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

    data class DirectoryFilterOptions(
        val statusOptions: Map<String, String> = emptyMap(),
        val typeOptions: Map<String, String> = emptyMap(),
        val genreOptions: Map<String, String> = emptyMap(),
        val genreLinks: Map<String, String> = emptyMap(),
        val formAction: String = ""
    )

    fun parseDirectoryFilterOptions(html: String): DirectoryFilterOptions {
        val doc = Jsoup.parse(html, BASE_URL)
        val statusOpts = linkedMapOf<String, String>()
        val typeOpts = linkedMapOf<String, String>()
        val genreOpts = linkedMapOf<String, String>()
        val genreLinks = linkedMapOf<String, String>()

        fun norm(s: String): String = s.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .trim()

        doc.select("select").forEach { sel ->
            val selName = norm(sel.attr("name") + " " + sel.id())
            sel.select("option").forEach { opt ->
                val value = opt.attr("value").trim()
                val label = norm(opt.text())
                if (value.isBlank() || label == "todos" || label == "all" || value == "0") return@forEach
                when {
                    "estado" in selName || "status" in selName -> {
                        when {
                            "emision" in label || "curso" in label -> statusOpts.putIfAbsent("emision", value)
                            "finaliz" in label -> statusOpts.putIfAbsent("finalizado", value)
                            "estren" in label || "proxim" in label -> statusOpts.putIfAbsent("estreno", value)
                        }
                    }
                    "tipo" in selName || "type" in selName -> {
                        when {
                            "pelicula" in label || "movie" in label -> typeOpts.putIfAbsent("peliculas", value)
                            "ova" in label -> typeOpts.putIfAbsent("ovas", value)
                            "anime" in label || "serie" in label || label == "tv" -> typeOpts.putIfAbsent("animes", value)
                        }
                    }
                    "genero" in selName || "genre" in selName -> {
                        genreOpts.putIfAbsent(label, value)
                    }
                }
            }
        }

        doc.select("a[href*=/genero/], a[href*=genero=]").forEach { a ->
            val label = norm(a.text())
            val href = normalizeUrl(a.attr("href"))
            if (label.isNotBlank() && href.isNotBlank()) {
                genreLinks.putIfAbsent(label, href)
            }
        }

        val formAction = doc.selectFirst("form[action*=directorio], form[action*=buscar], form")
            ?.attr("action")?.trim()?.takeIf { it.isNotBlank() }?.let(::normalizeUrl).orEmpty()

        return DirectoryFilterOptions(
            statusOptions = statusOpts,
            typeOptions = typeOpts,
            genreOptions = genreOpts,
            genreLinks = genreLinks,
            formAction = formAction
        )
    }

    private data class EmbeddedDirectoryMeta(val hasNextPage: Boolean = false)

    private fun parseRawDirectoryJson(
        rawJson: String,
        out: MutableList<AnimeSummary>
    ): EmbeddedDirectoryMeta {
        return try {
            val root = Json.parseToJsonElement(rawJson)
            var hasNext = false
            val arr = when {
                rawJson.startsWith("[") -> root.jsonArray
                else -> {
                    val obj = root.jsonObject
                    val current = obj["current_page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val last = obj["last_page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    if (current != null && last != null && current < last) hasNext = true
                    if (!obj["next_page_url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) hasNext = true
                    (obj["data"] ?: obj["animes"] ?: obj["results"] ?: obj["items"])?.jsonArray
                }
            }
            arr?.let { parseAnimeJsonArray(it, out) }
            EmbeddedDirectoryMeta(hasNextPage = hasNext)
        } catch (_: Throwable) {
            EmbeddedDirectoryMeta()
        }
    }

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
            Regex("""(?:(?:var|let|const)\s+|window\.)?(?:animes|data|list|results|search|resultados|directorio|items|catalog)\s*=\s*(\[\s*\{.*?\}\s*\])\s*;?""", RegexOption.DOT_MATCHES_ALL),
            Regex("""["']?(?:data|animes|results|items)["']?\s*:\s*(\[\s*\{.*?\}\s*\])""", RegexOption.DOT_MATCHES_ALL),
            Regex(""":(?:animes|results|items)=["'](\[\s*\{.*?\}\s*\])["']""", RegexOption.DOT_MATCHES_ALL)
        )
        arrayPatterns.forEach { regex ->
            regex.findAll(html).forEach { m ->
                candidates.add(m.groupValues[1])
            }
        }

        for (raw in candidates) {
            val cleaned = raw.replace("&quot;", "\"").replace("&#039;", "'")
            try {
                val arr = Json.parseToJsonElement(cleaned).jsonArray
                parseAnimeJsonArray(arr, out)
            } catch (_: Throwable) {
            }
        }
        return EmbeddedDirectoryMeta(hasNextPage = hasNextPage)
    }

    private fun parseAnimeJsonArray(
        arr: kotlinx.serialization.json.JsonArray,
        out: MutableList<AnimeSummary>
    ) {
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
                rawImg.startsWith("http") || rawImg.startsWith("/") || rawImg.startsWith("//") -> normalizeUrl(rawImg)
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
        doc.select("div.anime__pagination a.numbers, div.anime__pagination a, ul.pagination a, .episodes-pagination a, #episode_page a")
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
                val onclickPage = Regex("""(?:pagination|paginacion|loadEpisodes)\w*\([^)]*?\b(\d+)\s*['"]?\s*\)""")
                    .find(a.attr("onclick"))?.groupValues?.getOrNull(1)?.toIntOrNull()
                val dataPage = a.attr("data-page").ifBlank { a.attr("data-pag") }.toIntOrNull()
                val rangeStartPage = Regex("""^(\d+)\s*[-–]\s*(\d+)$""").find(label)
                    ?.let { m ->
                        val aNum = m.groupValues[1].toIntOrNull()
                        val bNum = m.groupValues[2].toIntOrNull()
                        val low = if (aNum != null && bNum != null) minOf(aNum, bNum) else aNum
                        low?.let { ((it - 1).coerceAtLeast(0) / 12) + 1 }
                    }
                val pageNum = hrefPage ?: onclickPage ?: dataPage ?: rangeStartPage ?: label.toIntOrNull() ?: sequentialIdx++
                if (pageRanges.none { it.page == pageNum }) {
                    pageRanges.add(EpisodePageRange(page = pageNum, label = label))
                }
            }

        val mergedDirect = LinkedHashMap<Int, EpisodeItem>()
        parseDirectEpisodeLinks(slug, doc).forEach { mergedDirect[it.number] = it }

        // Parse embedded episode JSON arrays if present in page scripts
        Regex("""(?:(?:var|let|const)\s+|window\.)?(?:episodes|episodios|capitulos|episodesList)\s*=\s*(\[\s*\{.*?\}\s*\])""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .forEach { m ->
                parseEpisodesJsonOrHtml(slug, m.groupValues[1]).forEach { ep ->
                    val existing = mergedDirect[ep.number]
                    mergedDirect[ep.number] = if (existing == null || ep.thumbnailUrl.isNotBlank()) ep else existing
                }
            }

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
            episodes = mergedDirect.values.sortedBy { it.number },
            pageRanges = pageRanges.sortedBy { it.page }
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
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: obj["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
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

    fun parseHasNextEpisode(html: String, slug: String, episodeNumber: Int): Boolean? {
        val doc = Jsoup.parse(html, "$BASE_URL/$slug/$episodeNumber/")
        val nextEp = episodeNumber + 1
        val escapedSlug = Regex.escape(slug)
        val nextRegex = Regex("""/$escapedSlug/$nextEp/?$""")
        val hasDirectNext = doc.select("a[href]").any { a ->
            val href = normalizeUrl(a.attr("href")).substringBefore("?").substringBefore("#")
            nextRegex.containsMatchIn(href)
        }
        if (hasDirectNext) return true

        val nextButton = doc.selectFirst("a:contains(Siguiente), a:contains(Próximo), a.next")
        if (nextButton != null) {
            val href = nextButton.attr("href").trim()
            if (href.isNotBlank() && href != "#" && !nextButton.hasClass("disabled")) {
                return true
            }
        }

        val hasEpisodeNav = doc.select("a:contains(Lista), a:contains(Anterior), a[href*='/$slug/${episodeNumber - 1}']")
            .isNotEmpty()
        return if (hasEpisodeNav) false else null
    }

    private fun parseDirectEpisodeLinks(slug: String, doc: Document): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        val escapedSlug = Regex.escape(slug)
        val epRegex = Regex("""/$escapedSlug/(?:episodio-)?(\d+)(?:/|\?|$)""")

        fun addEpisode(num: Int, el: Element) {
            val label = el.selectFirst("h5, h6, span, p")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: el.text().trim().takeIf { it.isNotBlank() }
                ?: "Episodio $num"
            val thumb = extractImageUrl(el).ifBlank {
                el.parent()?.let { extractImageUrl(it) }.orEmpty()
            }
            val idx = list.indexOfFirst { it.number == num }
            if (idx == -1) {
                list.add(
                    EpisodeItem(
                        number = num,
                        label = label,
                        url = "$BASE_URL/$slug/$num/",
                        thumbnailUrl = thumb
                    )
                )
            } else if (list[idx].thumbnailUrl.isBlank() && thumb.isNotBlank()) {
                list[idx] = list[idx].copy(thumbnailUrl = thumb)
            }
        }

        doc.select("a[href]").forEach { a ->
            val rawHref = normalizeUrl(a.attr("href"))
            val cleanHref = rawHref.substringBefore("?").substringBefore("#")
            val match = epRegex.find(cleanHref) ?: return@forEach
            val num = match.groupValues[1].toIntOrNull() ?: return@forEach
            addEpisode(num, a)
        }

        doc.select(
            "div.anime__details__episodes a, #episodes-content a, #episodes a, [data-ep], [data-episode]"
        ).forEach { el ->
            val dataEp = el.attr("data-ep")
                .ifBlank { el.attr("data-episode") }
                .ifBlank {
                    Regex("""(?:ep|episodio)[.\s_-]*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(el.text())?.groupValues?.getOrNull(1).orEmpty()
                }
            val num = dataEp.filter { it.isDigit() }.toIntOrNull() ?: return@forEach
            addEpisode(num, el)
        }

        return list.sortedBy { it.number }
    }

    private fun extractAnimeId(html: String, doc: Document): String {
        val patterns = listOf(
            Regex("""ajax/pagination_episodes/(\d+)"""),
            Regex("""ajax/episodes/(\d+)"""),
            Regex("""ajax/last_episode/(\d+)"""),
            Regex("""(?:var|let|const)\s+(?:anime_id|id_anime|animeId|idAnime)\s*=\s*["']?(\d+)["']?"""),
            Regex("""data-anime(?:-id)?=["'](\d+)["']"""),
            Regex("""["']?(?:anime_id|id_anime|animeId)["']?\s*[:=]\s*["']?(\d+)["']?"""),
            Regex("""(?:guardar|favorit\w*|episodes?)\w*\(\s*["']?(\d{1,8})["']?\s*\)""")
        )
        for (regex in patterns) {
            val found = regex.find(html)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) return found
        }
        val candidates = doc.select("[data-anime-id], [data-anime], [data-id]")
        for (el in candidates) {
            val attr = el.attr("data-anime-id")
                .ifBlank { el.attr("data-anime") }
                .ifBlank { el.attr("data-id") }
                .trim()
            if (attr.isNotBlank() && attr.length <= 8 && attr.all { it.isDigit() }) {
                return attr
            }
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

        val title = scope.select("h5 a, h5, h4, h3, h6, .anime__item__text h5, .anime__item__text a, .title, .card-title, .name, strong")
            .map { it.text().trim() }
            .firstOrNull { !isGenericActionLabel(it) }
            ?: anchors.mapNotNull { it.attr("title").trim().takeIf { t -> !isGenericActionLabel(t) } }
                .firstOrNull()
            ?: scope.attr("title").trim().takeIf { !isGenericActionLabel(it) }
            ?: scope.select("img").mapNotNull { it.attr("alt").trim().takeIf { t -> !isGenericActionLabel(t) } }
                .firstOrNull()
            ?: anchors.mapNotNull { it.text().trim().takeIf { t -> !isGenericActionLabel(t) } }
                .firstOrNull()
            ?: formatSlugTitle(slug)

        val coverUrl = extractImageUrl(el).ifBlank {
            enclosingCard?.let { extractImageUrl(it) }.orEmpty()
        }
        val rawBadges = scope.select("ul li, .ep, .comment, .view, .badge, .type, .card-txt, .status")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        var parsedType = ""
        var parsedStatus = ""
        var parsedEpBadge = scope.selectFirst(".ep")?.text()?.trim().orEmpty()

        rawBadges.forEach { b ->
            val lower = b.lowercase()
            when {
                lower.contains("emisi") || lower.contains("curso") ||
                    lower.contains("finaliz") || lower.contains("concl") ||
                    lower.contains("estren") || lower.contains("proxim") || lower.contains("próxim") -> {
                    if (parsedStatus.isBlank()) parsedStatus = b
                }
                lower.contains("anime") || lower.contains("serie") || lower == "tv" ||
                    lower.contains("pel") || lower.contains("movie") ||
                    lower.contains("ova") || lower.contains("ona") || lower.contains("especial") -> {
                    if (parsedType.isBlank()) parsedType = b
                }
                lower.contains("ep") || lower.contains("cap") || lower.all { it.isDigit() } -> {
                    if (parsedEpBadge.isBlank()) parsedEpBadge = b
                }
            }
        }
        val snippet = scope.selectFirst("p")?.text()?.trim()
            ?.takeIf { !isGenericActionLabel(it) }
            .orEmpty()

        return AnimeSummary(
            slug = slug,
            title = title,
            url = "$BASE_URL/$slug/",
            coverUrl = coverUrl,
            type = parsedType,
            status = parsedStatus,
            episodeBadge = parsedEpBadge,
            synopsisSnippet = snippet
        )
    }

    private fun findSingleAnimeParent(anchor: Element, targetSlug: String): Element? {
        var cur: Element? = anchor.parent()
        var headingFallback: Element? = null
        repeat(4) {
            val candidate = cur ?: return headingFallback
            val slugsInside = candidate.select("a[href]")
                .mapNotNull { extractSlugFromAnimeUrl(normalizeUrl(it.attr("href"))) }
                .distinct()
            if (slugsInside.size == 1 && slugsInside.first() == targetSlug) {
                if (extractImageUrl(candidate).isNotBlank()) {
                    return candidate
                }
                if (headingFallback == null &&
                    candidate.select("h1, h2, h3, h4, h5, h6, .title, .card-title, .anime__item__text, strong")
                        .any { !isGenericActionLabel(it.text()) }
                ) {
                    headingFallback = candidate
                }
            }
            cur = candidate.parent()
        }
        return headingFallback
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

        fun collectCardsFromContainer(container: Element, target: MutableList<ScheduleItem>) {
            val candidates = container.select(
                "div.box, div.cajas, div.anime__item, div.card, div.item, li, a[href]"
            )
            candidates.forEach { el ->
                val anchor = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: return@forEach
                val href = normalizeUrl(anchor.attr("href"))
                val slug = extractSlugFromAnimeUrl(href) ?: return@forEach
                val scope = if (el.tagName() == "a") findSingleAnimeParent(el, slug) ?: el.parent() ?: el else el
                val title = scope.select("h5, h4, h3, h6, .title, .card-title, span.title, p, strong")
                    .map { it.text().trim() }
                    .firstOrNull { !isGenericActionLabel(it) }
                    ?: anchor.attr("title").trim().takeIf { !isGenericActionLabel(it) }
                    ?: scope.select("img").mapNotNull { it.attr("alt").trim().takeIf { t -> !isGenericActionLabel(t) } }
                        .firstOrNull()
                    ?: anchor.text().trim().takeIf { !isGenericActionLabel(it) }
                    ?: formatSlugTitle(slug)
                val cover = extractImageUrl(scope).ifBlank { extractImageUrl(anchor) }
                val timeText = scope.selectFirst(".hora, .time, .badge, span.ep, time")
                    ?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: Regex("""\b\d{1,2}:\d{2}\b""").find(scope.text())?.value.orEmpty()
                val idx = target.indexOfFirst { it.slug == slug }
                if (idx == -1) {
                    target.add(
                        ScheduleItem(
                            slug = slug,
                            title = title,
                            url = "$BASE_URL/$slug/",
                            coverUrl = cover,
                            releaseTime = timeText,
                            provider = AnimeProvider.JKANIME
                        )
                    )
                } else if (target[idx].coverUrl.isBlank() && cover.isNotBlank()) {
                    target[idx] = target[idx].copy(coverUrl = cover)
                }
            }
        }

        // 1. Containers explicitly identified by day class/id/section heading
        doc.select("div.semana, div.tab-pane, section, div.box, div.card, div.row > div").forEach { container ->
            val idOrClass = "${container.id()} ${container.className()}"
            val headingText = container.selectFirst("h1, h2, h3, h4, h5, .card-header, .semana-title, .title")
                ?.text().orEmpty()
            val dayKey = normalizeDay(idOrClass) ?: normalizeDay(headingText) ?: return@forEach
            val target = buckets[dayKey] ?: return@forEach
            // Avoid giant parent wrappers containing multiple weekday headings
            val childDayHeadings = container.select("h2, h3, h4, h5")
                .mapNotNull { normalizeDay(it.text()) }
                .distinct()
            if (childDayHeadings.size > 1) return@forEach
            collectCardsFromContainer(container, target)
        }

        // 2. Sibling heading -> next grid/list pattern (e.g. <h2>Lunes</h2><div class="row">...</div>)
        if (buckets.values.all { it.isEmpty() }) {
            doc.select("h2, h3, h4, h5").forEach { heading ->
                val dayKey = normalizeDay(heading.text()) ?: return@forEach
                val target = buckets[dayKey] ?: return@forEach
                var sibling = heading.nextElementSibling()
                while (sibling != null) {
                    val siblingHeadingDay = if (sibling.tagName() in listOf("h2", "h3", "h4", "h5")) {
                        normalizeDay(sibling.text())
                    } else null
                    if (siblingHeadingDay != null) break
                    collectCardsFromContainer(sibling, target)
                    if (sibling.select("a[href]").isNotEmpty()) break
                    sibling = sibling.nextElementSibling()
                }
            }
        }

        return AnimeSchedule(
            provider = AnimeProvider.JKANIME,
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
