package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.model.AnimeSummary
import com.example.model.DirectoryFilter
import com.example.model.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val filter: DirectoryFilter = DirectoryFilter(),
    val items: List<AnimeSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val errorMessage: String? = null,
    val blockedUrl: String? = null
)

class ExploreViewModel(private val repository: JkAnimeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExploreUiState(
            filter = DirectoryFilter(provider = repository.selectedProvider.value),
            isLoading = true
        )
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> =
        (repository.userPreferences ?: MutableStateFlow(UserPreferences()))
            .map { it.recentSearches }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var fetchJob: Job? = null
    private var suppressNextProviderEmission = false

    init {
        val initialPending = repository.pendingExploreFilter.value
        if (initialPending != null) {
            suppressNextProviderEmission = true
        }
        viewModelScope.launch {
            repository.selectedProvider
                .collect { provider ->
                    val pending = repository.pendingExploreFilter.value
                    if (suppressNextProviderEmission || (pending != null && pending.provider == provider)) {
                        suppressNextProviderEmission = false
                        return@collect
                    }
                    _uiState.update {
                        it.copy(
                            filter = DirectoryFilter(provider = provider, query = it.filter.query),
                            items = emptyList(),
                            hasNextPage = false,
                            errorMessage = null,
                            blockedUrl = null
                        )
                    }
                    searchOrFilter(resetPage = true)
                }
        }
        viewModelScope.launch {
            repository.pendingExploreFilter.collect { pending ->
                if (pending != null) {
                    _uiState.update {
                        it.copy(
                            filter = pending.copy(query = "", page = 1),
                            items = emptyList(),
                            hasNextPage = false,
                            errorMessage = null,
                            blockedUrl = null
                        )
                    }
                    repository.consumePendingExploreFilter()
                    searchOrFilter(resetPage = true)
                }
            }
        }
    }

    fun selectProvider(provider: AnimeProvider) {
        if (repository.selectedProvider.value == provider) return
        repository.setSelectedProvider(provider)
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(filter = it.filter.copy(query = newQuery)) }
    }

    fun submitSearch() {
        val q = _uiState.value.filter.query.trim()
        if (q.isNotBlank()) {
            viewModelScope.launch {
                repository.recordSearchQuery(q)
            }
        }
        searchOrFilter(resetPage = true)
    }

    fun applyRecentSearch(query: String) {
        _uiState.update {
            it.copy(
                filter = it.filter.copy(
                    query = query,
                    page = 1
                )
            )
        }
        submitSearch()
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            repository.removeSearchQuery(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearSearchQueries()
        }
    }

    fun selectStatus(status: String) {
        val next = if (_uiState.value.filter.status == status) "" else status
        _uiState.update {
            val isJk = it.filter.provider == AnimeProvider.JKANIME
            it.copy(
                filter = it.filter.copy(
                    status = next,
                    type = if (isJk && next.isNotBlank()) "" else it.filter.type,
                    genre = if (isJk && next.isNotBlank()) "" else it.filter.genre,
                    query = if (isJk && next.isNotBlank()) "" else it.filter.query
                )
            )
        }
        searchOrFilter(resetPage = true)
    }

    fun selectType(type: String) {
        val next = if (_uiState.value.filter.type == type) "" else type
        _uiState.update {
            val isJk = it.filter.provider == AnimeProvider.JKANIME
            it.copy(
                filter = it.filter.copy(
                    type = next,
                    status = if (isJk && next.isNotBlank()) "" else it.filter.status,
                    genre = if (isJk && next.isNotBlank()) "" else it.filter.genre,
                    query = if (isJk && next.isNotBlank()) "" else it.filter.query
                )
            )
        }
        searchOrFilter(resetPage = true)
    }

    fun selectGenre(genre: String) {
        val next = if (_uiState.value.filter.genre == genre) "" else genre
        _uiState.update {
            val isJk = it.filter.provider == AnimeProvider.JKANIME
            it.copy(
                filter = it.filter.copy(
                    genre = next,
                    query = if (isJk && next.isNotBlank()) "" else it.filter.query,
                    status = if (isJk && next.isNotBlank()) "" else it.filter.status,
                    type = if (isJk && next.isNotBlank()) "" else it.filter.type
                )
            )
        }
        searchOrFilter(resetPage = true)
    }

    fun clearAllFilters() {
        val currentProvider = _uiState.value.filter.provider
        _uiState.update { it.copy(filter = DirectoryFilter(provider = currentProvider)) }
        searchOrFilter(resetPage = true)
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasNextPage) return
        _uiState.update { it.copy(filter = it.filter.copy(page = it.filter.page + 1)) }
        searchOrFilter(resetPage = false)
    }

    fun searchOrFilter(resetPage: Boolean = true) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val targetFilter = if (resetPage) {
                _uiState.value.filter.copy(page = 1)
            } else {
                _uiState.value.filter
            }

            _uiState.update {
                if (resetPage) {
                    it.copy(filter = targetFilter, isLoading = true, errorMessage = null, blockedUrl = null)
                } else {
                    it.copy(isLoadingMore = true, errorMessage = null, blockedUrl = null)
                }
            }

            repository.browseDirectory(targetFilter)
                .onSuccess { page ->
                    _uiState.update { state ->
                        val merged = if (resetPage) {
                            page.items
                        } else {
                            (state.items + page.items).distinctBy { it.slug }
                        }
                        state.copy(
                            items = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            hasNextPage = page.hasNextPage,
                            errorMessage = null,
                            blockedUrl = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = err.localizedMessage ?: "Error al explorar el directorio",
                            blockedUrl = (err as? CloudflareBlockedException)?.targetUrl
                        )
                    }
                }
        }
    }
}
