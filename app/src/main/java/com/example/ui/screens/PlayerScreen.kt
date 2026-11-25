package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.JkAnimeHttpClient
import com.example.model.AnimeProvider
import com.example.ui.components.ErrorStateView
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ServerPlaybackSupport
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.Locale

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    okHttpClient: OkHttpClient,
    onBack: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    var isFullscreen by remember { mutableStateOf(false) }

    // Handle orientation & immersive system bars on fullscreen toggle
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .let { if (!isFullscreen) it.statusBarsPadding().navigationBarsPadding() else it }
        ) {
            // Video Theater Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (isFullscreen) it.fillMaxSize()
                        else it.aspectRatio(16f / 9f)
                    }
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val resolved = state.resolvedVideo
                when {
                    state.isLoadingServers || state.isResolvingStream -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (state.isLoadingServers) {
                                    "Extrayendo servidores nativos…"
                                } else {
                                    "Resolviendo stream directo…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }

                    state.errorMessage != null -> {
                        ErrorStateView(
                            message = state.errorMessage.orEmpty(),
                            onRetry = { viewModel.loadEpisode(state.episodeNumber) },
                            onUnlockCloudflare = state.blockedUrl?.let { blocked ->
                                {
                                    onUnlockCloudflare(blocked) {
                                        viewModel.loadEpisode(state.episodeNumber)
                                    }
                                }
                            }
                        )
                    }

                    resolved != null && !resolved.streamUrl.isNullOrBlank() -> {
                        NativeExoPlayerBox(
                            streamUrl = resolved.streamUrl,
                            isHls = resolved.isHls,
                            headers = resolved.headers,
                            okHttpClient = okHttpClient,
                            resumePositionMs = state.resumePositionMs,
                            isFullscreen = isFullscreen,
                            hasNextEpisode = state.hasNextEpisode,
                            nextEpisodeNumber = state.episodeNumber + 1,
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onBack = {
                                if (isFullscreen) isFullscreen = false else onBack()
                            },
                            onNextEpisode = viewModel::skipToNextEpisodeIfAvailable,
                            onPlaybackError = viewModel::onNativePlaybackFailed,
                            onSaveProgress = viewModel::saveWatchProgress
                        )
                    }
                }
            }

            // Below-player details, episode switcher, and native server list (hidden in fullscreen)
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title & Episode Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.animeTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Episodio ${state.episodeNumber}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${state.provider.displayName} · ${state.provider.shortLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Prev / Next Episode Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.loadEpisode(state.episodeNumber - 1) },
                            enabled = state.episodeNumber > 1,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("player_prev_episode")
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.player_prev_episode))
                        }

                        if (state.hasNextEpisode) {
                            OutlinedButton(
                                onClick = { viewModel.skipToNextEpisodeIfAvailable() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("player_next_episode")
                            ) {
                                Text(stringResource(R.string.player_next_episode))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Rounded.SkipNext, contentDescription = null)
                            }
                        }
                    }

                    // Active Native Playback Banner
                    val activeServer = state.servers.getOrNull(state.selectedServerIndex)
                    val resolvedVideo = state.resolvedVideo
                    if (activeServer != null && resolvedVideo != null && !resolvedVideo.streamUrl.isNullOrBlank()) {
                        Surface(
                            color = Color(0x2622C55E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0x444ADE80)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_playback_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${activeServer.name} · Nativo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Reproducción nativa en ExoPlayer · Doble toque para adelantar o retroceder 10s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Native Server Selector
                    if (state.servers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.servers_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (state.provider == AnimeProvider.LATANIME) {
                                        "Solo servidores nativos"
                                    } else {
                                        "Desu (Nativo)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(state.servers) { idx, server ->
                                    val key = server.decodedEmbedUrl.ifBlank { "${server.name}_${server.rawUrl}" }
                                    val selected = key == state.selectedServerUrl || idx == state.selectedServerIndex
                                    val support = state.serverSupportMap[key] ?: ServerPlaybackSupport.CHECKING
                                    val tagText = when (support) {
                                        ServerPlaybackSupport.CHECKING -> "Verificando"
                                        ServerPlaybackSupport.NATIVE_STREAM -> "Nativo"
                                        ServerPlaybackSupport.UNAVAILABLE -> "No disp."
                                    }
                                    val badgeColor = when (support) {
                                        ServerPlaybackSupport.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ServerPlaybackSupport.NATIVE_STREAM -> Color(0xFF22C55E)
                                        ServerPlaybackSupport.UNAVAILABLE -> MaterialTheme.colorScheme.error
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.selectServer(idx) },
                                        leadingIcon = {
                                            when (support) {
                                                ServerPlaybackSupport.CHECKING -> {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = if (selected) Color.White else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                ServerPlaybackSupport.NATIVE_STREAM,
                                                ServerPlaybackSupport.UNAVAILABLE -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SmartDisplay,
                                                        contentDescription = "Nativo",
                                                        tint = if (selected) Color.White else Color(0xFF4ADE80),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = server.name,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Surface(
                                                    color = if (selected) {
                                                        Color.White.copy(alpha = 0.22f)
                                                    } else {
                                                        badgeColor.copy(alpha = 0.18f)
                                                    },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = tagText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (selected) Color.White else badgeColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("server_chip_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativeExoPlayerBox(
    streamUrl: String,
    isHls: Boolean,
    headers: Map<String, String>,
    okHttpClient: OkHttpClient,
    resumePositionMs: Long,
    isFullscreen: Boolean,
    hasNextEpisode: Boolean,
    nextEpisodeNumber: Int,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onSaveProgress: (Long, Long) -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(streamUrl, isHls) {
        val safeHeaders = headers.filterKeys { k ->
            k.equals("User-Agent", true) ||
                k.equals("Referer", true) ||
                k.equals("Origin", true) ||
                k.equals("Cookie", true)
        }
        val userAgent = safeHeaders["User-Agent"] ?: JkAnimeHttpClient.USER_AGENT
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(safeHeaders)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(4))
        val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
        if (isHls ||
            streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("master.txt", ignoreCase = true)
        ) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                if (resumePositionMs > 3_000L) {
                    seekTo(resumePositionMs)
                }
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember(streamUrl) { mutableStateOf(false) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var retriedTransient by remember(streamUrl) { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
    var nextCountdownSec by remember(streamUrl) { mutableIntStateOf(5) }
    var autoNextCancelled by remember(streamUrl) { mutableStateOf(false) }

    val inEndingRange = duration > 180_000L && currentPos >= duration - 120_000L

    fun performSkipOpEd() {
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val total = exoPlayer.duration.coerceAtLeast(0L)
        val isEndingWindow = total > 180_000L && pos >= total - 120_000L
        if (isEndingWindow && hasNextEpisode && pos + 85_000L >= total - 6_000L) {
            onNextEpisode()
            return
        }
        val target = pos + 85_000L
        val clamped = if (total > 1_500L) target.coerceAtMost(total - 1_500L) else target
        exoPlayer.seekTo(clamped)
        currentPos = clamped
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val causes = generateSequence<Throwable>(error) { it.cause }.toList()
                val httpCode = causes
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                val hasUnknownHost = causes.any { it is java.net.UnknownHostException }
                val isTransientHttp = httpCode != null && httpCode in listOf(502, 503, 504)
                val isTransientNetwork = !hasUnknownHost && (
                    isTransientHttp ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    )
                if (isTransientNetwork && !retriedTransient) {
                    retriedTransient = true
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }
                val causeChain = causes.joinToString(" | ") {
                    "${it.javaClass.simpleName}: ${it.message.orEmpty()}"
                }
                val detail = "${error.errorCodeName} ${httpCode ?: ""} $causeChain"
                onPlaybackError(detail)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSaveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic UI position & watch history sync
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            tick++
            if (tick % 5 == 0 && duration > 0L) {
                onSaveProgress(currentPos, duration)
            }
            delay(1_000L)
        }
    }

    // Auto-hide ExoPlayer controls (and Skip Opening/Ending inside it) after 3.5s
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    // Auto-clear double-tap feedback badge
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(700L)
            doubleTapFeedback = null
        }
    }

    // Auto-next episode countdown when video ends
    LaunchedEffect(isEnded, hasNextEpisode, autoNextCancelled) {
        if (isEnded && hasNextEpisode && !autoNextCancelled) {
            for (sec in 5 downTo 1) {
                nextCountdownSec = sec
                delay(1_000L)
            }
            onNextEpisode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(exoPlayer, duration) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val isLeftHalf = offset.x < size.width / 2f
                        if (isLeftHalf) {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPos = target
                            doubleTapFeedback = "-10s"
                        } else {
                            val rawTarget = exoPlayer.currentPosition + 10_000L
                            val target = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
                            exoPlayer.seekTo(target)
                            currentPos = target
                            doubleTapFeedback = "+10s"
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Double-tap seek feedback badge
        AnimatedVisibility(
            visible = doubleTapFeedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(
                    if (doubleTapFeedback == "-10s") Alignment.CenterStart else Alignment.CenterEnd
                )
                .padding(horizontal = 36.dp)
        ) {
            Surface(
                color = Color(0xCC0B0D14),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (doubleTapFeedback == "-10s") {
                            Icons.Rounded.Replay10
                        } else {
                            Icons.Rounded.Forward10
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = doubleTapFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Auto-next episode countdown banner
        AnimatedVisibility(
            visible = isEnded && hasNextEpisode && !autoNextCancelled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Surface(
                color = Color(0xEA121622),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Siguiente episodio ($nextEpisodeNumber) en ${nextCountdownSec}s",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { autoNextCancelled = true }
                        ) {
                            Text("Cancelar", color = Color.White.copy(alpha = 0.8f))
                        }
                        Button(
                            onClick = onNextEpisode,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Reproducir ahora")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x78000000))
                    .padding(12.dp)
            ) {
                // Top Bar inside ExoPlayer controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Skip Opening / Ending integrated with ExoPlayer controls
                        Surface(
                            color = Color(0xCC14141C),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { performSkipOpEd() }
                                .testTag("exo_skip_op_ed_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FastForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (inEndingRange) {
                                        stringResource(R.string.player_skip_ending)
                                    } else {
                                        stringResource(R.string.player_skip_opening)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Video Resize / Aspect Mode Cycle
                        Surface(
                            color = Color(0x99000000),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                                .testTag("exo_resize_mode")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = "Cambiar proporción de pantalla",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .size(18.dp)
                            )
                        }

                        Box {
                            Surface(
                                color = Color(0x99000000),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { speedMenuExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = "Velocidad",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false }
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            playbackSpeed = speed
                                            exoPlayer.setPlaybackSpeed(speed)
                                            speedMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Retroceder 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("exo_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Adelantar 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Scrub Bar & Fullscreen
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(currentPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    Slider(
                        value = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            if (duration > 0L) {
                                val target = (fraction * duration).toLong()
                                currentPos = target
                                exoPlayer.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("exo_fullscreen_toggle")
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.cd_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
