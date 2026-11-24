package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JkAnimeRepository
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: JkAnimeRepository) : ViewModel() {

    val historyEntries: StateFlow<List<WatchHistoryEntry>> =
        (repository.watchHistory ?: MutableStateFlow(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeEntry(slug: String, episodeNumber: Int) {
        viewModelScope.launch {
            repository.removeWatchHistoryEntry(slug, episodeNumber)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearWatchHistory()
        }
    }
}
