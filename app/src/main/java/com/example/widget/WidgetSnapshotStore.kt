package com.example.widget

import android.content.Context
import com.example.model.AnimeProvider
import com.example.model.AppColorTheme
import com.example.model.LatestEpisode
import com.example.model.ScheduleItem
import com.example.model.WatchHistoryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class WidgetPremiereItem(
    val slug: String,
    val title: String,
    val providerId: String,
    val badge: String,
    val coverUrl: String = "",
    val episodeNumber: Int = 0,
    val episodeUrl: String = ""
)

@Serializable
data class WidgetSnapshot(
    val activeProfileName: String = "Otaku",
    val continueSlug: String = "",
    val continueTitle: String = "",
    val continueEpisodeNumber: Int = 0,
    val continueProviderId: String = AnimeProvider.JKANIME.id,
    val continueEpisodeUrl: String = "",
    val continueCoverUrl: String = "",
    val continueProgressPercent: Int = 0,
    val todayDayLabel: String = "Estrenos de hoy",
    val todayPremieres: List<WidgetPremiereItem> = emptyList(),
    val updatedAtLabel: String = "",
    val colorThemeName: String = AppColorTheme.KITSUNE_RED.name,
    val amoledBlack: Boolean = false
) {
    fun resolvedColorTheme(): AppColorTheme =
        AppColorTheme.entries.firstOrNull { it.name == colorThemeName } ?: AppColorTheme.KITSUNE_RED
}

object WidgetSnapshotStore {
    private const val PREFS_NAME = "kitsune_widget_snapshot"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): WidgetSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null) ?: return WidgetSnapshot()
        return try {
            json.decodeFromString<WidgetSnapshot>(raw)
        } catch (_: Throwable) {
            WidgetSnapshot()
        }
    }

    fun update(
        context: Context,
        prefetchCovers: Boolean = true,
        transform: (WidgetSnapshot) -> WidgetSnapshot
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = load(appContext)
        val updated = transform(current).copy(
            updatedAtLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        prefs.edit().putString(KEY_SNAPSHOT_JSON, json.encodeToString(updated)).apply()
        KitsuneHomeWidgetProvider.requestUpdateAll(appContext)
        if (prefetchCovers) {
            WidgetBitmapCache.prefetchForSnapshotAsync(appContext, updated)
        }
    }

    fun updateTheme(
        context: Context,
        colorTheme: AppColorTheme? = null,
        amoledBlack: Boolean? = null
    ) {
        update(context, prefetchCovers = false) { current ->
            current.copy(
                colorThemeName = colorTheme?.name ?: current.colorThemeName,
                amoledBlack = amoledBlack ?: current.amoledBlack
            )
        }
    }

    fun updateContinueWatching(
        context: Context,
        profileName: String,
        latestEntry: WatchHistoryEntry?
    ) {
        update(context) { current ->
            if (latestEntry == null) {
                current.copy(
                    activeProfileName = profileName,
                    continueSlug = "",
                    continueTitle = "",
                    continueEpisodeNumber = 0,
                    continueCoverUrl = "",
                    continueProgressPercent = 0
                )
            } else {
                val pct = if (latestEntry.durationMs > 0L) {
                    ((latestEntry.positionMs * 100L) / latestEntry.durationMs).toInt().coerceIn(5, 100)
                } else {
                    15
                }
                current.copy(
                    activeProfileName = profileName,
                    continueSlug = latestEntry.animeSlug,
                    continueTitle = latestEntry.animeTitle,
                    continueEpisodeNumber = latestEntry.episodeNumber,
                    continueProviderId = latestEntry.providerId,
                    continueEpisodeUrl = latestEntry.episodeUrl,
                    continueCoverUrl = latestEntry.coverUrl,
                    continueProgressPercent = pct
                )
            }
        }
    }

    fun updateTodayPremieresFromEpisodes(
        context: Context,
        episodes: List<LatestEpisode>
    ) {
        if (episodes.isEmpty()) return
        val items = episodes.take(4).map { ep ->
            WidgetPremiereItem(
                slug = ep.animeSlug,
                title = ep.animeTitle,
                providerId = ep.provider.id,
                badge = ep.episodeLabel.ifBlank { "Ep. ${ep.episodeNumber}" },
                coverUrl = ep.coverUrl,
                episodeNumber = ep.episodeNumber.coerceAtLeast(1),
                episodeUrl = ep.episodeUrl
            )
        }
        update(context) { current ->
            current.copy(
                todayDayLabel = "Estrenos recientes",
                todayPremieres = items
            )
        }
    }

    fun updateTodayPremieresFromSchedule(
        context: Context,
        dayLabel: String,
        scheduleItems: List<ScheduleItem>
    ) {
        if (scheduleItems.isEmpty()) return
        val items = scheduleItems.take(4).map { item ->
            WidgetPremiereItem(
                slug = item.slug,
                title = item.title,
                providerId = item.provider.id,
                badge = item.releaseTime.ifBlank { item.episodeLabel.ifBlank { item.provider.badgeText } },
                coverUrl = item.coverUrl,
                episodeNumber = 0,
                episodeUrl = ""
            )
        }
        update(context) { current ->
            current.copy(
                todayDayLabel = "Estrenos · $dayLabel",
                todayPremieres = items
            )
        }
    }
}
