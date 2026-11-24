package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.JkAnimeHttpClient
import com.example.data.VideoStreamResolver
import com.example.ui.components.ErrorStateView
import com.example.ui.viewmodel.PlayerMode
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ServerPlaybackSupport
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
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
                                text = if (state.isLoadingServers) "Extrayendo servidores…" else "Resolviendo stream…",
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

                    resolved != null &&
                        state.playerMode == PlayerMode.NATIVE_EXO &&
                        !resolved.streamUrl.isNullOrBlank() -> {
                        NativeExoPlayerBox(
                            streamUrl = resolved.streamUrl,
                            headers = resolved.headers,
                            okHttpClient = okHttpClient,
                            resumePositionMs = state.resumePositionMs,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onBack = {
                                if (isFullscreen) isFullscreen = false else onBack()
                            },
                            onPlaybackError = viewModel::onNativePlaybackFailed,
                            onSaveProgress = viewModel::saveWatchProgress
                        )
                    }

                    resolved != null -> {
                        InAppWebPlayerBox(
                            embedUrl = resolved.embedUrl,
                            headers = resolved.headers,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onBack = {
                                if (isFullscreen) isFullscreen = false else onBack()
                            },
                            onFirstLoad = { viewModel.saveWatchProgress(0L, 0L) }
                        )
                    }
                }
            }

            // Below-player details, episode switcher, and server list (hidden in fullscreen)
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

                        OutlinedButton(
                            onClick = { viewModel.loadEpisode(state.episodeNumber + 1) },
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

                    // Active Playback Mode Banner + Mode Toggle
                    val hasDirect = !state.resolvedVideo?.streamUrl.isNullOrBlank()
                    val activeServer = state.servers.getOrNull(state.selectedServerIndex)
                    if (activeServer != null && state.resolvedVideo != null) {
                        val isNativeActive = state.playerMode == PlayerMode.NATIVE_EXO && hasDirect
                        Surface(
                            color = if (isNativeActive) {
                                Color(0x2622C55E)
                            } else {
                                Color(0x26F59E0B)
                            },
                            shape = RoundedCornerShape(12.dp),
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
                                    imageVector = if (isNativeActive) Icons.Rounded.SmartDisplay else Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = if (isNativeActive) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isNativeActive) {
                                            "${activeServer.name} · Reproductor Nativo"
                                        } else {
                                            "${activeServer.name} · Navegador Integrado"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isNativeActive) {
                                            "Stream directo en ExoPlayer (sin anuncios web)"
                                        } else {
                                            "Este servidor requiere cargarse en el navegador interno"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Server Selector with clear Nativo vs Navegador indicator per server
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
                                    text = "Nativo vs Navegador",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(state.servers) { idx, server ->
                                    val selected = idx == state.selectedServerIndex
                                    val support = state.serverSupportMap[idx] ?: ServerPlaybackSupport.CHECKING
                                    val tagText = when (support) {
                                        ServerPlaybackSupport.NATIVE_STREAM -> "Nativo"
                                        ServerPlaybackSupport.WEB_EMBED -> "Navegador"
                                        ServerPlaybackSupport.CHECKING -> "…"
                                    }
                                    val badgeColor = when (support) {
                                        ServerPlaybackSupport.NATIVE_STREAM -> Color(0xFF22C55E)
                                        ServerPlaybackSupport.WEB_EMBED -> Color(0xFFF59E0B)
                                        ServerPlaybackSupport.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
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
                                                ServerPlaybackSupport.NATIVE_STREAM -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SmartDisplay,
                                                        contentDescription = "Nativo",
                                                        tint = if (selected) Color.White else Color(0xFF4ADE80),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                ServerPlaybackSupport.WEB_EMBED -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Language,
                                                        contentDescription = "Navegador",
                                                        tint = if (selected) Color.White else Color(0xFFFBBF24),
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
                                                    color = if (selected) Color.White.copy(alpha = 0.22f) else badgeColor.copy(alpha = 0.18f),
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

                    // Mode Toggle (Native ExoPlayer vs In-App Web)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Modo de reproducción",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = state.playerMode == PlayerMode.NATIVE_EXO,
                                onClick = {
                                    if (hasDirect) viewModel.setPlayerMode(PlayerMode.NATIVE_EXO)
                                },
                                enabled = hasDirect,
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.SmartDisplay,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.player_native_mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("mode_native_chip")
                            )

                            FilterChip(
                                selected = state.playerMode == PlayerMode.IN_APP_WEB,
                                onClick = { viewModel.setPlayerMode(PlayerMode.IN_APP_WEB) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.player_web_mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.testTag("mode_web_chip")
                            )
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
    headers: Map<String, String>,
    okHttpClient: OkHttpClient,
    resumePositionMs: Long,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onPlaybackError: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(streamUrl) {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(JkAnimeHttpClient.USER_AGENT)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                if (resumePositionMs > 3_000L) {
                    seekTo(resumePositionMs)
                }
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
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

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
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
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
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
                // Top Bar
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppWebPlayerBox(
    embedUrl: String,
    headers: Map<String, String>,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onFirstLoad: () -> Unit
) {
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var rendererCrashed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(embedUrl) {
        rendererCrashed = false
        onFirstLoad()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.webViewClient = WebViewClient()
                    wv.stopLoading()
                    wv.destroy()
                } catch (_: Throwable) {
                }
            }
            webViewRef = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rendererCrashed) {
            ErrorStateView(
                message = "El reproductor web se detuvo inesperadamente. Puedes reintentar o elegir otro servidor.",
                onRetry = { rendererCrashed = false }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = JkAnimeHttpClient.USER_AGENT
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                customView = view
                                customViewCallback = callback
                                if (!isFullscreen) onToggleFullscreen()
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                webViewRef = null
                                rendererCrashed = true
                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString().orEmpty()
                                if (VideoStreamResolver.isAdOrTrackerUrl(url)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (VideoStreamResolver.isAdOrTrackerUrl(url)) return true
                                return false
                            }
                        }
                        loadUrl(embedUrl, headers)
                    }
                },
                update = { webView ->
                    if (webView.url != embedUrl) {
                        webView.loadUrl(embedUrl, headers)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom fullscreen container if HTML5 video enters fullscreen
        customView?.let { fsView ->
            AndroidView(
                factory = { fsView },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Overlay Back & Fullscreen controls in corner
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isFullscreen) {
                Surface(
                    color = Color(0x99000000),
                    shape = CircleShape
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                }
            }
            Surface(
                color = Color(0x99000000),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.testTag("web_fullscreen_toggle")
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
