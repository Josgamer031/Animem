package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.JkAnimeRepository
import com.example.data.ProfileStore
import com.example.data.UserPreferencesStore
import com.example.data.WatchHistoryStore
import com.example.model.AnimeProvider
import com.example.model.AppColorTheme
import com.example.notifications.DeepLinkIntentHelper
import com.example.notifications.PremiereAlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class KitsuneHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            updateAppWidget(context, appWidgetManager, appWidgetId, options = options)
        }
        WidgetBitmapCache.prefetchForSnapshotAsync(context, WidgetSnapshotStore.load(context))
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId, options = newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appContext = context.applicationContext
            val pendingResult = goAsync()
            requestUpdateAll(appContext, isRefreshing = true)
            PremiereAlertScheduler.triggerImmediateCheck(appContext)
            refreshScope.launch {
                try {
                    withTimeoutOrNull(14_000L) {
                        performLiveRefresh(appContext)
                    }
                } finally {
                    requestUpdateAll(appContext, isRefreshing = false)
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH"
        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private data class PremiereRowViews(
            val rowId: Int,
            val coverId: Int,
            val titleId: Int,
            val badgeId: Int
        )

        fun triggerLiveRefresh(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KitsuneHomeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            appContext.sendBroadcast(intent)
        }

        fun requestUpdateAll(context: Context, isRefreshing: Boolean = false) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, KitsuneHomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val options = manager.getAppWidgetOptions(id)
                    updateAppWidget(
                        context = appContext,
                        appWidgetManager = manager,
                        appWidgetId = id,
                        options = options,
                        isRefreshing = isRefreshing
                    )
                }
            }
        }

        private suspend fun performLiveRefresh(context: Context) {
            val prefs = UserPreferencesStore(context).preferencesFlow.firstOrNull()
            if (prefs != null) {
                WidgetSnapshotStore.updateTheme(
                    context = context,
                    colorTheme = prefs.colorTheme,
                    amoledBlack = prefs.amoledBlack
                )
            }

            val profileStore = ProfileStore(context)
            val activeProfile = profileStore.activeProfileFlow.firstOrNull()
            val profileId = activeProfile?.id ?: "default"
            val profileName = activeProfile?.name ?: "Otaku"
            val latestHistory = WatchHistoryStore(context).historyFlow.firstOrNull()
                ?.firstOrNull { it.profileId == profileId }
            WidgetSnapshotStore.updateContinueWatching(
                context = context,
                profileName = profileName,
                latestEntry = latestHistory
            )

            val repository = JkAnimeRepository(context)
            val feed = repository.getHomeFeed(AnimeProvider.JKANIME).getOrNull()
            if (feed != null && feed.latestEpisodes.isNotEmpty()) {
                WidgetSnapshotStore.updateTodayPremieresFromEpisodes(context, feed.latestEpisodes)
            }
            val schedule = repository.getSchedule(AnimeProvider.JKANIME).getOrNull()
            if (schedule != null) {
                val todayKey = currentDayKey()
                val today = schedule.days.firstOrNull { it.dayKey == todayKey && it.items.isNotEmpty() }
                    ?: schedule.days.firstOrNull { it.items.isNotEmpty() }
                if (today != null) {
                    WidgetSnapshotStore.updateTodayPremieresFromSchedule(
                        context = context,
                        dayLabel = today.dayLabel,
                        scheduleItems = today.items
                    )
                }
            }

            val latestSnapshot = WidgetSnapshotStore.load(context)
            WidgetBitmapCache.prefetchForSnapshotSync(context, latestSnapshot)
        }

        private fun currentDayKey(): String =
            when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "lunes"
                java.util.Calendar.TUESDAY -> "martes"
                java.util.Calendar.WEDNESDAY -> "miercoles"
                java.util.Calendar.THURSDAY -> "jueves"
                java.util.Calendar.FRIDAY -> "viernes"
                java.util.Calendar.SATURDAY -> "sabado"
                else -> "domingo"
            }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle? = null,
            isRefreshing: Boolean = false
        ) {
            val snapshot = WidgetSnapshotStore.load(context)
            val theme = snapshot.resolvedColorTheme()
            val primaryColor = (0xFF000000L or (theme.primaryHex and 0x00FFFFFFL)).toInt()
            val secondaryColor = (0xFF000000L or (theme.secondaryHex and 0x00FFFFFFL)).toInt()
            val tertiaryColor = (0xFF000000L or (theme.tertiaryHex and 0x00FFFFFFL)).toInt()

            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200) ?: 200
            val maxVisiblePremieres = when {
                minHeightDp < 130 -> 0
                minHeightDp < 185 -> 1
                minHeightDp < 245 -> 2
                else -> 3
            }

            val views = RemoteViews(context.packageName, R.layout.widget_kitsune_home)

            // Apply AMOLED vs Obsidian surface theme
            val rootBgRes = if (snapshot.amoledBlack) {
                R.drawable.widget_kitsune_bg_amoled
            } else {
                R.drawable.widget_kitsune_bg
            }
            val cardBgRes = if (snapshot.amoledBlack) {
                R.drawable.widget_card_bg_amoled
            } else {
                R.drawable.widget_card_bg
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", rootBgRes)
            views.setInt(R.id.widget_continue_card, "setBackgroundResource", cardBgRes)

            // Apply theme accent colors
            views.setInt(R.id.widget_logo_icon, "setColorFilter", primaryColor)
            views.setTextColor(R.id.widget_continue_kicker, primaryColor)
            views.setTextColor(R.id.widget_premieres_header, tertiaryColor)
            views.setInt(R.id.widget_refresh_btn, "setColorFilter", tertiaryColor)

            val pillBgRes = when (theme) {
                AppColorTheme.KITSUNE_RED -> R.drawable.widget_pill_crimson
                AppColorTheme.SAKURA_PINK -> R.drawable.widget_pill_sakura
                AppColorTheme.CYBER_CYAN -> R.drawable.widget_pill_cyan
                AppColorTheme.EMERALD_JADE -> R.drawable.widget_pill_emerald
                AppColorTheme.SOLAR_GOLD -> R.drawable.widget_pill_solar
            }
            views.setInt(R.id.widget_resume_btn, "setBackgroundResource", pillBgRes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_continue_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(primaryColor)
                )
            }

            // Header status label
            val profileLine = when {
                isRefreshing -> "${snapshot.activeProfileName} · ${context.getString(R.string.widget_updating)}"
                snapshot.updatedAtLabel.isNotBlank() -> "Perfil: ${snapshot.activeProfileName} · ${snapshot.updatedAtLabel}"
                else -> "Perfil: ${snapshot.activeProfileName}"
            }
            views.setTextViewText(R.id.widget_profile_label, profileLine)

            // Open app intents
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                3001,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_brand_title, openAppPending)
            views.setOnClickPendingIntent(R.id.widget_logo_icon, openAppPending)
            views.setOnClickPendingIntent(R.id.widget_header_title_group, openAppPending)

            // Quick Search shortcut
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            DeepLinkIntentHelper.putOpenTabExtras(searchIntent, DeepLinkIntentHelper.TAB_EXPLORE)
            val searchPending = PendingIntent.getActivity(
                context,
                3004,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_search_btn, searchPending)

            // Quick Schedule shortcut
            val scheduleIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            DeepLinkIntentHelper.putOpenTabExtras(scheduleIntent, DeepLinkIntentHelper.TAB_SCHEDULE)
            val schedulePending = PendingIntent.getActivity(
                context,
                3005,
                scheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_schedule_btn, schedulePending)
            views.setOnClickPendingIntent(R.id.widget_premieres_more, schedulePending)

            // Live Refresh button
            val refreshIntent = Intent(context, KitsuneHomeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                3002,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPending)

            // Continue Watching Hero Card
            if (snapshot.continueSlug.isNotBlank() && snapshot.continueEpisodeNumber > 0) {
                val providerName = AnimeProvider.fromId(snapshot.continueProviderId).displayName
                val pct = snapshot.continueProgressPercent.coerceIn(5, 100)
                views.setTextViewText(
                    R.id.widget_continue_kicker,
                    "${context.getString(R.string.section_continue_watching).uppercase()} · $pct%"
                )
                views.setTextViewText(R.id.widget_continue_title, snapshot.continueTitle)
                views.setTextViewText(
                    R.id.widget_continue_subtitle,
                    "Episodio ${snapshot.continueEpisodeNumber} · $providerName"
                )
                views.setViewVisibility(R.id.widget_resume_btn, View.VISIBLE)
                views.setViewVisibility(R.id.widget_continue_progress, View.VISIBLE)
                views.setProgressBar(R.id.widget_continue_progress, 100, pct, false)

                val cachedHeroCover = WidgetBitmapCache.loadCachedBitmap(
                    context = context,
                    url = snapshot.continueCoverUrl,
                    widthPx = 112,
                    heightPx = 154
                )
                if (cachedHeroCover != null) {
                    views.setImageViewBitmap(R.id.widget_continue_cover, cachedHeroCover)
                } else {
                    views.setImageViewResource(
                        R.id.widget_continue_cover,
                        R.drawable.ic_widget_poster_placeholder
                    )
                }

                val playIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                DeepLinkIntentHelper.putOpenPlayerExtras(
                    intent = playIntent,
                    slug = snapshot.continueSlug,
                    episodeNumber = snapshot.continueEpisodeNumber,
                    animeTitle = snapshot.continueTitle,
                    coverUrl = snapshot.continueCoverUrl,
                    providerId = snapshot.continueProviderId,
                    episodeUrl = snapshot.continueEpisodeUrl
                )
                val playPending = PendingIntent.getActivity(
                    context,
                    3003,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_continue_card, playPending)
                views.setOnClickPendingIntent(R.id.widget_resume_btn, playPending)
            } else {
                views.setTextViewText(
                    R.id.widget_continue_kicker,
                    context.getString(R.string.section_continue_watching).uppercase()
                )
                views.setTextViewText(
                    R.id.widget_continue_title,
                    context.getString(R.string.widget_no_history)
                )
                views.setTextViewText(
                    R.id.widget_continue_subtitle,
                    context.getString(R.string.widget_tap_to_explore)
                )
                views.setImageViewResource(
                    R.id.widget_continue_cover,
                    R.drawable.ic_widget_poster_placeholder
                )
                views.setViewVisibility(R.id.widget_resume_btn, View.GONE)
                views.setViewVisibility(R.id.widget_continue_progress, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_continue_card, openAppPending)
            }

            // Premieres Section (responsive row count based on widget height)
            if (maxVisiblePremieres == 0) {
                views.setViewVisibility(R.id.widget_premieres_section, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_premieres_section, View.VISIBLE)
                views.setTextViewText(R.id.widget_premieres_header, snapshot.todayDayLabel)

                val rowConfigs = listOf(
                    PremiereRowViews(
                        R.id.widget_premiere_row_1,
                        R.id.widget_premiere_cover_1,
                        R.id.widget_premiere_title_1,
                        R.id.widget_premiere_badge_1
                    ),
                    PremiereRowViews(
                        R.id.widget_premiere_row_2,
                        R.id.widget_premiere_cover_2,
                        R.id.widget_premiere_title_2,
                        R.id.widget_premiere_badge_2
                    ),
                    PremiereRowViews(
                        R.id.widget_premiere_row_3,
                        R.id.widget_premiere_cover_3,
                        R.id.widget_premiere_title_3,
                        R.id.widget_premiere_badge_3
                    )
                )
                val premieres = snapshot.todayPremieres.take(maxVisiblePremieres)
                views.setViewVisibility(
                    R.id.widget_premieres_empty,
                    if (premieres.isEmpty()) View.VISIBLE else View.GONE
                )

                rowConfigs.forEachIndexed { idx, row ->
                    val item = premieres.getOrNull(idx)
                    if (item == null) {
                        views.setViewVisibility(row.rowId, View.GONE)
                    } else {
                        views.setViewVisibility(row.rowId, View.VISIBLE)
                        views.setInt(row.rowId, "setBackgroundResource", cardBgRes)
                        views.setTextViewText(row.titleId, item.title)
                        val providerTag = AnimeProvider.fromId(item.providerId).badgeText
                        views.setTextViewText(
                            row.badgeId,
                            if (item.badge.isNotBlank()) "${item.badge} · $providerTag" else providerTag
                        )
                        views.setTextColor(row.badgeId, secondaryColor)

                        val coverBmp = WidgetBitmapCache.loadCachedBitmap(
                            context = context,
                            url = item.coverUrl,
                            widthPx = 68,
                            heightPx = 94
                        )
                        if (coverBmp != null) {
                            views.setImageViewBitmap(row.coverId, coverBmp)
                        } else {
                            views.setImageViewResource(
                                row.coverId,
                                R.drawable.ic_widget_poster_placeholder
                            )
                        }

                        val rowIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        if (item.episodeNumber > 0) {
                            DeepLinkIntentHelper.putOpenPlayerExtras(
                                intent = rowIntent,
                                slug = item.slug,
                                episodeNumber = item.episodeNumber,
                                animeTitle = item.title,
                                coverUrl = item.coverUrl,
                                providerId = item.providerId,
                                episodeUrl = item.episodeUrl
                            )
                        } else {
                            DeepLinkIntentHelper.putOpenAnimeDetailExtras(
                                intent = rowIntent,
                                slug = item.slug,
                                providerId = item.providerId
                            )
                        }
                        val rowPending = PendingIntent.getActivity(
                            context,
                            3100 + idx,
                            rowIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(row.rowId, rowPending)
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
