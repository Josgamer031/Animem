package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.model.AnimeSummary
import com.example.model.DirectoryFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProvider
                .collect { provider ->
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
    }

    fun selectProvider(provider: AnimeProvider) {
        if (repository.selectedProvider.value == provider) return
        repository.setSelectedProvider(provider)
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(filter = it.filter.copy(query = newQuery)) }
    }

    fun submitSearch() {
        searchOrFilter(resetPage = true)
    }

    fun selectStatus(status: String) {
        val next = if (_uiState.value.filter.status == status) "" else status
        _uiState.update { it.copy(filter = it.filter.copy(status = next, query = "")) }
        searchOrFilter(resetPage = true)
    }

    fun selectType(type: String) {
        val next = if (_uiState.value.filter.type == type) "" else type
        _uiState.update { it.copy(filter = it.filter.copy(type = next, query = "")) }
        searchOrFilter(resetPage = true)
    }

    fun selectGenre(genre: String) {
        val next = if (_uiState.value.filter.genre == genre) "" else genre
        _uiState.update { it.copy(filter = it.filter.copy(genre = next, query = "")) }
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
