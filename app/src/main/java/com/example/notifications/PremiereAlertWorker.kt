package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.FavoritesStore
import com.example.data.JkAnimeRepository
import com.example.data.UserPreferencesStore
import com.example.model.AnimeProvider
import com.example.model.LatestEpisode
import com.example.model.WatchStatus
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class PremiereAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefsStore = UserPreferencesStore(applicationContext)
        val prefs = prefsStore.preferencesFlow.firstOrNull()
        val forceCheck = inputData.getBoolean(KEY_FORCE_CHECK, false)

        if (!forceCheck && prefs?.premiereAlertsEnabled != true) {
            return Result.success()
        }

        val favorites = FavoritesStore(applicationContext).favoritesFlow.firstOrNull().orEmpty()
        val trackedFavorites = favorites.filter {
            it.watchStatus == WatchStatus.WATCHING || it.watchStatus == WatchStatus.PLAN_TO_WATCH
        }

        val repository = JkAnimeRepository(applicationContext)
        val providersToFetch = buildSet {
            add(AnimeProvider.JKANIME)
            if (trackedFavorites.any { it.providerId == AnimeProvider.LATANIME.id } || forceCheck) {
                add(AnimeProvider.LATANIME)
            }
        }

        val allLatestEpisodes = mutableListOf<LatestEpisode>()
        for (provider in providersToFetch) {
            val feed = repository.getHomeFeed(provider).getOrNull()
            if (feed != null) {
                allLatestEpisodes += feed.latestEpisodes
            }
        }

        // Also keep today's schedule snapshot fresh for the home widget
        repository.getSchedule(AnimeProvider.JKANIME)

        if (trackedFavorites.isEmpty() || allLatestEpisodes.isEmpty()) {
            return Result.success()
        }

        val alreadyNotified = prefs?.notifiedPremiereKeys.orEmpty().toSet()
        val trackedByKey = trackedFavorites.associateBy { "${it.providerId}:${it.animeSlug}" }

        val newMatches = allLatestEpisodes
            .filter { ep ->
                val listKey = "${ep.provider.id}:${ep.animeSlug}"
                val premiereKey = "$listKey:${ep.episodeNumber}"
                trackedByKey.containsKey(listKey) && premiereKey !in alreadyNotified
            }
            .distinctBy { "${it.provider.id}:${it.animeSlug}:${it.episodeNumber}" }

        if (newMatches.isEmpty()) {
            return Result.success()
        }

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (canPost) {
            ensureChannel()
            newMatches.take(4).forEachIndexed { idx, ep ->
                postEpisodeNotification(ep, idx)
            }
        }

        val keysToRecord = newMatches.map { "${it.provider.id}:${it.animeSlug}:${it.episodeNumber}" }
        prefsStore.recordNotifiedPremiereKeys(keysToRecord)
        return Result.success()
    }

    private fun postEpisodeNotification(ep: LatestEpisode, offset: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        DeepLinkIntentHelper.putOpenPlayerExtras(
            intent = intent,
            slug = ep.animeSlug,
            episodeNumber = ep.episodeNumber.coerceAtLeast(1),
            animeTitle = ep.animeTitle,
            coverUrl = ep.coverUrl,
            providerId = ep.provider.id,
            episodeUrl = ep.episodeUrl
        )
        val requestCode = ("${ep.provider.id}:${ep.animeSlug}:${ep.episodeNumber}").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val epText = ep.episodeLabel.ifBlank { "Episodio ${ep.episodeNumber}" }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitsune)
            .setContentTitle(
                applicationContext.getString(R.string.notification_premiere_title, ep.provider.displayName)
            )
            .setContentText("${ep.animeTitle} · $epText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${ep.animeTitle} acaba de estrenar $epText en ${ep.provider.displayName}. Toca para reproducirlo ahora.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_BASE_ID + offset + (ep.animeSlug.hashCode() and 0xFF), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notification_channel_premieres_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = applicationContext.getString(R.string.notification_channel_premieres_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val KEY_FORCE_CHECK = "force_check"
        private const val CHANNEL_ID = "kitsune_premieres"
        private const val NOTIFICATION_BASE_ID = 5200
    }
}

object PremiereAlertScheduler {
    private const val PERIODIC_WORK_NAME = "kitsune_premiere_alerts_periodic"
    private const val IMMEDIATE_WORK_NAME = "kitsune_premiere_alerts_now"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PremiereAlertWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun triggerImmediateCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data = Data.Builder()
            .putBoolean(PremiereAlertWorker.KEY_FORCE_CHECK, true)
            .build()
        val request = OneTimeWorkRequestBuilder<PremiereAlertWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
