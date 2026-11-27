package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.EpisodeItem
import com.example.model.EpisodePageRange
import com.example.model.WatchHistoryEntry
import com.example.model.WatchStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AnimeDetailUiState {
    data object Loading : AnimeDetailUiState
    data class Success(
        val detail: AnimeDetail,
        val displayedEpisodes: List<EpisodeItem>,
        val selectedPage: Int,
        val isLoadingEpisodes: Boolean = false,
        val isLoadingMoreEpisodes: Boolean = false,
        val sortDescending: Boolean = false
    ) : AnimeDetailUiState
    data class Error(val message: String, val blockedUrl: String? = null) : AnimeDetailUiState
}

class AnimeDetailViewModel(
    private val repository: JkAnimeRepository,
    private val slug: String,
    private val provider: AnimeProvider = AnimeProvider.JKANIME
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnimeDetailUiState>(AnimeDetailUiState.Loading)
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    val watchedEpisodes: StateFlow<Map<Int, WatchHistoryEntry>> =
        (repository.watchHistory ?: MutableStateFlow(emptyList()))
            .map { list ->
                list.filter { it.animeSlug == slug && it.providerId == provider.id }
                    .associateBy { it.episodeNumber }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val isFavorite: StateFlow<Boolean> =
        (repository.favorites ?: MutableStateFlow(emptyList()))
            .map { list ->
                list.any { it.animeSlug == slug && it.providerId == provider.id }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favoriteStatus: StateFlow<WatchStatus?> =
        (repository.favorites ?: MutableStateFlow(emptyList()))
            .map { list ->
                list.firstOrNull { it.animeSlug == slug && it.providerId == provider.id }?.watchStatus
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _episodeQuery = MutableStateFlow("")
    val episodeQuery: StateFlow<String> = _episodeQuery.asStateFlow()

    private val cachedPages = mutableMapOf<Int, List<EpisodeItem>>()
    private val allEpisodesMap = LinkedHashMap<Int, EpisodeItem>()
    private var backgroundPagesJob: Job? = null

    init {
        loadDetail()
    }

    fun updateEpisodeQuery(query: String) {
        _episodeQuery.value = query.filter { it.isDigit() }
    }

    fun toggleFavorite() {
        val current = _uiState.value as? AnimeDetailUiState.Success ?: return
        viewModelScope.launch {
            repository.toggleFavorite(current.detail)
        }
    }

    fun updateWatchStatus(status: WatchStatus) {
        val current = _uiState.value as? AnimeDetailUiState.Success ?: return
        viewModelScope.launch {
            if (!isFavorite.value) {
                repository.toggleFavorite(current.detail)
            }
            repository.updateFavoriteStatus(slug, provider.id, status)
        }
    }

    fun onGenreSelected(genre: String) {
        repository.requestExploreGenre(provider, genre)
    }

    fun loadDetail() {
        backgroundPagesJob?.cancel()
        cachedPages.clear()
        allEpisodesMap.clear()
        viewModelScope.launch {
            _uiState.value = AnimeDetailUiState.Loading
            repository.getAnimeDetail(slug, provider)
                .onSuccess { detail ->
                    detail.episodes.forEach { ep ->
                        allEpisodesMap[ep.number] = ep
                    }
                    if (detail.episodes.isNotEmpty()) {
                        detail.episodes.groupBy { ((it.number - 1).coerceAtLeast(0) / 12) + 1 }
                            .forEach { (p, list) -> cachedPages[p] = list.sortedBy { it.number } }
                    }
                    val allSorted = allEpisodesMap.values.sortedBy { it.number }
                    val showAllByDefault = detail.pageRanges.size <= 25
                    val initialSelectedPage = if (showAllByDefault) 0 else (detail.pageRanges.firstOrNull()?.page ?: 1)
                    val initialDisplayed = if (initialSelectedPage == 0) {
                        allSorted
                    } else {
                        cachedPages[initialSelectedPage] ?: allSorted
                    }

                    _uiState.value = AnimeDetailUiState.Success(
                        detail = detail,
                        displayedEpisodes = initialDisplayed,
                        selectedPage = initialSelectedPage
                    )

                    if (detail.animeId.isNotBlank()) {
                        startBackgroundEpisodePaging(detail)
                    }
                }
                .onFailure { err ->
                    _uiState.value = AnimeDetailUiState.Error(
                        message = err.localizedMessage ?: "No se pudieron obtener los detalles del anime",
                        blockedUrl = (err as? CloudflareBlockedException)?.targetUrl
                    )
                }
        }
    }

    private fun startBackgroundEpisodePaging(detail: AnimeDetail) {
        val knownMaxPage = detail.pageRanges.maxOfOrNull { it.page } ?: 1
        val needsProbe = knownMaxPage == 1 && detail.episodes.size >= 12
        if (knownMaxPage <= 1 && !needsProbe) return

        backgroundPagesJob = viewModelScope.launch {
            _uiState.update { state ->
                (state as? AnimeDetailUiState.Success)?.copy(isLoadingMoreEpisodes = true) ?: state
            }

            if (knownMaxPage > 1) {
                val autoPages = if (knownMaxPage <= 25) {
                    (2..knownMaxPage).toList()
                } else {
                    listOf(2, knownMaxPage)
                }
                for (page in autoPages) {
                    if (cachedPages[page]?.size == 12) continue
                    val res = repository.fetchEpisodesPageResult(slug, detail.animeId, page).getOrNull() ?: break
                    if (res.episodes.isEmpty()) continue
                    mergeFetchedPage(page, res.episodes, knownMaxPage = res.lastPage)
                }
            } else {
                // Unknown last_page: probe 2, 3, ... until an empty/short/duplicate page is reached
                var page = 2
                while (page <= 25) {
                    val res = repository.fetchEpisodesPageResult(slug, detail.animeId, page).getOrNull() ?: break
                    val newOnes = res.episodes.filter { !allEpisodesMap.containsKey(it.number) }
                    if (newOnes.isEmpty()) break
                    mergeFetchedPage(page, res.episodes, knownMaxPage = res.lastPage)
                    if (res.episodes.size < 12) break
                    page++
                }
            }

            _uiState.update { state ->
                (state as? AnimeDetailUiState.Success)?.copy(isLoadingMoreEpisodes = false) ?: state
            }
        }
    }

    private fun mergeFetchedPage(
        page: Int,
        episodes: List<EpisodeItem>,
        knownMaxPage: Int? = null
    ) {
        val sortedPage = episodes.sortedBy { it.number }
        cachedPages[page] = sortedPage
        episodes.forEach { ep ->
            val prev = allEpisodesMap[ep.number]
            allEpisodesMap[ep.number] = if (prev == null || ep.thumbnailUrl.isNotBlank()) ep else prev
        }

        _uiState.update { state ->
            val current = state as? AnimeDetailUiState.Success ?: return@update state
            val updatedRanges = ensureRangeExists(
                current.detail.pageRanges,
                maxOf(page, knownMaxPage ?: page),
                page,
                sortedPage
            )
            val updatedDetail = current.detail.copy(
                episodes = allEpisodesMap.values.sortedBy { it.number },
                pageRanges = updatedRanges
            )
            val rawList = if (current.selectedPage == 0) {
                allEpisodesMap.values.toList()
            } else {
                cachedPages[current.selectedPage] ?: current.displayedEpisodes
            }
            val sortedDisplayed = if (current.sortDescending) {
                rawList.sortedByDescending { it.number }
            } else {
                rawList.sortedBy { it.number }
            }
            current.copy(
                detail = updatedDetail,
                displayedEpisodes = sortedDisplayed
            )
        }
    }

    private fun ensureRangeExists(
        existing: List<EpisodePageRange>,
        totalPages: Int,
        page: Int,
        pageEpisodes: List<EpisodeItem>
    ): List<EpisodePageRange> {
        val map = existing.associateBy { it.page }.toMutableMap()
        if (pageEpisodes.isNotEmpty() && !map.containsKey(page)) {
            map[page] = EpisodePageRange(
                page = page,
                label = "${pageEpisodes.first().number} - ${pageEpisodes.last().number}"
            )
        }
        for (p in 1..totalPages) {
            if (!map.containsKey(p)) {
                map[p] = EpisodePageRange(
                    page = p,
                    label = "${(p - 1) * 12 + 1} - ${p * 12}"
                )
            }
        }
        return map.values.sortedBy { it.page }
    }

    fun selectEpisodePage(page: Int) {
        val current = _uiState.value as? AnimeDetailUiState.Success ?: return
        if (page == 0) {
            val all = allEpisodesMap.values.let { list ->
                if (current.sortDescending) list.sortedByDescending { it.number } else list.sortedBy { it.number }
            }
            _uiState.value = current.copy(displayedEpisodes = all, selectedPage = 0, isLoadingEpisodes = false)
            return
        }
        if (current.selectedPage == page && cachedPages.containsKey(page)) return

        val cached = cachedPages[page]
        if (cached != null) {
            _uiState.update {
                val sorted = if (current.sortDescending) cached.sortedByDescending { e -> e.number } else cached.sortedBy { e -> e.number }
                current.copy(displayedEpisodes = sorted, selectedPage = page, isLoadingEpisodes = false)
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(selectedPage = page, isLoadingEpisodes = true)
            repository.fetchEpisodesPageResult(slug, current.detail.animeId, page)
                .onSuccess { res ->
                    cachedPages[page] = res.episodes
                    res.episodes.forEach { ep ->
                        val prev = allEpisodesMap[ep.number]
                        allEpisodesMap[ep.number] = if (prev == null || ep.thumbnailUrl.isNotBlank()) ep else prev
                    }
                    val latest = _uiState.value as? AnimeDetailUiState.Success ?: return@onSuccess
                    val sorted = if (latest.sortDescending) res.episodes.sortedByDescending { it.number } else res.episodes.sortedBy { it.number }
                    _uiState.value = latest.copy(
                        displayedEpisodes = sorted,
                        selectedPage = page,
                        isLoadingEpisodes = false
                    )
                }
                .onFailure {
                    val latest = _uiState.value as? AnimeDetailUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(isLoadingEpisodes = false)
                }
        }
    }

    fun toggleSortOrder() {
        val current = _uiState.value as? AnimeDetailUiState.Success ?: return
        val nextDesc = !current.sortDescending
        val sorted = if (nextDesc) {
            current.displayedEpisodes.sortedByDescending { it.number }
        } else {
            current.displayedEpisodes.sortedBy { it.number }
        }
        _uiState.value = current.copy(displayedEpisodes = sorted, sortDescending = nextDesc)
    }
}
