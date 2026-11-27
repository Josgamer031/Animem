package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.AnimeProvider
import com.example.notifications.DeepLinkIntentHelper
import com.example.notifications.PremiereAlertScheduler

class KitsuneHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            PremiereAlertScheduler.triggerImmediateCheck(context)
            requestUpdateAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH"

        fun requestUpdateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, KitsuneHomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(appContext, manager, id)
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val snapshot = WidgetSnapshotStore.load(context)
            val views = RemoteViews(context.packageName, R.layout.widget_kitsune_home)

            views.setTextViewText(
                R.id.widget_profile_label,
                if (snapshot.updatedAtLabel.isNotBlank()) {
                    "Perfil: ${snapshot.activeProfileName} · ${snapshot.updatedAtLabel}"
                } else {
                    "Perfil: ${snapshot.activeProfileName}"
                }
            )

            // Open app when tapping header/logo
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

            // Refresh button
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

            // Continue Watching Card
            if (snapshot.continueSlug.isNotBlank() && snapshot.continueEpisodeNumber > 0) {
                val providerName = AnimeProvider.fromId(snapshot.continueProviderId).displayName
                views.setTextViewText(R.id.widget_continue_title, snapshot.continueTitle)
                views.setTextViewText(
                    R.id.widget_continue_subtitle,
                    "Episodio ${snapshot.continueEpisodeNumber} · $providerName"
                )
                views.setViewVisibility(R.id.widget_resume_btn, View.VISIBLE)
                views.setViewVisibility(R.id.widget_continue_progress, View.VISIBLE)
                views.setProgressBar(
                    R.id.widget_continue_progress,
                    100,
                    snapshot.continueProgressPercent.coerceIn(5, 100),
                    false
                )

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
                    R.id.widget_continue_title,
                    context.getString(R.string.widget_no_history)
                )
                views.setTextViewText(
                    R.id.widget_continue_subtitle,
                    context.getString(R.string.widget_tap_to_explore)
                )
                views.setViewVisibility(R.id.widget_resume_btn, View.GONE)
                views.setViewVisibility(R.id.widget_continue_progress, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_continue_card, openAppPending)
            }

            // Premieres rows (up to 3)
            views.setTextViewText(R.id.widget_premieres_header, snapshot.todayDayLabel)
            val rowConfigs = listOf(
                Triple(R.id.widget_premiere_row_1, R.id.widget_premiere_title_1, R.id.widget_premiere_badge_1),
                Triple(R.id.widget_premiere_row_2, R.id.widget_premiere_title_2, R.id.widget_premiere_badge_2),
                Triple(R.id.widget_premiere_row_3, R.id.widget_premiere_title_3, R.id.widget_premiere_badge_3)
            )
            val premieres = snapshot.todayPremieres.take(3)
            views.setViewVisibility(
                R.id.widget_premieres_empty,
                if (premieres.isEmpty()) View.VISIBLE else View.GONE
            )

            rowConfigs.forEachIndexed { idx, (rowId, titleId, badgeId) ->
                val item = premieres.getOrNull(idx)
                if (item == null) {
                    views.setViewVisibility(rowId, View.GONE)
                } else {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(titleId, item.title)
                    val providerTag = AnimeProvider.fromId(item.providerId).badgeText
                    views.setTextViewText(
                        badgeId,
                        if (item.badge.isNotBlank()) "${item.badge} · $providerTag" else providerTag
                    )
                    val detailIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    DeepLinkIntentHelper.putOpenAnimeDetailExtras(
                        intent = detailIntent,
                        slug = item.slug,
                        providerId = item.providerId
                    )
                    val detailPending = PendingIntent.getActivity(
                        context,
                        3100 + idx,
                        detailIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rowId, detailPending)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
