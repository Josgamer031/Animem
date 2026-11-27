package com.example.notifications

import android.content.Intent
import com.example.model.AnimeProvider
import com.example.model.LaunchDeepLink

object DeepLinkIntentHelper {
    private const val EXTRA_LINK_TYPE = "kitsune_link_type"
    private const val EXTRA_SLUG = "kitsune_slug"
    private const val EXTRA_EPISODE = "kitsune_episode"
    private const val EXTRA_TITLE = "kitsune_title"
    private const val EXTRA_COVER = "kitsune_cover"
    private const val EXTRA_PROVIDER_ID = "kitsune_provider_id"
    private const val EXTRA_EPISODE_URL = "kitsune_episode_url"

    private const val TYPE_PLAYER = "player"
    private const val TYPE_DETAIL = "detail"

    fun putOpenPlayerExtras(
        intent: Intent,
        slug: String,
        episodeNumber: Int,
        animeTitle: String,
        coverUrl: String,
        providerId: String,
        episodeUrl: String = ""
    ): Intent = intent.apply {
        putExtra(EXTRA_LINK_TYPE, TYPE_PLAYER)
        putExtra(EXTRA_SLUG, slug)
        putExtra(EXTRA_EPISODE, episodeNumber)
        putExtra(EXTRA_TITLE, animeTitle)
        putExtra(EXTRA_COVER, coverUrl)
        putExtra(EXTRA_PROVIDER_ID, providerId)
        putExtra(EXTRA_EPISODE_URL, episodeUrl)
    }

    fun putOpenAnimeDetailExtras(
        intent: Intent,
        slug: String,
        providerId: String
    ): Intent = intent.apply {
        putExtra(EXTRA_LINK_TYPE, TYPE_DETAIL)
        putExtra(EXTRA_SLUG, slug)
        putExtra(EXTRA_PROVIDER_ID, providerId)
    }

    fun parse(intent: Intent?): LaunchDeepLink? {
        if (intent == null) return null
        val type = intent.getStringExtra(EXTRA_LINK_TYPE) ?: return null
        val slug = intent.getStringExtra(EXTRA_SLUG)?.takeIf { it.isNotBlank() } ?: return null
        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)?.takeIf { it.isNotBlank() }
            ?: AnimeProvider.JKANIME.id
        return when (type) {
            TYPE_PLAYER -> {
                val ep = intent.getIntExtra(EXTRA_EPISODE, 1).coerceAtLeast(1)
                LaunchDeepLink.OpenPlayer(
                    slug = slug,
                    episodeNumber = ep,
                    animeTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    coverUrl = intent.getStringExtra(EXTRA_COVER).orEmpty(),
                    providerId = providerId,
                    episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL).orEmpty()
                )
            }
            TYPE_DETAIL -> LaunchDeepLink.OpenAnimeDetail(
                slug = slug,
                providerId = providerId
            )
            else -> null
        }
    }
}
