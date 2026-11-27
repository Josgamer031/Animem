package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnimeProvider(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val baseUrl: String,
    val badgeText: String
) {
    JKANIME(
        id = "jkanime",
        displayName = "JKAnime",
        shortLabel = "Sub Español",
        baseUrl = "https://jkanime.net",
        badgeText = "JKAnime"
    ),
    LATANIME(
        id = "latanime",
        displayName = "Latanime",
        shortLabel = "Español Latino",
        baseUrl = "https://latanime.org",
        badgeText = "Latino"
    );

    companion object {
        fun fromId(id: String?): AnimeProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: JKANIME
    }
}

data class AnimeSummary(
    val slug: String,
    val title: String,
    val url: String,
    val coverUrl: String,
    val type: String = "",
    val status: String = "",
    val episodeBadge: String = "",
    val synopsisSnippet: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class LatestEpisode(
    val animeSlug: String,
    val animeTitle: String,
    val episodeNumber: Int,
    val episodeLabel: String,
    val episodeUrl: String,
    val coverUrl: String,
    val typeLabel: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class HomeFeed(
    val featured: List<AnimeSummary> = emptyList(),
    val latestEpisodes: List<LatestEpisode> = emptyList(),
    val airing: List<AnimeSummary> = emptyList(),
    val recentAdded: List<AnimeSummary> = emptyList(),
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class EpisodeItem(
    val number: Int,
    val label: String,
    val url: String,
    val thumbnailUrl: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class EpisodePageRange(
    val page: Int,
    val label: String
)

data class AnimeDetail(
    val slug: String,
    val animeId: String,
    val title: String,
    val alternativeTitle: String = "",
    val coverUrl: String,
    val synopsis: String,
    val status: String = "",
    val type: String = "",
    val airedInfo: String = "",
    val totalEpisodesText: String = "",
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val pageRanges: List<EpisodePageRange> = emptyList(),
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class VideoServer(
    val name: String,
    val rawUrl: String,
    val decodedEmbedUrl: String,
    val isInternalJkPlayer: Boolean = false
)

data class EpisodePlaybackInfo(
    val servers: List<VideoServer>,
    val hasNextEpisode: Boolean? = null,
    val resolvedEpisodeUrl: String = ""
)

data class ResolvedVideo(
    val serverName: String,
    val streamUrl: String?,
    val embedUrl: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val usedWebSniff: Boolean = false,
    val isHls: Boolean = false
)

data class DirectoryPage(
    val items: List<AnimeSummary>,
    val currentPage: Int,
    val hasNextPage: Boolean
)

data class DirectoryFilter(
    val query: String = "",
    val status: String = "",
    val type: String = "",
    val genre: String = "",
    val page: Int = 1,
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

@Serializable
data class WatchHistoryEntry(
    val animeSlug: String,
    val animeTitle: String,
    val coverUrl: String,
    val episodeNumber: Int,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val providerId: String = AnimeProvider.JKANIME.id,
    val episodeUrl: String = "",
    val profileId: String = "default"
)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarKey: String = "kitsune",
    val preferredProviderId: String = AnimeProvider.JKANIME.id,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ScheduleItem(
    val slug: String,
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val releaseTime: String = "",
    val episodeLabel: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME
)

data class ScheduleDay(
    val dayKey: String,
    val dayLabel: String,
    val items: List<ScheduleItem> = emptyList()
)

data class AnimeSchedule(
    val provider: AnimeProvider,
    val days: List<ScheduleDay> = emptyList()
)

@Serializable
enum class WatchStatus(val label: String) {
    WATCHING("Viendo"),
    PLAN_TO_WATCH("Por ver"),
    COMPLETED("Completados"),
    DROPPED("Abandonados")
}

@Serializable
data class FavoriteAnime(
    val animeSlug: String,
    val animeTitle: String,
    val coverUrl: String = "",
    val type: String = "",
    val status: String = "",
    val totalEpisodesText: String = "",
    val providerId: String = AnimeProvider.JKANIME.id,
    val profileId: String = "default",
    val addedAtMillis: Long = System.currentTimeMillis(),
    val watchStatus: WatchStatus = WatchStatus.WATCHING
)

data class ProfileWatchStats(
    val totalEpisodesWatched: Int = 0,
    val uniqueAnimesWatched: Int = 0,
    val totalWatchTimeMs: Long = 0L,
    val watchingCount: Int = 0,
    val planToWatchCount: Int = 0,
    val completedCount: Int = 0,
    val droppedCount: Int = 0,
    val jkanimeEpisodesCount: Int = 0,
    val latanimeEpisodesCount: Int = 0
) {
    val totalInList: Int
        get() = watchingCount + planToWatchCount + completedCount + droppedCount
}

fun computeProfileWatchStats(
    history: List<WatchHistoryEntry>,
    favorites: List<FavoriteAnime>
): ProfileWatchStats {
    val distinctEpisodes = history.distinctBy { "${it.providerId}:${it.animeSlug}:${it.episodeNumber}" }
    val uniqueAnimes = distinctEpisodes.distinctBy { "${it.providerId}:${it.animeSlug}" }.size
    val totalMs = distinctEpisodes.sumOf { entry ->
        when {
            entry.positionMs > 0L -> entry.positionMs
            entry.durationMs > 0L -> entry.durationMs
            else -> 0L
        }
    }
    return ProfileWatchStats(
        totalEpisodesWatched = distinctEpisodes.size,
        uniqueAnimesWatched = uniqueAnimes,
        totalWatchTimeMs = totalMs,
        watchingCount = favorites.count { it.watchStatus == WatchStatus.WATCHING },
        planToWatchCount = favorites.count { it.watchStatus == WatchStatus.PLAN_TO_WATCH },
        completedCount = favorites.count { it.watchStatus == WatchStatus.COMPLETED },
        droppedCount = favorites.count { it.watchStatus == WatchStatus.DROPPED },
        jkanimeEpisodesCount = distinctEpisodes.count { it.providerId == AnimeProvider.JKANIME.id },
        latanimeEpisodesCount = distinctEpisodes.count { it.providerId == AnimeProvider.LATANIME.id }
    )
}

fun formatWatchDuration(ms: Long): String {
    if (ms <= 0L) return "0 min"
    val totalMinutes = (ms / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes} min"
    }
}

sealed interface LaunchDeepLink {
    data class OpenPlayer(
        val slug: String,
        val episodeNumber: Int,
        val animeTitle: String = "",
        val coverUrl: String = "",
        val providerId: String = AnimeProvider.JKANIME.id,
        val episodeUrl: String = ""
    ) : LaunchDeepLink

    data class OpenAnimeDetail(
        val slug: String,
        val providerId: String = AnimeProvider.JKANIME.id
    ) : LaunchDeepLink
}

@Serializable
enum class DeviceMode {
    PHONE,
    SMART_TV
}

@Serializable
data class UserPreferences(
    val autoPlayNext: Boolean = true,
    val autoSkipOpening: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val deviceMode: DeviceMode = DeviceMode.PHONE,
    val hasSelectedDeviceMode: Boolean = false,
    val premiereAlertsEnabled: Boolean = false,
    val notifiedPremiereKeys: List<String> = emptyList()
)




