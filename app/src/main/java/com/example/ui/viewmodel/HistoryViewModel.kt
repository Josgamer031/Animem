package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JkAnimeRepository
import com.example.model.FavoriteAnime
import com.example.model.UserProfile
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
