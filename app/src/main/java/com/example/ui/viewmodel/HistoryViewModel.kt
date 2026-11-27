package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JkAnimeRepository
import com.example.model.FavoriteAnime
import com.example.model.ProfileWatchStats
import com.example.model.UserProfile
import com.example.model.WatchHistoryEntry
import com.example.model.WatchStatus
import com.example.model.computeProfileWatchStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: JkAnimeRepository) : ViewModel() {

    val activeProfile: StateFlow<UserProfile> =
        (repository.activeProfile ?: MutableStateFlow(UserProfile("default", "Otaku", "kitsune")))
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UserProfile("default", "Otaku", "kitsune")
            )

    val historyEntries: StateFlow<List<WatchHistoryEntry>> =
        (repository.watchHistory ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<FavoriteAnime>> =
        (repository.favorites ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStatusFilter = MutableStateFlow<WatchStatus?>(null)
    val selectedStatusFilter: StateFlow<WatchStatus?> = _selectedStatusFilter.asStateFlow()

    val filteredFavorites: StateFlow<List<FavoriteAnime>> =
        combine(favorites, _selectedStatusFilter) { list, filter ->
            if (filter == null) list else list.filter { it.watchStatus == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profileStats: StateFlow<ProfileWatchStats> =
        combine(historyEntries, favorites) { history, favs ->
            computeProfileWatchStats(history, favs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileWatchStats())

    fun setStatusFilter(status: WatchStatus?) {
        _selectedStatusFilter.value = status
    }

    fun updateFavoriteStatus(slug: String, providerId: String, status: WatchStatus) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(slug, providerId, status)
        }
    }

    fun removeEntry(slug: String, episodeNumber: Int, providerId: String) {
        viewModelScope.launch {
            repository.removeWatchHistoryEntry(slug, episodeNumber, providerId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearWatchHistory()
        }
    }

    fun removeFavorite(slug: String, providerId: String) {
        viewModelScope.launch {
            repository.removeFavorite(slug, providerId)
        }
    }
}
