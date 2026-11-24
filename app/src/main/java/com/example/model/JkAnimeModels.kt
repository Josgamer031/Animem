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

data class ResolvedVideo(
    val serverName: String,
    val streamUrl: String?,
    val embedUrl: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String> = emptyMap()
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
    val episodeUrl: String = ""
)
