package com.example.data

import android.content.Context
import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.AnimeSchedule
import com.example.model.AnimeSummary
import com.example.model.DirectoryFilter
import com.example.model.DirectoryPage
import com.example.model.EpisodeItem
import com.example.model.EpisodePageRange
import com.example.model.FavoriteAnime
import com.example.model.HomeFeed
import com.example.model.ResolvedVideo
import com.example.model.ScheduleDay
import com.example.model.ScheduleItem
import com.example.model.UserProfile
import com.example.model.VideoServer
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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
        httpClient.webSniffHandler = { url, referer ->
            bridge.sniffDirectMediaUrl(url, referer)
        }
    }
    private val streamResolver = VideoStreamResolver(httpClient)
    private val historyStore = context?.let { WatchHistoryStore(it.applicationContext) }
    private val favoritesStore = context?.let { FavoritesStore(it.applicationContext) }
    private val profileStore = context?.let { ProfileStore(it.applicationContext) }

    private val _selectedProvider = MutableStateFlow(AnimeProvider.JKANIME)
    val selectedProvider: StateFlow<AnimeProvider> = _selectedProvider.asStateFlow()

    private val _pendingExploreGenre = MutableStateFlow<Pair<AnimeProvider, String>?>(null)
    val pendingExploreGenre: StateFlow<Pair<AnimeProvider, String>?> = _pendingExploreGenre.asStateFlow()

    private val coverCache = ConcurrentHashMap<String, String>()
    private val latanimeEpisodeUrlCache = ConcurrentHashMap<String, Map<Int, String>>()
    private val maxEpisodeCache = ConcurrentHashMap<String, Int>()
    private val detailKnownExhaustiveCache = ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var jkDirectoryFilterOptions: JkAnimeParser.DirectoryFilterOptions? = null

    fun setSelectedProvider(provider: AnimeProvider) {
        _selectedProvider.value = provider
    }

    fun requestExploreGenre(provider: AnimeProvider, rawGenre: String) {
        val slug = normalizeGenreKey(rawGenre)
        _selectedProvider.value = provider
        if (slug.isNotBlank()) {
            _pendingExploreGenre.value = provider to slug
        }
    }

    fun consumePendingExploreGenre() {
        _pendingExploreGenre.value = null
    }

    val profiles: Flow<List<UserProfile>>?
        get() = profileStore?.profilesFlow

    val activeProfile: Flow<UserProfile>?
        get() = profileStore?.activeProfileFlow

    val watchHistory: Flow<List<WatchHistoryEntry>>?
        get() = when {
            historyStore != null && profileStore != null ->
                combine(profileStore.activeProfileFlow, historyStore.historyFlow) { profile, entries ->
                    entries.filter { it.profileId == profile.id }
                }
            historyStore != null -> historyStore.historyFlow
            else -> null
        }

    val favorites: Flow<List<FavoriteAnime>>?
        get() = when {
            favoritesStore != null && profileStore != null ->
                combine(profileStore.activeProfileFlow, favoritesStore.favoritesFlow) { profile, entries ->
                    entries.filter { it.profileId == profile.id }
                }
            favoritesStore != null -> favoritesStore.favoritesFlow
            else -> null
        }

    suspend fun toggleFavorite(detail: AnimeDetail): Boolean {
        val currentProfileId = profileStore?.activeProfileFlow?.firstOrNull()?.id
            ?: ProfileStore.DEFAULT_PROFILE_ID
        val entry = FavoriteAnime(
            animeSlug = detail.slug,
            animeTitle = detail.title,
            coverUrl = detail.coverUrl,
            type = detail.type,
            status = detail.status,
            totalEpisodesText = detail.totalEpisodesText,
            providerId = detail.provider.id,
            profileId = currentProfileId
        )
        return favoritesStore?.toggleFavorite(entry) ?: false
    }

    suspend fun removeFavorite(slug: String, providerId: String) {
        val currentProfileId = profileStore?.activeProfileFlow?.firstOrNull()?.id
            ?: ProfileStore.DEFAULT_PROFILE_ID
        favoritesStore?.removeFavorite(currentProfileId, providerId, slug)
    }

    suspend fun createProfile(name: String, avatarKey: String): UserProfile? =
        profileStore?.createProfile(name, avatarKey, _selectedProvider.value.id)

    suspend fun updateProfile(profileId: String, name: String, avatarKey: String) {
        profileStore?.updateProfile(profileId, name, avatarKey)
    }

    suspend fun switchProfile(profileId: String) {
        profileStore?.switchProfile(profileId)
    }

    suspend fun deleteProfile(profileId: String): Boolean {
        val deleted = profileStore?.deleteProfile(profileId) ?: false
        if (deleted) {
            historyStore?.clearProfile(profileId)
            favoritesStore?.clearProfile(profileId)
        }
        return deleted
    }

    suspend fun getHomeFeed(provider: AnimeProvider = _selectedProvider.value): Result<HomeFeed> =
        runCatching {
            when (provider) {
                AnimeProvider.LATANIME -> getLatanimeHomeFeed()
                AnimeProvider.JKANIME -> getJkAnimeHomeFeed()
            }
        }

    private suspend fun getJkAnimeHomeFeed(): HomeFeed {
        val base = httpClient.baseUrl
        val html = httpClient.fetchHtml("$base/")
        var feed = JkAnimeParser.parseHome(html)
        cacheKnownCovers(feed)

        // If airing or recentAdded didn't render in static HTML, hydrate Home via WebView
        if (feed.airing.isEmpty() || feed.recentAdded.isEmpty()) {
            val rendered = runCatching {
                httpClient.fetchRenderedHtml("$base/", referer = "$base/") { candidate ->
                    val parsed = JkAnimeParser.parseHome(candidate)
                    parsed.airing.isNotEmpty() || parsed.recentAdded.isNotEmpty()
                }
            }.getOrNull()
            if (!rendered.isNullOrBlank()) {
                val renderedFeed = JkAnimeParser.parseHome(rendered)
                cacheKnownCovers(renderedFeed)
                feed = feed.copy(
                    featured = feed.featured.ifEmpty { renderedFeed.featured },
                    latestEpisodes = feed.latestEpisodes.ifEmpty { renderedFeed.latestEpisodes },
                    airing = feed.airing.ifEmpty { renderedFeed.airing },
                    recentAdded = feed.recentAdded.ifEmpty { renderedFeed.recentAdded }
                )
            }
        }

        val airingSlugs = feed.airing.map { it.slug }.toSet()
        val recentSlugs = feed.recentAdded.map { it.slug }.toSet()
        val overlapsHeavily = airingSlugs.isNotEmpty() && recentSlugs.isNotEmpty() &&
            airingSlugs.intersect(recentSlugs).size >= (airingSlugs.size / 2).coerceAtLeast(1)

        if (feed.airing.isEmpty() || overlapsHeavily) {
            var emisionItems = browseDirectory(DirectoryFilter(status = "emision", provider = AnimeProvider.JKANIME))
                .getOrNull()?.items.orEmpty()

            // Secondary fallback for airing: schedule (/horario/) items
            if (emisionItems.isEmpty()) {
                emisionItems = runCatching {
                    val schedHtml = httpClient.fetchHtml("$base/horario/", referer = "$base/")
                    JkAnimeParser.parseSchedule(schedHtml).days.flatMap { day ->
                        day.items.map { item ->
                            com.example.model.AnimeSummary(
                                slug = item.slug,
                                title = item.title,
                                url = item.url,
                                coverUrl = item.coverUrl,
                                type = "En emisión",
                                status = "En emisión",
                                episodeBadge = item.releaseTime,
                                provider = AnimeProvider.JKANIME
                            )
                        }
                    }.distinctBy { it.slug }
                }.getOrNull().orEmpty()
            }

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

        // Last-resort airing derivation from today's latest episodes (currently releasing episodes = airing)
        if (feed.airing.isEmpty() && feed.latestEpisodes.isNotEmpty()) {
            val fromEpisodes = feed.latestEpisodes
                .distinctBy { it.animeSlug }
                .take(16)
                .map { ep ->
                    com.example.model.AnimeSummary(
                        slug = ep.animeSlug,
                        title = ep.animeTitle,
                        url = "$base/${ep.animeSlug}/",
                        coverUrl = ep.coverUrl,
                        type = "En emisión",
                        status = "En emisión",
                        episodeBadge = ep.episodeLabel,
                        provider = AnimeProvider.JKANIME
                    )
                }
            feed = feed.copy(airing = fromEpisodes)
        }

        var directoryItemsCache: List<com.example.model.AnimeSummary>? = null
        suspend fun loadDirItems(): List<com.example.model.AnimeSummary> {
            return directoryItemsCache ?: (browseDirectory(DirectoryFilter(provider = AnimeProvider.JKANIME))
                .getOrNull()?.items.orEmpty().also { dir ->
                    directoryItemsCache = dir
                    dir.forEach { if (it.coverUrl.isNotBlank()) coverCache["${AnimeProvider.JKANIME.id}:${it.slug}"] = it.coverUrl }
                })
        }

        if (feed.recentAdded.isEmpty()) {
            val currentAiringSlugs = feed.airing.map { it.slug }.toSet()
            val newestUrls = listOf(
                "$base/estrenos/",
                "$base/directorio/?orden=fecha",
                "$base/directorio/?orden=recientes",
                "$base/directorio/?estado=estreno"
            )
            var newestItems = emptyList<com.example.model.AnimeSummary>()
            for (url in newestUrls) {
                val respHtml = runCatching { httpClient.fetchHtml(url, referer = "$base/") }.getOrNull()
                    ?: continue
                val parsed = JkAnimeParser.parseDirectoryOrSearch(respHtml, 1).items
                if (parsed.isNotEmpty()) {
                    newestItems = parsed
                    break
                }
            }
            if (newestItems.isEmpty() && feed.latestEpisodes.isNotEmpty()) {
                newestItems = feed.latestEpisodes
                    .distinctBy { it.animeSlug }
                    .map { ep ->
                        com.example.model.AnimeSummary(
                            slug = ep.animeSlug,
                            title = ep.animeTitle,
                            url = "$base/${ep.animeSlug}/",
                            coverUrl = ep.coverUrl,
                            type = ep.typeLabel.ifBlank { "Reciente" },
                            episodeBadge = ep.episodeLabel,
                            provider = AnimeProvider.JKANIME
                        )
                    }
            }
            if (newestItems.isEmpty()) {
                newestItems = loadDirItems()
            }
            newestItems.forEach {
                if (it.coverUrl.isNotBlank()) coverCache["${AnimeProvider.JKANIME.id}:${it.slug}"] = it.coverUrl
            }
            val distinctRecent = newestItems.filter { it.slug !in currentAiringSlugs }.take(24)
            val chosenRecent = distinctRecent.ifEmpty { newestItems.take(24) }
            if (chosenRecent.isNotEmpty()) {
                feed = feed.copy(recentAdded = chosenRecent)
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

    suspend fun getSchedule(provider: AnimeProvider = _selectedProvider.value): Result<AnimeSchedule> =
        runCatching {
            when (provider) {
                AnimeProvider.LATANIME -> getLatanimeSchedule()
                AnimeProvider.JKANIME -> getJkAnimeSchedule()
            }
        }

    private suspend fun getJkAnimeSchedule(): AnimeSchedule {
        val base = httpClient.baseUrl
        val url = "$base/horario/"
        val html = httpClient.fetchHtml(url, referer = "$base/")
        var schedule = JkAnimeParser.parseSchedule(html)

        if (schedule.days.all { it.items.isEmpty() }) {
            val rendered = httpClient.fetchRenderedHtml(url, referer = "$base/") { candidate ->
                JkAnimeParser.parseSchedule(candidate).days.any { it.items.isNotEmpty() }
            }
            if (!rendered.isNullOrBlank()) {
                schedule = JkAnimeParser.parseSchedule(rendered)
            }
        }

        // Fallback: if /horario/ still didn't yield sections, populate with airing directory items on today
        if (schedule.days.all { it.items.isEmpty() }) {
            val airing = browseDirectory(DirectoryFilter(status = "emision", provider = AnimeProvider.JKANIME))
                .getOrNull()?.items.orEmpty()
            if (airing.isNotEmpty()) {
                schedule = buildScheduleFallbackFromAiring(AnimeProvider.JKANIME, airing)
            }
        }

        val pid = AnimeProvider.JKANIME.id
        return schedule.copy(
            days = schedule.days.map { day ->
                day.copy(
                    items = day.items.map { item ->
                        if (item.coverUrl.isNotBlank()) {
                            coverCache["$pid:${item.slug}"] = item.coverUrl
                            item
                        } else {
                            item.copy(coverUrl = coverCache["$pid:${item.slug}"].orEmpty())
                        }
                    }
                )
            }
        )
    }

    private suspend fun getLatanimeSchedule(): AnimeSchedule {
        val base = AnimeProvider.LATANIME.baseUrl
        val candidates = listOf("$base/calendario", "$base/horario")
        var schedule: AnimeSchedule? = null

        for (candidate in candidates) {
            val html = runCatching { httpClient.fetchHtml(candidate, referer = "$base/") }.getOrNull()
                ?: continue
            var parsed = LatanimeParser.parseSchedule(html)
            if (parsed.days.all { it.items.isEmpty() }) {
                val rendered = runCatching {
                    httpClient.fetchRenderedHtml(candidate, referer = "$base/") { body ->
                        LatanimeParser.parseSchedule(body).days.any { it.items.isNotEmpty() }
                    }
                }.getOrNull()
                if (!rendered.isNullOrBlank()) {
                    parsed = LatanimeParser.parseSchedule(rendered)
                }
            }
            if (parsed.days.any { it.items.isNotEmpty() }) {
                schedule = parsed
                break
            }
        }

        if (schedule == null || schedule.days.all { it.items.isEmpty() }) {
            val emision = browseDirectory(DirectoryFilter(status = "emision", provider = AnimeProvider.LATANIME))
                .getOrNull()?.items.orEmpty()
            schedule = if (emision.isNotEmpty()) {
                buildScheduleFallbackFromAiring(AnimeProvider.LATANIME, emision)
            } else {
                LatanimeParser.parseSchedule("")
            }
        }

        val pid = AnimeProvider.LATANIME.id
        return schedule.copy(
            days = schedule.days.map { day ->
                day.copy(
                    items = day.items.map { item ->
                        if (item.coverUrl.isNotBlank()) {
                            coverCache["$pid:${item.slug}"] = item.coverUrl
                            item
                        } else {
                            item.copy(coverUrl = coverCache["$pid:${item.slug}"].orEmpty())
                        }
                    }
                )
            }
        )
    }

    private fun buildScheduleFallbackFromAiring(
        provider: AnimeProvider,
        airing: List<AnimeSummary>
    ): AnimeSchedule {
        val dayDefs = listOf(
            "lunes" to "Lunes",
            "martes" to "Martes",
            "miercoles" to "Miércoles",
            "jueves" to "Jueves",
            "viernes" to "Viernes",
            "sabado" to "Sábado",
            "domingo" to "Domingo"
        )
        val buckets = List(7) { mutableListOf<ScheduleItem>() }
        airing.forEachIndexed { idx, anime ->
            val targetDay = idx % 7
            buckets[targetDay].add(
                ScheduleItem(
                    slug = anime.slug,
                    title = anime.title,
                    url = anime.url,
                    coverUrl = anime.coverUrl,
                    episodeLabel = anime.type.ifBlank { "En emisión" },
                    provider = provider
                )
            )
        }
        return AnimeSchedule(
            provider = provider,
            days = dayDefs.mapIndexed { idx, (key, label) ->
                ScheduleDay(dayKey = key, dayLabel = label, items = buckets[idx])
            }
        )
    }

    suspend fun browseDirectory(filter: DirectoryFilter): Result<DirectoryPage> = runCatching {
        when (filter.provider) {
            AnimeProvider.LATANIME -> browseLatanimeDirectory(filter)
            AnimeProvider.JKANIME -> browseJkAnimeDirectory(
                filter.copy(status = "", type = "")
            )
        }
    }

    private suspend fun browseJkAnimeDirectory(filter: DirectoryFilter): DirectoryPage {
        val base = httpClient.baseUrl
        val referer = "$base/directorio/"
        val q = filter.query.trim()

        if (q.isNotBlank()) {
            val plusEncoded = URLEncoder.encode(q, "UTF-8")
            val spaceEncoded = plusEncoded.replace("+", "%20")
            val underscoreEncoded = URLEncoder.encode(q.replace(Regex("\\s+"), "_"), "UTF-8")
            val slugLower = q.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

            val candidateUrls = buildList {
                if (filter.page == 1) {
                    add("$base/buscar/$spaceEncoded/")
                    if (underscoreEncoded != spaceEncoded) add("$base/buscar/$underscoreEncoded/")
                    add("$base/buscar/$spaceEncoded/1/")
                    add("$base/directorio/?q=$plusEncoded")
                    add("$base/buscar/?q=$plusEncoded")
                    add("$base/ajax/buscar?q=$plusEncoded")
                    add("$base/ajax/search?q=$plusEncoded")
                    if (slugLower.isNotBlank()) add("$base/buscar/$slugLower/")
                } else {
                    add("$base/buscar/$spaceEncoded/${filter.page}/")
                    add("$base/buscar/$spaceEncoded/?p=${filter.page}")
                    add("$base/buscar/$underscoreEncoded/${filter.page}/")
                    add("$base/directorio/?q=$plusEncoded&p=${filter.page}")
                }
            }.distinct()

            var lastCloudflareError: CloudflareBlockedException? = null
            for (candidateUrl in candidateUrls) {
                val html = try {
                    if (candidateUrl.contains("/ajax/")) {
                        httpClient.fetchJsonOrText(candidateUrl, referer = "$base/")
                    } else {
                        httpClient.fetchHtml(candidateUrl, referer = "$base/")
                    }
                } catch (cf: CloudflareBlockedException) {
                    lastCloudflareError = cf
                    continue
                } catch (_: Throwable) {
                    continue
                }
                val parsed = JkAnimeParser.parseDirectoryOrSearch(html, filter.page)
                if (parsed.items.isNotEmpty()) {
                    return enrichDirectoryCovers(applyClientSideFilters(parsed, filter))
                }
            }

            // Try JS-rendered fallback for dynamic Vue/jQuery search results
            val renderCandidates = listOf(
                "$base/buscar/$spaceEncoded/",
                "$base/directorio/?q=$plusEncoded"
            )
            for (renderUrl in renderCandidates) {
                val rendered = runCatching {
                    httpClient.fetchRenderedHtml(renderUrl, referer = "$base/") { candidate ->
                        JkAnimeParser.parseDirectoryOrSearch(candidate, filter.page).items.isNotEmpty()
                    }
                }.getOrNull()
                if (!rendered.isNullOrBlank()) {
                    val renderedParsed = JkAnimeParser.parseDirectoryOrSearch(rendered, filter.page)
                    if (renderedParsed.items.isNotEmpty()) {
                        return enrichDirectoryCovers(applyClientSideFilters(renderedParsed, filter))
                    }
                }
            }

            if (lastCloudflareError != null) {
                throw lastCloudflareError
            }
            return DirectoryPage(items = emptyList(), currentPage = filter.page, hasNextPage = false)
        }

        // Directory filters (status / type / genre) with live option discovery + rendered fallback
        if ((filter.status.isNotBlank() || filter.type.isNotBlank() || filter.genre.isNotBlank()) &&
            jkDirectoryFilterOptions == null
        ) {
            runCatching { httpClient.fetchHtml("$base/directorio/", referer = "$base/") }.getOrNull()
                ?.let { dirHtml ->
                    val opts = JkAnimeParser.parseDirectoryFilterOptions(dirHtml)
                    if (opts.statusOptions.isNotEmpty() || opts.typeOptions.isNotEmpty() ||
                        opts.genreOptions.isNotEmpty() || opts.genreLinks.isNotEmpty()
                    ) {
                        jkDirectoryFilterOptions = opts
                    }
                }
        }

        val candidateUrls = buildJkDirectoryCandidateUrls(filter)
        var lastParsed: DirectoryPage? = null
        var lastCloudflareError: CloudflareBlockedException? = null
        var renderAttempts = 0

        for (candidateUrl in candidateUrls) {
            val html = try {
                httpClient.fetchHtml(candidateUrl, referer = referer)
            } catch (cf: CloudflareBlockedException) {
                lastCloudflareError = cf
                continue
            } catch (_: Throwable) {
                continue
            }

            if (jkDirectoryFilterOptions == null && candidateUrl.contains("/directorio")) {
                val opts = JkAnimeParser.parseDirectoryFilterOptions(html)
                if (opts.statusOptions.isNotEmpty() || opts.typeOptions.isNotEmpty() ||
                    opts.genreOptions.isNotEmpty() || opts.genreLinks.isNotEmpty()
                ) {
                    jkDirectoryFilterOptions = opts
                }
            }

            var parsed = JkAnimeParser.parseDirectoryOrSearch(html, filter.page)
            val htmlLower = html.lowercase()
            val explicitEmpty = htmlLower.contains("no se encontraron") ||
                htmlLower.contains("no hay animes")
            val looksDynamicShell = htmlLower.contains("id=\"app\"") ||
                htmlLower.contains("axios") ||
                htmlLower.contains("vue") ||
                html.length < 4000

            if (parsed.items.isEmpty() && !explicitEmpty && looksDynamicShell && renderAttempts < 2) {
                renderAttempts++
                val rendered = runCatching {
                    httpClient.fetchRenderedHtml(candidateUrl, referer = referer) { candidate ->
                        JkAnimeParser.parseDirectoryOrSearch(candidate, filter.page).items.isNotEmpty()
                    }
                }.getOrNull()
                if (!rendered.isNullOrBlank()) {
                    parsed = JkAnimeParser.parseDirectoryOrSearch(rendered, filter.page)
                }
            }

            if (parsed.items.isNotEmpty()) {
                val filtered = applyClientSideFilters(parsed, filter)
                if (filtered.items.isNotEmpty()) {
                    return enrichDirectoryCovers(filtered)
                }
                if (lastParsed == null) lastParsed = parsed
            }
        }

        // Fallbacks when specific combined/single JKAnime endpoints return empty
        if (lastParsed == null || lastParsed.items.isEmpty()) {
            if (filter.status == "emision" && filter.type.isBlank() && filter.genre.isBlank()) {
                val homeAiring = runCatching { getJkAnimeHomeFeed() }.getOrNull()?.airing.orEmpty()
                if (homeAiring.isNotEmpty()) {
                    return enrichDirectoryCovers(
                        DirectoryPage(items = homeAiring, currentPage = filter.page, hasNextPage = false)
                    )
                }
            }
            if ((filter.status.isNotBlank() || filter.type.isNotBlank()) && filter.genre.isNotBlank()) {
                // Retry primary genre route alone and filter client-side
                val genreOnly = buildJkDirectoryCandidateUrls(
                    filter.copy(status = "", type = "")
                ).firstOrNull()
                if (!genreOnly.isNullOrBlank()) {
                    val gHtml = runCatching { httpClient.fetchHtml(genreOnly, referer = referer) }.getOrNull()
                    if (!gHtml.isNullOrBlank()) {
                        val gParsed = JkAnimeParser.parseDirectoryOrSearch(gHtml, filter.page)
                        val gFiltered = applyClientSideFilters(gParsed, filter)
                        if (gFiltered.items.isNotEmpty()) return enrichDirectoryCovers(gFiltered)
                        if (gParsed.items.isNotEmpty()) return enrichDirectoryCovers(gParsed)
                    }
                }
            }
        }

        if (lastCloudflareError != null && (lastParsed == null || lastParsed.items.isEmpty())) {
            throw lastCloudflareError
        }

        return enrichDirectoryCovers(lastParsed ?: DirectoryPage(emptyList(), filter.page, false))
    }

    private fun enrichDirectoryCovers(page: DirectoryPage): DirectoryPage {
        val pid = AnimeProvider.JKANIME.id
        page.items.forEach {
            if (it.coverUrl.isNotBlank()) coverCache["$pid:${it.slug}"] = it.coverUrl
        }
        return page.copy(
            items = page.items.map { item ->
                if (item.coverUrl.isBlank()) {
                    item.copy(coverUrl = coverCache["$pid:${item.slug}"].orEmpty())
                } else {
                    item
                }
            }
        )
    }

    private suspend fun browseLatanimeDirectory(filter: DirectoryFilter): DirectoryPage {
        val base = AnimeProvider.LATANIME.baseUrl
        val referer = "$base/animes"
        val candidates = buildLatanimeDirectoryCandidateUrls(filter)
        var lastParsed: DirectoryPage? = null

        for (candidateUrl in candidates) {
            val html = runCatching { httpClient.fetchHtml(candidateUrl, referer = referer) }.getOrNull()
                ?: continue
            var parsed = LatanimeParser.parseDirectoryOrSearch(html, filter.page)

            if (parsed.items.isEmpty()) {
                val rendered = runCatching {
                    httpClient.fetchRenderedHtml(candidateUrl, referer = referer) { candidate ->
                        LatanimeParser.parseDirectoryOrSearch(candidate, filter.page).items.isNotEmpty()
                    }
                }.getOrNull()
                if (!rendered.isNullOrBlank()) {
                    parsed = LatanimeParser.parseDirectoryOrSearch(rendered, filter.page)
                }
            }

            if (parsed.items.isNotEmpty()) {
                val filtered = applyClientSideFilters(parsed, filter)
                if (filtered.items.isNotEmpty()) {
                    return filtered
                }
                lastParsed = parsed
            }
        }

        // If query returned empty on dedicated /buscar, try filtering home/emision/animes page 1 as last resort
        if (filter.query.isNotBlank() && filter.page == 1 && (lastParsed == null || lastParsed.items.isEmpty())) {
            val fallbackHtml = runCatching { httpClient.fetchHtml("$base/animes?p=1", referer = "$base/") }.getOrNull()
            if (!fallbackHtml.isNullOrBlank()) {
                val parsed = LatanimeParser.parseDirectoryOrSearch(fallbackHtml, 1)
                val matched = applyClientSideFilters(parsed, filter)
                if (matched.items.isNotEmpty()) return matched
            }
        }

        return lastParsed ?: DirectoryPage(emptyList(), filter.page, false)
    }

    private fun applyClientSideFilters(page: DirectoryPage, filter: DirectoryFilter): DirectoryPage {
        val qLower = filter.query.trim().lowercase()
        val typeFilter = filter.type.trim().lowercase()
        val statusFilter = filter.status.trim().lowercase()

        val filtered = page.items.filter { item ->
            val queryOk = if (qLower.isBlank()) {
                true
            } else {
                val titleLower = item.title.lowercase()
                val slugLower = item.slug.lowercase().replace('-', ' ')
                qLower.split(Regex("\\s+")).all { word ->
                    titleLower.contains(word) || slugLower.contains(word)
                }
            }

            val itemType = item.type.lowercase()
            val typeOk = when (typeFilter) {
                "" -> true
                "peliculas", "pelicula", "movie" -> {
                    itemType.isBlank() ||
                        itemType == "latino" ||
                        itemType.contains("pel") ||
                        itemType.contains("movie")
                }
                "ovas", "ova" -> {
                    itemType.isBlank() ||
                        itemType == "latino" ||
                        itemType.contains("ova") ||
                        itemType.contains("especial")
                }
                "animes", "anime", "tv" -> {
                    itemType.isBlank() ||
                        itemType == "latino" ||
                        itemType.contains("anime") ||
                        itemType.contains("serie") ||
                        itemType.contains("tv")
                }
                else -> true
            }

            val itemStatus = item.status.lowercase()
            val statusOk = when (statusFilter) {
                "" -> true
                "emision" -> itemStatus.isBlank() || itemStatus.contains("emisi") || itemStatus.contains("curso")
                "finalizado" -> itemStatus.isBlank() || itemStatus.contains("finaliz") || itemStatus.contains("concl")
                "estreno" -> itemStatus.isBlank() || itemStatus.contains("estren") || itemStatus.contains("próxim") || itemStatus.contains("proxim")
                else -> true
            }

            queryOk && typeOk && statusOk
        }

        // If strict metadata filter wiped all items while query already matched, prefer non-empty query matches
        val finalItems = if (filtered.isEmpty() && qLower.isNotBlank()) {
            page.items.filter { item ->
                val titleLower = item.title.lowercase()
                val slugLower = item.slug.lowercase().replace('-', ' ')
                qLower.split(Regex("\\s+")).all { word ->
                    titleLower.contains(word) || slugLower.contains(word)
                }
            }.ifEmpty { page.items }
        } else {
            filtered
        }
        return page.copy(items = finalItems)
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
            maxEpisodeCache["${AnimeProvider.LATANIME.id}:$slug"] = parsed.episodes.maxOf { it.number }
            detailKnownExhaustiveCache["${AnimeProvider.LATANIME.id}:$slug"] = true
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

        // Critical fallback: if static parse + AJAX pagination returned 0 episodes, force a rendered WebView pass
        if (mergedEpisodesMap.isEmpty()) {
            val lateRendered = runCatching {
                httpClient.fetchRenderedHtml(detailUrl, referer = "${httpClient.baseUrl}/") { candidate ->
                    val check = JkAnimeParser.parseAnimeDetail(slug, candidate)
                    check.episodes.isNotEmpty() ||
                        (check.animeId.isNotBlank() && check.animeId != parsed.animeId)
                }
            }.getOrNull()
            if (!lateRendered.isNullOrBlank()) {
                val lateParsed = JkAnimeParser.parseAnimeDetail(slug, lateRendered)
                if (parsed.animeId.isBlank() && lateParsed.animeId.isNotBlank()) {
                    parsed = parsed.copy(animeId = lateParsed.animeId)
                    fetchEpisodesPageResult(slug, lateParsed.animeId, firstPageNum).getOrNull()
                        ?.episodes?.forEach { ep -> mergedEpisodesMap[ep.number] = ep }
                }
                lateParsed.episodes.forEach { ep ->
                    val existing = mergedEpisodesMap[ep.number]
                    mergedEpisodesMap[ep.number] = if (existing == null || ep.thumbnailUrl.isNotBlank()) ep else existing
                }
                if (parsed.pageRanges.isEmpty() && lateParsed.pageRanges.isNotEmpty()) {
                    parsed = parsed.copy(pageRanges = lateParsed.pageRanges)
                }
                if (parsed.totalEpisodesText.isBlank() && lateParsed.totalEpisodesText.isNotBlank()) {
                    parsed = parsed.copy(totalEpisodesText = lateParsed.totalEpisodesText)
                }
            }
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

        if (mergedEpisodesMap.isEmpty()) {
            val targetCount = when {
                maxDirectEp in 1..2000 -> maxDirectEp
                totalCountFromText != null && !parsed.status.contains("estreno", ignoreCase = true) -> totalCountFromText
                else -> 0
            }
            if (targetCount in 1..2000) {
                for (num in 1..targetCount) {
                    mergedEpisodesMap[num] = EpisodeItem(
                        number = num,
                        label = "Episodio $num",
                        url = "${httpClient.baseUrl}/$slug/$num/"
                    )
                }
            } else if (!parsed.status.contains("estreno", ignoreCase = true) &&
                !parsed.status.contains("proxim", ignoreCase = true)
            ) {
                // Probe Episode 1 directly so ongoing series without numeric count still expose playable episodes
                val ep1Url = "${httpClient.baseUrl}/$slug/1/"
                val ep1Html = runCatching { httpClient.fetchHtml(ep1Url, referer = detailUrl) }.getOrNull()
                if (!ep1Html.isNullOrBlank()) {
                    val hasEp1Servers = JkAnimeParser.parseEpisodeServers(ep1Html, ep1Url).isNotEmpty()
                    val ep1HasNext = JkAnimeParser.parseHasNextEpisode(ep1Html, slug, 1)
                    if (hasEp1Servers || ep1HasNext != null) {
                        mergedEpisodesMap[1] = EpisodeItem(
                            number = 1,
                            label = "Episodio 1",
                            url = ep1Url
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

        val resultDetail = parsed.copy(episodes = sortedEpisodes, pageRanges = finalRanges)
        val jkKey = "${AnimeProvider.JKANIME.id}:$slug"
        val knownMax = sortedEpisodes.maxOfOrNull { it.number } ?: 0
        if (knownMax > 0) {
            maxEpisodeCache[jkKey] = maxOf(maxEpisodeCache[jkKey] ?: 0, knownMax)
        }
        if (finalRanges.size <= 1 && knownMax > 0) {
            detailKnownExhaustiveCache[jkKey] = true
        }
        return resultDetail
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
        val base = httpClient.baseUrl
        val referer = "$base/$slug/"
        val ajaxCandidates = listOf(
            "$base/ajax/pagination_episodes/$animeId/$page/",
            "$base/ajax/pagination_episodes/$animeId/$page",
            "$base/ajax/episodes/$animeId/$page/"
        )
        var bestResult = JkAnimeParser.EpisodePageResult(emptyList())
        var lastCf: CloudflareBlockedException? = null
        for (ajaxUrl in ajaxCandidates) {
            val body = try {
                httpClient.fetchJsonOrText(ajaxUrl, referer = referer)
            } catch (cf: CloudflareBlockedException) {
                lastCf = cf
                continue
            } catch (_: Throwable) {
                continue
            }
            val res = JkAnimeParser.parseEpisodesPageResult(slug, body)
            if (res.episodes.isNotEmpty() || res.lastPage != null || res.total != null) {
                bestResult = res
                break
            }
        }
        if (bestResult.episodes.isEmpty() && lastCf != null) {
            throw lastCf
        }
        val jkKey = "${AnimeProvider.JKANIME.id}:$slug"
        val pageMax = bestResult.episodes.maxOfOrNull { it.number } ?: 0
        if (pageMax > 0) {
            maxEpisodeCache[jkKey] = maxOf(maxEpisodeCache[jkKey] ?: 0, pageMax)
        }
        if (bestResult.lastPage != null && page >= bestResult.lastPage) {
            detailKnownExhaustiveCache[jkKey] = true
        }
        bestResult
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

    suspend fun getEpisodePlaybackInfo(
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider = AnimeProvider.JKANIME,
        episodeUrl: String = ""
    ): Result<com.example.model.EpisodePlaybackInfo> = runCatching {
        val targetUrl = resolveEpisodeUrl(slug, episodeNumber, provider, episodeUrl)
        val referer = when (provider) {
            AnimeProvider.LATANIME -> "${AnimeProvider.LATANIME.baseUrl}/anime/$slug"
            AnimeProvider.JKANIME -> "${httpClient.baseUrl}/$slug/"
        }
        val html = httpClient.fetchHtml(targetUrl, referer = referer)
        val servers = when (provider) {
            AnimeProvider.LATANIME -> LatanimeParser.parseEpisodeServers(html, targetUrl)
            AnimeProvider.JKANIME -> JkAnimeParser.parseEpisodeServers(html, targetUrl)
        }
        val pageHasNextHint = when (provider) {
            AnimeProvider.LATANIME -> LatanimeParser.parseHasNextEpisode(html, targetUrl, episodeNumber)
            AnimeProvider.JKANIME -> JkAnimeParser.parseHasNextEpisode(html, slug, episodeNumber)
        }
        val hasNext = checkHasNextEpisode(slug, episodeNumber, provider, pageHasNextHint)
        com.example.model.EpisodePlaybackInfo(
            servers = servers,
            hasNextEpisode = hasNext,
            resolvedEpisodeUrl = targetUrl
        )
    }

    private suspend fun checkHasNextEpisode(
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider,
        pageHasNextHint: Boolean?
    ): Boolean {
        val key = "${provider.id}:$slug"
        val cachedMax = maxEpisodeCache[key]
        if (cachedMax != null && episodeNumber < cachedMax) {
            return true
        }
        if (detailKnownExhaustiveCache[key] == true && cachedMax != null) {
            return episodeNumber < cachedMax
        }
        if (pageHasNextHint == false) {
            maxEpisodeCache[key] = maxOf(maxEpisodeCache[key] ?: 0, episodeNumber)
            return false
        }

        // Fetch detail (and last AJAX page on JKAnime if needed) to verify exact maximum episode
        val detail = runCatching { getAnimeDetail(slug, provider).getOrNull() }.getOrNull()
        if (detail != null) {
            val detailMax = detail.episodes.maxOfOrNull { it.number } ?: 0
            if (episodeNumber < detailMax) return true

            if (provider == AnimeProvider.JKANIME && detail.animeId.isNotBlank() && detail.pageRanges.size > 1) {
                val lastPage = detail.pageRanges.maxOf { it.page }
                val lastRes = fetchEpisodesPageResult(slug, detail.animeId, lastPage).getOrNull()
                val actualMax = lastRes?.episodes?.maxOfOrNull { it.number } ?: detailMax
                if (actualMax > 0) {
                    maxEpisodeCache[key] = actualMax
                    detailKnownExhaustiveCache[key] = true
                    return episodeNumber < actualMax
                }
            }

            if (detailMax > 0) {
                maxEpisodeCache[key] = detailMax
                return episodeNumber < detailMax
            }
        }

        return pageHasNextHint ?: false
    }

    suspend fun resolveServerVideo(
        server: VideoServer,
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider = AnimeProvider.JKANIME,
        episodeUrl: String = "",
        allowWebSniff: Boolean = false,
        forceWebSniff: Boolean = false
    ): Result<ResolvedVideo> = runCatching {
        val epUrl = resolveEpisodeUrl(slug, episodeNumber, provider, episodeUrl)
        streamResolver.resolve(
            server = server,
            episodePageUrl = epUrl,
            providerBaseUrl = provider.baseUrl,
            allowWebSniff = allowWebSniff,
            forceWebSniff = forceWebSniff
        )
    }

    suspend fun saveWatchProgress(entry: WatchHistoryEntry) {
        val currentProfileId = profileStore?.activeProfileFlow?.firstOrNull()?.id
            ?: ProfileStore.DEFAULT_PROFILE_ID
        historyStore?.upsertProgress(entry.copy(profileId = currentProfileId))
    }

    suspend fun removeWatchHistoryEntry(slug: String, episodeNumber: Int, providerId: String) {
        val currentProfileId = profileStore?.activeProfileFlow?.firstOrNull()?.id
            ?: ProfileStore.DEFAULT_PROFILE_ID
        historyStore?.removeEntry(currentProfileId, providerId, slug, episodeNumber)
    }

    suspend fun clearWatchHistory() {
        val currentProfileId = profileStore?.activeProfileFlow?.firstOrNull()?.id
            ?: ProfileStore.DEFAULT_PROFILE_ID
        historyStore?.clearProfile(currentProfileId)
    }

    private fun buildJkDirectoryCandidateUrls(filter: DirectoryFilter): List<String> {
        val base = httpClient.baseUrl
        val page = filter.page
        val opts = jkDirectoryFilterOptions

        val genreAliases = when (filter.genre.lowercase()) {
            "sci-fi" -> listOf("ciencia-ficcion", "sci-fi")
            "shounen" -> listOf("shounen", "shonen")
            "recuentos-de-la-vida" -> listOf("recuentos-de-la-vida", "slice-of-life")
            else -> listOf(filter.genre).filter { it.isNotBlank() }
        }

        val discoveredGenreValue = genreAliases.firstNotNullOfOrNull { alias ->
            val normAlias = alias.replace("-", " ")
            opts?.genreOptions?.entries?.firstOrNull { (k, _) ->
                k == normAlias || k.replace(" ", "-") == alias
            }?.value
        }

        val discoveredGenreLink = genreAliases.firstNotNullOfOrNull { alias ->
            val normAlias = alias.replace("-", " ")
            opts?.genreLinks?.entries?.firstOrNull { (k, _) ->
                k == normAlias || k.replace(" ", "-") == alias
            }?.value
        }

        val jkTypeCandidates = when (filter.type.lowercase()) {
            "animes", "tv" -> listOfNotNull(
                opts?.typeOptions?.get("animes"),
                "animes",
                "tv",
                "Anime"
            ).distinct()
            "peliculas", "pelicula", "movie" -> listOfNotNull(
                opts?.typeOptions?.get("peliculas"),
                "peliculas",
                "movie",
                "Pelicula"
            ).distinct()
            "ovas", "ova" -> listOfNotNull(
                opts?.typeOptions?.get("ovas"),
                "ovas",
                "ova",
                "OVA"
            ).distinct()
            else -> listOf(filter.type).filter { it.isNotBlank() }
        }

        val jkStatusCandidates = when (filter.status.lowercase()) {
            "emision" -> listOfNotNull(
                opts?.statusOptions?.get("emision"),
                "emision",
                "en-emision",
                "1"
            ).distinct()
            "finalizado" -> listOfNotNull(
                opts?.statusOptions?.get("finalizado"),
                "finalizado",
                "2"
            ).distinct()
            "estreno" -> listOfNotNull(
                opts?.statusOptions?.get("estreno"),
                "estreno",
                "0"
            ).distinct()
            else -> listOf(filter.status).filter { it.isNotBlank() }
        }

        val candidates = mutableListOf<String>()

        // Dedicated path routes when filtering by a single dimension (most reliable on JKAnime)
        if (filter.genre.isNotBlank() && filter.type.isBlank() && filter.status.isBlank()) {
            if (!discoveredGenreLink.isNullOrBlank()) {
                val baseLink = discoveredGenreLink.trimEnd('/')
                candidates.add(if (page > 1) "$baseLink/$page/" else "$baseLink/")
            }
            genreAliases.forEach { alias ->
                val g = URLEncoder.encode(alias, "UTF-8")
                candidates.add(if (page > 1) "$base/genero/$g/$page/" else "$base/genero/$g/")
            }
        }
        if (filter.type == "animes" && filter.genre.isBlank() && filter.status.isBlank()) {
            candidates.add(if (page > 1) "$base/directorio/?tipo=animes&p=$page" else "$base/directorio/?tipo=animes")
            candidates.add(if (page > 1) "$base/directorio/animes/$page/" else "$base/directorio/animes/")
        }
        if (filter.type == "peliculas" && filter.genre.isBlank() && filter.status.isBlank()) {
            candidates.add(if (page > 1) "$base/peliculas/$page/" else "$base/peliculas/")
            candidates.add(if (page > 1) "$base/directorio/peliculas/$page/" else "$base/directorio/peliculas/")
        }
        if (filter.type == "ovas" && filter.genre.isBlank() && filter.status.isBlank()) {
            candidates.add(if (page > 1) "$base/ovas/$page/" else "$base/ovas/")
            candidates.add(if (page > 1) "$base/directorio/ovas/$page/" else "$base/directorio/ovas/")
        }
        if (filter.status == "estreno" && filter.genre.isBlank() && filter.type.isBlank()) {
            candidates.add(if (page > 1) "$base/estrenos/$page/" else "$base/estrenos/")
        }
        if (filter.status == "emision" && filter.genre.isBlank() && filter.type.isBlank()) {
            jkStatusCandidates.forEach { st ->
                candidates.add(
                    if (page > 1) "$base/directorio/?estado=${URLEncoder.encode(st, "UTF-8")}&p=$page"
                    else "$base/directorio/?estado=${URLEncoder.encode(st, "UTF-8")}"
                )
            }
            candidates.add(if (page > 1) "$base/emision/$page/" else "$base/emision/")
            candidates.add("$base/directorio/emision/")
            candidates.add("$base/horario/")
        }
        if (filter.status == "finalizado" && filter.genre.isBlank() && filter.type.isBlank()) {
            jkStatusCandidates.forEach { st ->
                candidates.add(
                    if (page > 1) "$base/directorio/?estado=${URLEncoder.encode(st, "UTF-8")}&p=$page"
                    else "$base/directorio/?estado=${URLEncoder.encode(st, "UTF-8")}"
                )
            }
            candidates.add("$base/directorio/finalizado/")
        }

        // Combined query params (/directorio/?estado=...&tipo=...&genero=...&p=...)
        fun buildQueryUrl(statusVal: String, typeVal: String, genreVal: String, pageKey: String): String {
            val params = mutableListOf<String>()
            if (page > 1) params.add("$pageKey=$page")
            if (statusVal.isNotBlank()) params.add("estado=${URLEncoder.encode(statusVal, "UTF-8")}")
            if (typeVal.isNotBlank()) params.add("tipo=${URLEncoder.encode(typeVal, "UTF-8")}")
            if (genreVal.isNotBlank()) params.add("genero=${URLEncoder.encode(genreVal, "UTF-8")}")
            return if (params.isEmpty()) "$base/directorio/" else "$base/directorio/?${params.joinToString("&")}"
        }

        val primaryStatus = jkStatusCandidates.firstOrNull().orEmpty()
        val primaryType = jkTypeCandidates.firstOrNull().orEmpty()
        val genreQueryVals = (listOfNotNull(discoveredGenreValue) + genreAliases).distinct()
        val primaryGenre = genreQueryVals.firstOrNull().orEmpty()

        candidates.add(buildQueryUrl(primaryStatus, primaryType, primaryGenre, "p"))
        jkTypeCandidates.drop(1).forEach { altType ->
            candidates.add(buildQueryUrl(primaryStatus, altType, primaryGenre, "p"))
        }
        jkStatusCandidates.drop(1).forEach { altStatus ->
            candidates.add(buildQueryUrl(altStatus, primaryType, primaryGenre, "p"))
        }
        genreQueryVals.drop(1).forEach { altGenre ->
            candidates.add(buildQueryUrl(primaryStatus, primaryType, altGenre, "p"))
        }
        if (page > 1) {
            candidates.add(buildQueryUrl(primaryStatus, primaryType, primaryGenre, "page"))
        }
        if (filter.status.isBlank() && filter.type.isBlank() && filter.genre.isBlank() && page > 1) {
            candidates.add("$base/directorio/$page/")
        }

        return candidates.distinct()
    }

    private fun buildLatanimeDirectoryCandidateUrls(filter: DirectoryFilter): List<String> {
        val base = AnimeProvider.LATANIME.baseUrl
        val q = filter.query.trim()
        val page = filter.page

        if (q.isNotBlank()) {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val plusAlt = encoded.replace("+", "%20")
            return listOf(
                "$base/buscar?q=$encoded&p=$page",
                "$base/buscar?q=$plusAlt",
                "$base/animes?q=$encoded&p=$page"
            ).distinct()
        }

        val candidates = mutableListOf<String>()

        if (filter.status == "emision" && filter.type.isBlank() && filter.genre.isBlank()) {
            candidates.add("$base/emision?p=$page")
        }
        if (filter.type == "peliculas" && filter.status.isBlank() && filter.genre.isBlank()) {
            candidates.add("$base/peliculas?p=$page")
        }
        if (filter.genre.isNotBlank() && filter.status.isBlank() && filter.type.isBlank()) {
            val g = URLEncoder.encode(filter.genre, "UTF-8")
            candidates.add("$base/genero/$g?p=$page")
        }

        val latTypePrimary = when (filter.type.lowercase()) {
            "animes" -> "anime"
            "peliculas" -> "pelicula"
            "ovas" -> "ova"
            else -> filter.type
        }
        val latTypeAlt = filter.type

        fun buildAnimesUrl(typeVal: String, statusVal: String): String {
            val params = mutableListOf("p=$page")
            if (filter.genre.isNotBlank()) params.add("genero=${URLEncoder.encode(filter.genre, "UTF-8")}")
            if (typeVal.isNotBlank()) params.add("tipo=${URLEncoder.encode(typeVal, "UTF-8")}")
            if (statusVal.isNotBlank()) params.add("estado=${URLEncoder.encode(statusVal, "UTF-8")}")
            return "$base/animes?${params.joinToString("&")}"
        }

        candidates.add(buildAnimesUrl(latTypePrimary, filter.status))
        if (latTypeAlt != latTypePrimary) {
            candidates.add(buildAnimesUrl(latTypeAlt, filter.status))
        }
        if (filter.status.isNotBlank()) {
            candidates.add(buildAnimesUrl(latTypePrimary, ""))
        }

        return candidates.distinct()
    }

    private fun normalizeGenreKey(raw: String): String {
        val clean = raw.trim().lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("""\s+"""), "-")
        return when (clean) {
            "ciencia-ficcion", "ciencias-ficcion" -> "sci-fi"
            "shonen" -> "shounen"
            "recuentos-de-la-vida", "slice-of-life" -> "recuentos-de-la-vida"
            else -> clean
        }
    }
}
