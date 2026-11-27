package com.example.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.MainActivity
import com.example.R
import com.example.model.AnimeProvider
import java.util.UUID

@OptIn(UnstableApi::class)
class EpisodeMediaSessionManager(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val slug: String,
    private val provider: AnimeProvider,
    private val onPrevEpisode: () -> Unit,
    private val onNextEpisode: () -> Unit
) {
    private val appContext = context.applicationContext

    @Volatile
    private var animeTitle: String = ""

    @Volatile
    private var episodeNumber: Int = 1

    @Volatile
    private var coverUrl: String = ""

    @Volatile
    private var hasPrevEpisode: Boolean = false

    @Volatile
    private var hasNextEpisode: Boolean = false

    private val episodeForwardingPlayer = object : ForwardingPlayer(exoPlayer) {
        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            if (hasNextEpisode) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT)
                builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (hasPrevEpisode) {
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> hasNextEpisode
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> hasPrevEpisode
            else -> super.isCommandAvailable(command)
        }

        override fun seekToNext() {
            if (hasNextEpisode) onNextEpisode()
        }

        override fun seekToNextMediaItem() {
            if (hasNextEpisode) onNextEpisode()
        }

        override fun seekToPrevious() {
            if (hasPrevEpisode) onPrevEpisode()
        }

        override fun seekToPreviousMediaItem() {
            if (hasPrevEpisode) onPrevEpisode()
        }

        override fun getMediaMetadata(): MediaMetadata = buildMetadata()
    }

    private val mediaSession: MediaSession? = runCatching {
        ensureNotificationChannel()
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            appContext,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        MediaSession.Builder(appContext, episodeForwardingPlayer)
            .setId("kitsune_${UUID.randomUUID()}")
            .setSessionActivity(sessionActivity)
            .build()
    }.getOrNull()

    fun updateState(
        title: String,
        episode: Int,
        cover: String,
        hasPrev: Boolean,
        hasNext: Boolean,
        isPlaying: Boolean
    ) {
        animeTitle = title
        episodeNumber = episode
        coverUrl = cover
        hasPrevEpisode = hasPrev
        hasNextEpisode = hasNext

        val currentItem = exoPlayer.currentMediaItem
        if (currentItem != null) {
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(buildMetadata())
                .build()
            if (currentItem.mediaMetadata.title != updatedItem.mediaMetadata.title ||
                currentItem.mediaMetadata.subtitle != updatedItem.mediaMetadata.subtitle
            ) {
                exoPlayer.replaceMediaItem(
                    exoPlayer.currentMediaItemIndex,
                    updatedItem
                )
            }
        }

        postNotification(isPlaying)
    }

    private fun buildMetadata(): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(animeTitle.ifBlank { slug })
            .setSubtitle("Episodio $episodeNumber · ${provider.displayName}")
            .setArtist("${provider.displayName} · Episodio $episodeNumber")
            .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            runCatching { Uri.parse(coverUrl) }.getOrNull()?.let { uri ->
                builder.setArtworkUri(uri)
            }
        }
        return builder.build()
    }

    private fun postNotification(isPlaying: Boolean) {
        val session = mediaSession ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitsune)
            .setContentTitle(animeTitle.ifBlank { slug })
            .setContentText("Episodio $episodeNumber · ${provider.displayName}")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.notification_channel_playback_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = appContext.getString(R.string.notification_channel_playback_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun release() {
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
        runCatching {
            mediaSession?.release()
        }
    }

    companion object {
        private const val CHANNEL_ID = "kitsune_media_playback"
        private const val NOTIFICATION_ID = 4102
    }
}
