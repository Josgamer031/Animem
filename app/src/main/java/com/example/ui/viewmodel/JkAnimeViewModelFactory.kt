package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider

class JkAnimeViewModelFactory(
    private val repository: JkAnimeRepository,
    private val slug: String = "",
    private val episodeNumber: Int = 1,
    private val animeTitle: String = "",
    private val coverUrl: String = "",
    private val providerId: String = AnimeProvider.JKANIME.id,
    private val episodeUrl: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val provider = AnimeProvider.fromId(providerId)
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository) as T
            modelClass.isAssignableFrom(ExploreViewModel::class.java) ->
                ExploreViewModel(repository) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository) as T
            modelClass.isAssignableFrom(AnimeDetailViewModel::class.java) ->
                AnimeDetailViewModel(repository, slug, provider) as T
            modelClass.isAssignableFrom(PlayerViewModel::class.java) ->
                PlayerViewModel(repository, slug, episodeNumber, animeTitle, coverUrl, provider, episodeUrl) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
