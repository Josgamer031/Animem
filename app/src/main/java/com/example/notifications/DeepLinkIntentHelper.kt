package com.example.notifications

import android.content.Intent
import android.net.Uri
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

    private val NON_ANIME_JK_SEGMENTS = setOf(
        "directorio", "buscar", "horario", "emision", "estrenos", "peliculas",
        "ovas", "onas", "especiales", "genero", "letra", "top", "ajax",
        "user", "login", "registro", "perfil", "terminos", "privacidad",
        "terminos-y-condiciones", "politica-de-privacidad", "dmca", "contacto"
    )

    fun buildAnimeWebUrl(slug: String, provider: AnimeProvider): String = when (provider) {
        AnimeProvider.LATANIME -> "${provider.baseUrl}/anime/$slug"
        AnimeProvider.JKANIME -> "${provider.baseUrl}/$slug/"
    }

    fun buildEpisodeWebUrl(
        slug: String,
        episodeNumber: Int,
        provider: AnimeProvider,
        explicitEpisodeUrl: String = ""
    ): String {
        if (explicitEpisodeUrl.startsWith("http")) return explicitEpisodeUrl
        return when (provider) {
            AnimeProvider.LATANIME -> "${provider.baseUrl}/ver/$slug-episodio-$episodeNumber"
            AnimeProvider.JKANIME -> "${provider.baseUrl}/$slug/$episodeNumber/"
        }
    }

    fun buildAnimeAppUri(slug: String, provider: AnimeProvider): String =
        "kitsunestream://anime/${provider.id}/$slug"

    fun buildEpisodeAppUri(slug: String, episodeNumber: Int, provider: AnimeProvider): String =
        "kitsunestream://episode/${provider.id}/$slug/$episodeNumber"

    fun buildAnimeShareText(title: String, slug: String, provider: AnimeProvider): String {
        val webUrl = buildAnimeWebUrl(slug, provider)
        val appUri = buildAnimeAppUri(slug, provider)
        return "Mira $title en KitsuneStream (${provider.displayName}):\n$webUrl\nAbrir en la app: $appUri"
    }

    fun buildEpisodeShareText(
        title: String,
        episodeNumber: Int,
        slug: String,
        provider: AnimeProvider,
        explicitEpisodeUrl: String = ""
    ): String {
        val webUrl = buildEpisodeWebUrl(slug, episodeNumber, provider, explicitEpisodeUrl)
        val appUri = buildEpisodeAppUri(slug, episodeNumber, provider)
        return "Mira $title - Episodio $episodeNumber en KitsuneStream (${provider.displayName}):\n$webUrl\nAbrir en la app: $appUri"
    }

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

        val type = intent.getStringExtra(EXTRA_LINK_TYPE)
        if (type != null) {
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

        val dataUri = intent.data ?: return null
        return parseUri(dataUri)
    }

    fun parseUri(uri: Uri): LaunchDeepLink? {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments.filter { it.isNotBlank() }

        if (scheme == "kitsunestream") {
            return when (host) {
                "anime" -> {
                    when {
                        segments.size >= 2 -> {
                            val provider = AnimeProvider.fromId(segments[0])
                            LaunchDeepLink.OpenAnimeDetail(
                                slug = segments[1],
                                providerId = provider.id
                            )
                        }
                        segments.size == 1 -> LaunchDeepLink.OpenAnimeDetail(
                            slug = segments[0],
                            providerId = AnimeProvider.JKANIME.id
                        )
                        else -> null
                    }
                }
                "episode" -> {
                    when {
                        segments.size >= 3 -> {
                            val provider = AnimeProvider.fromId(segments[0])
                            val ep = segments[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
                            LaunchDeepLink.OpenPlayer(
                                slug = segments[1],
                                episodeNumber = ep,
                                providerId = provider.id
                            )
                        }
                        segments.size == 2 -> {
                            val ep = segments[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
                            LaunchDeepLink.OpenPlayer(
                                slug = segments[0],
                                episodeNumber = ep,
                                providerId = AnimeProvider.JKANIME.id
                            )
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }

        if (host == "kitsunestream.app") {
            return when (segments.firstOrNull()?.lowercase()) {
                "anime" -> {
                    val providerId = segments.getOrNull(1)?.let { AnimeProvider.fromId(it).id } ?: return null
                    val slug = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
                    LaunchDeepLink.OpenAnimeDetail(slug = slug, providerId = providerId)
                }
                "episode" -> {
                    val providerId = segments.getOrNull(1)?.let { AnimeProvider.fromId(it).id } ?: return null
                    val slug = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
                    val ep = segments.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    LaunchDeepLink.OpenPlayer(slug = slug, episodeNumber = ep, providerId = providerId)
                }
                else -> null
            }
        }

        if (host.contains("jkanime")) {
            val slug = segments.firstOrNull()?.takeIf {
                it.isNotBlank() && it.lowercase() !in NON_ANIME_JK_SEGMENTS && !it.contains('.')
            } ?: return null
            val epNum = segments.getOrNull(1)?.toIntOrNull()
            return if (epNum != null && epNum >= 1) {
                LaunchDeepLink.OpenPlayer(
                    slug = slug,
                    episodeNumber = epNum,
                    providerId = AnimeProvider.JKANIME.id,
                    episodeUrl = uri.toString()
                )
            } else {
                LaunchDeepLink.OpenAnimeDetail(
                    slug = slug,
                    providerId = AnimeProvider.JKANIME.id
                )
            }
        }

        if (host.contains("latanime")) {
            val first = segments.firstOrNull()?.lowercase() ?: return null
            if (first == "anime") {
                val slug = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                return LaunchDeepLink.OpenAnimeDetail(
                    slug = slug,
                    providerId = AnimeProvider.LATANIME.id
                )
            }
            if (first == "ver") {
                val epSlug = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                val epRegex = Regex("""-(?:episodio|capitulo|cap|ep)-(\d+)$""", RegexOption.IGNORE_CASE)
                val match = epRegex.find(epSlug)
                val epNum = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""-(\d+)$""").find(epSlug)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: 1
                val animeSlug = epSlug
                    .replace(epRegex, "")
                    .replace(Regex("""-\d+$"""), "")
                    .ifBlank { epSlug }
                return LaunchDeepLink.OpenPlayer(
                    slug = animeSlug,
                    episodeNumber = epNum.coerceAtLeast(1),
                    providerId = AnimeProvider.LATANIME.id,
                    episodeUrl = uri.toString()
                )
            }
        }

        return null
    }
}
