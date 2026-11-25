package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.model.FavoriteAnime
import com.example.model.HomeFeed
import com.example.model.UserProfile
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String, val blockedUrl: String? = null) : HomeUiState
}

class HomeViewModel(private val repository: JkAnimeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedProvider: StateFlow<AnimeProvider> = repository.selectedProvider

    val profiles: StateFlow<List<UserProfile>> =
        (repository.profiles ?: MutableStateFlow(listOf(UserProfile("default", "Otaku", "kitsune"))))
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                listOf(UserProfile("default", "Otaku", "kitsune"))
            )

    val activeProfile: StateFlow<UserProfile> =
        (repository.activeProfile ?: MutableStateFlow(UserProfile("default", "Otaku", "kitsune")))
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UserProfile("default", "Otaku", "kitsune")
            )

    // Independent per profile (via repository.watchHistory) AND independent per provider
    val continueWatching: StateFlow<List<WatchHistoryEntry>> =
        combine(
            repository.selectedProvider,
            repository.watchHistory ?: MutableStateFlow(emptyList())
        ) { provider, entries ->
            entries.filter { it.providerId == provider.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<FavoriteAnime>> =
        combine(
            repository.selectedProvider,
            repository.favorites ?: MutableStateFlow(emptyList())
        ) { provider, entries ->
            entries.filter { it.providerId == provider.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProvider
                .collect { provider ->
                    loadHome(isRefresh = false, provider = provider)
                }
        }
    }

    fun selectProvider(provider: AnimeProvider) {
        if (repository.selectedProvider.value == provider) return
        _uiState.value = HomeUiState.Loading
        repository.setSelectedProvider(provider)
    }

    fun createProfile(name: String, avatarKey: String) {
        viewModelScope.launch {
            repository.createProfile(name, avatarKey)
        }
    }

    fun updateProfile(profileId: String, name: String, avatarKey: String) {
        viewModelScope.launch {
            repository.updateProfile(profileId, name, avatarKey)
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            repository.switchProfile(profileId)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
        }
    }

    fun loadHome(
        isRefresh: Boolean = true,
        provider: AnimeProvider = repository.selectedProvider.value
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else if (_uiState.value !is HomeUiState.Success ||
                (_uiState.value as? HomeUiState.Success)?.feed?.provider != provider
            ) {
                _uiState.value = HomeUiState.Loading
            }

            repository.getHomeFeed(provider)
                .onSuccess { feed ->
                    if (feed.latestEpisodes.isEmpty() && feed.featured.isEmpty() && feed.recentAdded.isEmpty()) {
                        _uiState.value = HomeUiState.Error(
                            "No se encontraron elementos en ${provider.displayName}. Intenta actualizar."
                        )
                    } else {
                        _uiState.value = HomeUiState.Success(feed)
                    }
                }
                .onFailure { error ->
                    val currentSuccess = _uiState.value as? HomeUiState.Success
                    if (currentSuccess == null || currentSuccess.feed.provider != provider) {
                        val blocked = (error as? CloudflareBlockedException)?.targetUrl
                            ?: "${provider.baseUrl}/".takeIf {
                                error.message?.contains("Cloudflare", ignoreCase = true) == true
                            }
                        _uiState.value = HomeUiState.Error(
                            message = error.localizedMessage ?: "Error de conexión con ${provider.displayName}",
                            blockedUrl = blocked
                        )
                    }
                }
            _isRefreshing.value = false
        }
    }
}
