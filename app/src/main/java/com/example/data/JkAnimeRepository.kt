package com.example.data

import android.content.Context
import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.DirectoryFilter
import com.example.model.DirectoryPage
import com.example.model.EpisodeItem
import com.example.model.EpisodePageRange
import com.example.model.HomeFeed
import com.example.model.ResolvedVideo
import com.example.model.VideoServer
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class JkAnimeRepository(context: Context? = null) {

    val httpClient = JkAnimeHttpClient()
    val cloudflareBridge = CloudflareWebBridge(httpClient).also { bridge ->
        httpClient.webFetchHandler = { url, referer ->
            bridge.fetchAutomatically(url, referer)
        }
        httpClient.webRenderedHandler = { url, referer, hasRenderedContent ->
            bridge.fetchRenderedPageHtml(url, referer, hasRenderedContent)
        }
    }
    private val streamResolver = VideoStreamResolver(httpClient)
    private val historyStore = context?.let { WatchHistoryStore(it.applicationContext) }

    private val _selectedProvider = MutableStateFlow(AnimeProvider.JKANIME)
    val selectedProvider: StateFlow<AnimeProvider> = _selectedProvider.asStateFlow()

    private val coverCache = ConcurrentHashMap<String, String>()
    private val latanimeEpisodeUrlCache = ConcurrentHashMap<String, Map<Int, String>>()

    fun setSelectedProvider(provider: AnimeProvider) {
        _selectedProvider.value = provider
    }

    val watchHistory: Flow<List<WatchHistoryEntry>>?
        get() = historyStore?.historyFlow

    suspend fun getHomeFeed(provider: AnimeProvider = _selectedProvider.value): Result<HomeFeed> =
        runCatching {
            when (provider) {
                AnimeProvider.LATANIME -> getLatanimeHomeFeed()
                AnimeProvider.JKANIME -> getJkAnimeHomeFeed()
            }
        }

    private suspend fun getJkAnimeHomeFeed(): HomeFeed {
        val html = httpClient.fetchHtml("${httpClient.baseUrl}/")
        var feed = JkAnimeParser.parseHome(html)
        cacheKnownCovers(feed)

        val airingSlugs = feed.airing.map { it.slug }.toSet()
        val recentSlugs = feed.recentAdded.map { it.slug }.toSet()
        val overlapsHeavily = airingSlugs.isNotEmpty() && recentSlugs.isNotEmpty() &&
            airingSlugs.intersect(recentSlugs).size >= (airingSlugs.size / 2).coerceAtLeast(1)

        if (feed.airing.isEmpty() || overlapsHeavily) {
            val emisionItems = browseDirectory(DirectoryFilter(status = "emision", provider = AnimeProvider.JKANIME))
                .getOrNull()?.items.orEmpty()
            if (emisionItems.isNotEmpty()) {
                val newAiring = emisionItems.take(18)
                val newAiringSlugs = newAiring.map { it.slug }.toSet()
                val distinctRecent = feed.recentAdded.filter { it.slug !in newAiringSlugs }
                feed = feed.copy(
                    airing = newAiring,
                    recentAdded = if (distinctRecent.isNotEmpty()) distinctRecent else feed.recentAdded
                )
            }
        }

        var directoryItemsCache: List<com.example.model.AnimeSummary>? = null
        suspend fun loadDirItems(): List<com.example.model.AnimeSummary> {
            return directoryItemsCache ?: (browseDirectory(DirectoryFilter(provider = AnimeProvider.JKANIME))
                .getOrNull()?.items.orEmpty().also { dir ->
                    directoryItemsCache = dir
                    dir.forEach { if (it.coverUrl.isNotBlank()) coverCache["${AnimeProvider.JKANIME.id}:${it.slug}"] = it.coverUrl }
                })
        }

        if (feed.recentAdded.isEmpty() && feed.airing.isNotEmpty()) {
            val currentAiringSlugs = feed.airing.map { it.slug }.toSet()
            val dirItems = loadDirItems()
            val distinctRecent = dirItems.filter { it.slug !in currentAiringSlugs }.take(24)
            if (distinctRecent.isNotEmpty()) {
                feed = feed.copy(recentAdded = distinctRecent)
            }
        }

        // Enrich any missing covers in recentAdded / airing / latestEpisodes from directory & detail pages
        val hasMissingCovers = feed.recentAdded.any { it.coverUrl.isBlank() } ||
            feed.airing.any { it.coverUrl.isBlank() } ||
            feed.latestEpisodes.any { it.coverUrl.isBlank() }

        if (hasMissingCovers) {
            loadDirItems()
            feed = applyCachedCovers(feed)

            val stillMissingSlugs = (feed.recentAdded + feed.airing)
                .filter { it.coverUrl.isBlank() }
                .map { it.slug }
                .distinct()
                .take(12)

            if (stillMissingSlugs.isNotEmpty()) {
                withTimeoutOrNull(4_500L) {
                    coroutineScope {
                        stillMissingSlugs.map { slug ->
                            async {
                                val cover = runCatching { fetchJkCoverFromDetail(slug) }.getOrNull().orEmpty()
                                if (cover.isNotBlank()) {
                                    coverCache["${AnimeProvider.JKANIME.id}:$slug"] = cover
                                }
                            }
                        }.awaitAll()
                    }
                }
                feed = applyCachedCovers(feed)
            }
        }

        return feed
    }

    private suspend fun getLatanimeHomeFeed(): HomeFeed {
        val base = AnimeProvider.LATANIME.baseUrl
        val html = httpClient.fetchHtml("$base/", referer = "$base/")
        var feed = LatanimeParser.parseHome(html)
        cacheKnownCovers(feed)

        if (feed.airing.isEmpty()) {
            val airingPage = browseDirectory(DirectoryFilter(status = "emision", provider = AnimeProvider.LATANIME))
                .getOrNull()?.items.orEmpty()
            if (airingPage.isNotEmpty()) {
                val airingSlugs = airingPage.map { it.slug }.toSet()
                val distinctRecent = feed.recentAdded.filter { it.slug !in airingSlugs }
                feed = feed.copy(
                    airing = airingPage.take(18),
                    recentAdded = if (distinctRecent.isNotEmpty()) distinctRecent else feed.recentAdded
                )
            }
        }

        if (feed.recentAdded.isEmpty()) {
            val dirPage = browseDirectory(DirectoryFilter(provider = AnimeProvider.LATANIME))
                .getOrNull()?.items.orEmpty()
            val airingSlugs = feed.airing.map { it.slug }.toSet()
            val distinctRecent = dirPage.filter { it.slug !in airingSlugs }.take(24)
            if (distinctRecent.isNotEmpty()) {
                feed = feed.copy(recentAdded = distinctRecent)
            }
        }

        cacheKnownCovers(feed)
        return applyCachedCovers(feed)
    }

    private suspend fun fetchJkCoverFromDetail(slug: String): String {
        val detailUrl = "${httpClient.baseUrl}/$slug/"
        val html = httpClient.fetchHtml(detailUrl, referer = "${httpClient.baseUrl}/")
        val doc = Jsoup.parse(html, httpClient.baseUrl)
        return doc.selectFirst("div.anime__details__pic, div.anime_pic")
            ?.let { JkAnimeParser.extractImageUrl(it) }
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.let(JkAnimeParser::normalizeUrl)
                .orEmpty()
    }

    private fun cacheKnownCovers(feed: HomeFeed) {
        val pid = feed.provider.id
        (feed.featured + feed.airing + feed.recentAdded).forEach { item ->
            if (item.coverUrl.isNotBlank()) coverCache["$pid:${item.slug}"] = item.coverUrl
        }
        feed.latestEpisodes.forEach { ep ->
            if (ep.coverUrl.isNotBlank()) coverCache["$pid:${ep.animeSlug}"] = ep.coverUrl
        }
    }

    private fun applyCachedCovers(feed: HomeFeed): HomeFeed {
        val pid = feed.provider.id
        return feed.copy(
            featured = feed.featured.map {
                if (it.coverUrl.isBlank()) it.copy(coverUrl = coverCache["$pid:${it.slug}"].orEmpty()) else it
            },
            latestEpisodes = feed.latestEpisodes.map {
                if (it.coverUrl.isBlank()) it.copy(coverUrl = coverCache["$pid:${it.animeSlug}"].orEmpty()) else it
            },
            airing = feed.airing.map {
                if (it.coverUrl.isBlank()) it.copy(coverUrl = coverCache["$pid:${it.slug}"].orEmpty()) else it
            },
            recentAdded = feed.recentAdded.map {
                if (it.coverUrl.isBlank()) it.copy(coverUrl = coverCache["$pid:${it.slug}"].orEmpty()) else it
            }
        )
    }

    suspend fun browseDirectory(filter: DirectoryFilter): Result<DirectoryPage> = runCatching {
        when (filter.provider) {
            AnimeProvider.LATANIME -> browseLatanimeDirectory(filter)
            AnimeProvider.JKANIME -> browseJkAnimeDirectory(filter)
        }
    }

    private suspend fun browseJkAnimeDirectory(filter: DirectoryFilter): DirectoryPage {
        val url = buildJkDirectoryUrl(filter)
        val referer = "${httpClient.baseUrl}/directorio/"
        val html = httpClient.fetchHtml(url, referer = referer)
        var parsed = JkAnimeParser.parseDirectoryOrSearch(html, filter.page)

        if (parsed.items.isEmpty()) {
            val rendered = httpClient.fetchRenderedHtml(url, referer = referer) { candidate ->
                JkAnimeParser.parseDirectoryOrSearch(candidate, filter.page).items.isNotEmpty()
            }
            if (!rendered.isNullOrBlank()) {
                parsed = JkAnimeParser.parseDirectoryOrSearch(rendered, filter.page)
            }
        }

        if (parsed.items.isEmpty() && filter.query.isNotBlank() && filter.page == 1) {
            val altSlugQuery = URLEncoder.encode(filter.query.trim(), "UTF-8").replace("+", "_")
            val altUrl = "${httpClient.baseUrl}/buscar/$altSlugQuery/"
            val altHtml = runCatching { httpClient.fetchHtml(altUrl, referer = referer) }.getOrNull()
            if (!altHtml.isNullOrBlank()) {
                val altParsed = JkAnimeParser.parseDirectoryOrSearch(altHtml, 1)
                if (altParsed.items.isNotEmpty()) {
                    parsed = altParsed
                } else {
                    val altRendered = httpClient.fetchRenderedHtml(altUrl, referer = referer) { candidate ->
                        JkAnimeParser.parseDirectoryOrSearch(candidate, 1).items.isNotEmpty()
                    }
                    if (!altRendered.isNullOrBlank()) {
                        parsed = JkAnimeParser.parseDirectoryOrSearch(altRendered, 1)
                    }
                }
            }
        }
        return parsed
    }

    private suspend fun browseLatanimeDirectory(filter: DirectoryFilter): DirectoryPage {
        val base = AnimeProvider.LATANIME.baseUrl
        val url = buildLatanimeDirectoryUrl(filter)
        val referer = "$base/animes"
        val html = httpClient.fetchHtml(url, referer = referer)
        var parsed = LatanimeParser.parseDirectoryOrSearch(html, filter.page)

        if (parsed.items.isEmpty()) {
            val rendered = httpClient.fetchRenderedHtml(url, referer = referer) { candidate ->
                LatanimeParser.parseDirectoryOrSearch(candidate, filter.page).items.isNotEmpty()
            }
            if (!rendered.isNullOrBlank()) {
                parsed = LatanimeParser.parseDirectoryOrSearch(rendered, filter.page)
            }
        }
        return parsed
    }

    suspend fun getAnimeDetail(
        slug: String,
        provider: AnimeProvider = _selectedProvider.value
    ): Result<AnimeDetail> = runCatching {
        when (provider) {
            AnimeProvider.LATANIME -> getLatanimeDetail(slug)
            AnimeProvider.JKANIME -> getJkAnimeDetail(slug)
        }
    }

    private suspend fun getLatanimeDetail(slug: String): AnimeDetail {
        val base = AnimeProvider.LATANIME.baseUrl
        val detailUrl = "$base/anime/$slug"
        val html = httpClient.fetchHtml(detailUrl, referer = "$base/")
        val parsed = LatanimeParser.parseAnimeDetail(slug, html)
        if (parsed.coverUrl.isNotBlank()) {
            coverCache["${AnimeProvider.LATANIME.id}:$slug"] = parsed.coverUrl
        }
        if (parsed.episodes.isNotEmpty()) {
            latanimeEpisodeUrlCache[slug] = parsed.episodes.associate { it.number to it.url }
        }
        return parsed
    }

    private suspend fun getJkAnimeDetail(slug: String): AnimeDetail {
        val detailUrl = "${httpClient.baseUrl}/$slug/"
        val html = httpClient.fetchHtml(detailUrl)
        var parsed = JkAnimeParser.parseAnimeDetail(slug, html)

        if (parsed.animeId.isBlank() || (parsed.episodes.size <= 2 && parsed.pageRanges.isEmpty())) {
            val rendered = httpClient.fetchRenderedHtml(detailUrl, referer = "${httpClient.baseUrl}/") { candidate ->
                val check = JkAnimeParser.parseAnimeDetail(slug, candidate)
                check.animeId.isNotBlank() || check.episodes.size > 2 || check.pageRanges.isNotEmpty()
            }
            if (!rendered.isNullOrBlank()) {
                val renderedParsed = JkAnimeParser.parseAnimeDetail(slug, rendered)
                parsed = parsed.copy(
                    animeId = parsed.animeId.ifBlank { renderedParsed.animeId },
                    episodes = if (renderedParsed.episodes.size > parsed.episodes.size) renderedParsed.episodes else parsed.episodes,
                    pageRanges = if (renderedParsed.pageRanges.size > parsed.pageRanges.size) renderedParsed.pageRanges else parsed.pageRanges,
                    coverUrl = parsed.coverUrl.ifBlank { renderedParsed.coverUrl },
                    synopsis = if (parsed.synopsis == "Sin sinopsis disponible." && renderedParsed.synopsis.isNotBlank()) renderedParsed.synopsis else parsed.synopsis
                )
            }
        }

        if (parsed.coverUrl.isNotBlank()) {
            coverCache["${AnimeProvider.JKANIME.id}:$slug"] = parsed.coverUrl
        }

        val firstPageNum = parsed.pageRanges.firstOrNull()?.page ?: 1
        val ajaxPage1 = if (parsed.animeId.isNotBlank()) {
            fetchEpisodesPageResult(slug, parsed.animeId, firstPageNum).getOrNull()
        } else {
            null
        }

        val mergedEpisodesMap = LinkedHashMap<Int, EpisodeItem>()
        parsed.episodes.forEach { mergedEpisodesMap[it.number] = it }
        ajaxPage1?.episodes?.forEach { ep ->
            val existing = mergedEpisodesMap[ep.number]
            mergedEpisodesMap[ep.number] = if (existing == null || ep.thumbnailUrl.isNotBlank()) ep else existing
        }

        val maxFromRanges = parsed.pageRanges.maxOfOrNull { it.page } ?: 1
        val maxFromAjax = ajaxPage1?.lastPage
            ?: ajaxPage1?.total?.let { if (it in 1..2000) ((it - 1) / 12) + 1 else 1 }
            ?: 1
        val totalCountFromText = parsed.totalEpisodesText.filter { it.isDigit() }.toIntOrNull()
            ?.takeIf { it in 1..2000 }
        val maxFromText = if (totalCountFromText != null && ajaxPage1 == null) {
            ((totalCountFromText - 1) / 12) + 1
        } else {
            1
        }
        val maxDirectEp = mergedEpisodesMap.keys.maxOrNull() ?: 0
        val maxFromDirectEp = if (maxDirectEp in 1..2000) ((maxDirectEp - 1) / 12) + 1 else 1
        val estimatedPages = maxOf(maxFromRanges, maxFromAjax, maxFromText, maxFromDirectEp)

        if (ajaxPage1 == null || ajaxPage1.episodes.isEmpty()) {
            val targetCount = when {
                maxDirectEp in 1..2000 -> maxDirectEp
                totalCountFromText != null && !parsed.status.contains("estreno", ignoreCase = true) -> totalCountFromText
                else -> 0
            }
            if (targetCount in 1..2000 && mergedEpisodesMap.size < targetCount) {
                for (num in 1..targetCount) {
                    if (!mergedEpisodesMap.containsKey(num)) {
                        mergedEpisodesMap[num] = EpisodeItem(
                            number = num,
                            label = "Episodio $num",
                            url = "${httpClient.baseUrl}/$slug/$num/"
                        )
                    }
                }
            }
        }

        val sortedEpisodes = mergedEpisodesMap.values.sortedBy { it.number }

        val existingRangeByPage = parsed.pageRanges.associateBy { it.page }
        val finalRanges = if (estimatedPages > 1) {
            (1..estimatedPages).map { p ->
                existingRangeByPage[p] ?: EpisodePageRange(
                    page = p,
                    label = "${(p - 1) * 12 + 1} - ${p * 12}"
                )
            }
        } else {
            parsed.pageRanges
        }

        return parsed.copy(episodes = sortedEpisodes, pageRanges = finalRanges)
    }

    suspend fun fetchEpisodesPage(
        slug: String,
        animeId: String,
        page: Int
    ): Result<List<EpisodeItem>> = fetchEpisodesPageResult(slug, animeId, page).map { it.episodes }

    suspend fun fetchEpisodesPageResult(
        slug: String,
        animeId: String,
        page: Int
    ): Result<JkAnimeParser.EpisodePageResult> = runCatching {
        if (animeId.isBlank()) return@runCatching JkAnimeParser.EpisodePageResult(emptyList())
        val ajaxUrl = "${httpClient.baseUrl}/ajax/pagination_episodes/$animeId/$page/"
        val body = httpClient.fetchJsonOrText(ajaxUrl, referer = "${httpClient.baseUrl}/$slug/")
        JkAnimeParser.parseEpisodesPageResult(slug, body)
    }

    suspend fun resolveEpisodeUrl(
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider,
        explicitUrl: String = ""
    ): String {
        if (explicitUrl.isNotBlank()) return explicitUrl
        if (provider == AnimeProvider.JKANIME) {
            return "${httpClient.baseUrl}/$slug/$episodeNumber/"
        }
        val cached = latanimeEpisodeUrlCache[slug]?.get(episodeNumber)
        if (!cached.isNullOrBlank()) return cached
        val detail = runCatching { getLatanimeDetail(slug) }.getOrNull()
        val found = detail?.episodes?.firstOrNull { it.number == episodeNumber }?.url
        return found ?: "${AnimeProvider.LATANIME.baseUrl}/ver/$slug-episodio-$episodeNumber"
    }

    suspend fun getEpisodeServers(
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider = AnimeProvider.JKANIME,
        episodeUrl: String = ""
    ): Result<List<VideoServer>> = runCatching {
        val targetUrl = resolveEpisodeUrl(slug, episodeNumber, provider, episodeUrl)
        val referer = when (provider) {
            AnimeProvider.LATANIME -> "${AnimeProvider.LATANIME.baseUrl}/anime/$slug"
            AnimeProvider.JKANIME -> "${httpClient.baseUrl}/$slug/"
        }
        val html = httpClient.fetchHtml(targetUrl, referer = referer)
        when (provider) {
            AnimeProvider.LATANIME -> LatanimeParser.parseEpisodeServers(html, targetUrl)
            AnimeProvider.JKANIME -> JkAnimeParser.parseEpisodeServers(html, targetUrl)
        }
    }

    suspend fun resolveServerVideo(
        server: VideoServer,
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider = AnimeProvider.JKANIME,
        episodeUrl: String = ""
    ): Result<ResolvedVideo> = runCatching {
        val epUrl = resolveEpisodeUrl(slug, episodeNumber, provider, episodeUrl)
        streamResolver.resolve(server, epUrl, providerBaseUrl = provider.baseUrl)
    }

    suspend fun saveWatchProgress(entry: WatchHistoryEntry) {
        historyStore?.upsertProgress(entry)
    }

    suspend fun removeWatchHistoryEntry(slug: String, episodeNumber: Int) {
        historyStore?.removeEntry(slug, episodeNumber)
    }

    suspend fun clearWatchHistory() {
        historyStore?.clearAll()
    }

    private fun buildJkDirectoryUrl(filter: DirectoryFilter): String {
        val base = httpClient.baseUrl
        val q = filter.query.trim()
        if (q.isNotBlank()) {
            val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
            return "$base/buscar/$encoded/${filter.page}/"
        }

        val params = mutableListOf<String>()
        if (filter.page > 1) params.add("p=${filter.page}")
        if (filter.status.isNotBlank()) params.add("estado=${URLEncoder.encode(filter.status, "UTF-8")}")
        if (filter.type.isNotBlank()) params.add("tipo=${URLEncoder.encode(filter.type, "UTF-8")}")
        if (filter.genre.isNotBlank()) params.add("genero=${URLEncoder.encode(filter.genre, "UTF-8")}")

        return if (params.isEmpty()) {
            "$base/directorio/"
        } else {
            "$base/directorio/?${params.joinToString("&")}"
        }
    }

    private fun buildLatanimeDirectoryUrl(filter: DirectoryFilter): String {
        val base = AnimeProvider.LATANIME.baseUrl
        val q = filter.query.trim()
        if (q.isNotBlank()) {
            val encoded = URLEncoder.encode(q, "UTF-8")
            return "$base/buscar?q=$encoded&p=${filter.page}"
        }
        if (filter.status == "emision" && filter.type.isBlank() && filter.genre.isBlank()) {
            return "$base/emision?p=${filter.page}"
        }
        val params = mutableListOf("p=${filter.page}")
        if (filter.genre.isNotBlank()) params.add("genero=${URLEncoder.encode(filter.genre, "UTF-8")}")
        if (filter.type.isNotBlank()) params.add("tipo=${URLEncoder.encode(filter.type, "UTF-8")}")
        return "$base/animes?${params.joinToString("&")}"
    }
}
