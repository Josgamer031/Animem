package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.model.DeviceMode
import com.example.model.EpisodeItem
import com.example.player.EpisodeMediaSessionManager
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LocalPipController
import com.example.ui.components.TvRemoteShortcutsDialog
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ServerPlaybackSupport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    okHttpClient: OkHttpClient,
    isTvMode: Boolean = false,
    onSwitchDeviceMode: (DeviceMode) -> Unit = {},
    onBack: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val activity = remember(context) { context.findActivity() }
    val pipController = LocalPipController.current
    val isInPipMode by (pipController?.isInPictureInPictureMode ?: remember { MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    var isFullscreen by remember { mutableStateOf(true) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showServersSheet by remember { mutableStateOf(false) }
    var showTvShortcutsDialog by remember { mutableStateOf(false) }

    if (showTvShortcutsDialog && !isInPipMode) {
        TvRemoteShortcutsDialog(
            currentMode = if (isTvMode) DeviceMode.SMART_TV else DeviceMode.PHONE,
            onSwitchMode = onSwitchDeviceMode,
            onDismiss = { showTvShortcutsDialog = false }
        )
    }

    if (showEpisodesSheet && !isInPipMode) {
        PlayerEpisodesBottomSheet(
            animeTitle = state.animeTitle,
            currentEpisodeNumber = state.episodeNumber,
            episodes = state.episodes,
            isLoading = state.isLoadingEpisodesList,
            onSelectEpisode = { ep ->
                showEpisodesSheet = false
                if (ep.number != state.episodeNumber) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.loadEpisode(ep.number, ep.url)
                }
            },
            onDismiss = { showEpisodesSheet = false }
        )
    }

    if (showServersSheet && !isInPipMode) {
        PlayerServersBottomSheet(
            servers = state.servers,
            selectedServerUrl = state.selectedServerUrl,
            selectedServerIndex = state.selectedServerIndex,
            serverSupportMap = state.serverSupportMap,
            provider = state.provider,
            onSelectServer = { idx ->
                showServersSheet = false
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectServer(idx)
            },
            onDismiss = { showServersSheet = false }
        )
    }

    // Handle orientation & immersive system bars on fullscreen toggle
    DisposableEffect(isFullscreen, isInPipMode) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isInPipMode) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else if (isFullscreen) {
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .let { if (!isFullscreen && !isInPipMode) it.statusBarsPadding().navigationBarsPadding() else it }
        ) {
            // Video Theater Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (isFullscreen || isInPipMode) it.fillMaxSize()
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
                            slug = state.slug,
                            animeTitle = state.animeTitle,
                            coverUrl = state.coverUrl,
                            provider = state.provider,
                            episodeNumber = state.episodeNumber,
                            resumePositionMs = state.resumePositionMs,
                            isFullscreen = isFullscreen,
                            isInPipMode = isInPipMode,
                            isTvMode = isTvMode,
                            hasPrevEpisode = state.episodeNumber > 1,
                            hasNextEpisode = state.hasNextEpisode,
                            nextEpisodeNumber = state.episodeNumber + 1,
                            autoPlayNext = userPreferences.autoPlayNext,
                            autoSkipOpening = userPreferences.autoSkipOpening,
                            hasEpisodesList = state.episodes.isNotEmpty() || state.isLoadingEpisodesList,
                            hasServers = state.servers.isNotEmpty(),
                            onOpenEpisodes = { showEpisodesSheet = true },
                            onOpenServers = { showServersSheet = true },
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onShowTvShortcuts = { showTvShortcutsDialog = true },
                            onBack = onBack,
                            onPrevEpisode = {
                                if (state.episodeNumber > 1) {
                                    val prevNum = state.episodeNumber - 1
                                    val prevUrl = state.episodes.firstOrNull { it.number == prevNum }?.url.orEmpty()
                                    viewModel.loadEpisode(prevNum, prevUrl)
                                }
                            },
                            onNextEpisode = viewModel::skipToNextEpisodeIfAvailable,
                            onPlaybackError = viewModel::onNativePlaybackFailed,
                            onSaveProgress = viewModel::saveWatchProgress
                        )
                    }
                }

                // Top bar overlay in fullscreen while loading or on error so user can always go back or switch servers
                if ((isFullscreen && !isInPipMode) &&
                    (state.isLoadingServers || state.isResolvingStream || state.errorMessage != null)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_fullscreen_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.animeTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Episodio ${state.episodeNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (state.servers.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showServersSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("player_fullscreen_servers_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Servidores",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Below-player details, episode switcher, and native server list (hidden in fullscreen & PiP)
            if (!isFullscreen && !isInPipMode) {
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

                    // Prev / Episodes / Next Episode Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val prevNum = state.episodeNumber - 1
                                val prevUrl = state.episodes.firstOrNull { it.number == prevNum }?.url.orEmpty()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.loadEpisode(prevNum, prevUrl)
                            },
                            enabled = state.episodeNumber > 1,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("player_prev_episode")
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.player_prev_episode),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (state.episodes.isNotEmpty() || state.isLoadingEpisodesList) {
                            OutlinedButton(
                                onClick = { showEpisodesSheet = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("player_episodes_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.episodes.isNotEmpty()) {
                                        "Eps (${state.episodes.size})"
                                    } else {
                                        stringResource(R.string.episodes_title)
                                    },
                                    maxLines = 1
                                )
                            }
                        }

                        if (state.hasNextEpisode) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.skipToNextEpisodeIfAvailable()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("player_next_episode")
                            ) {
                                Text(
                                    text = stringResource(R.string.player_next_episode),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
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
                                        text = if (isTvMode) {
                                            "Reproducción nativa · Control TV: Rojo/S omitir, ◀/▶ ±10s, Menú/M atajos"
                                        } else {
                                            "Reproducción nativa en ExoPlayer · Doble toque para adelantar o retroceder 10s"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isTvMode) {
                                    OutlinedButton(
                                        onClick = { showTvShortcutsDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .tvFocusHighlight(shape = RoundedCornerShape(10.dp))
                                            .testTag("player_tv_shortcuts_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SettingsRemote,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Atajos TV",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
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
    slug: String,
    animeTitle: String,
    coverUrl: String,
    provider: AnimeProvider,
    episodeNumber: Int,
    resumePositionMs: Long,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    isTvMode: Boolean,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    nextEpisodeNumber: Int,
    autoPlayNext: Boolean,
    autoSkipOpening: Boolean,
    hasEpisodesList: Boolean,
    hasServers: Boolean,
    onOpenEpisodes: () -> Unit,
    onOpenServers: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowTvShortcuts: () -> Unit,
    onBack: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onSaveProgress: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val pipController = LocalPipController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPrev by rememberUpdatedState(onPrevEpisode)
    val currentOnNext by rememberUpdatedState(onNextEpisode)
    val currentOnSaveProgress by rememberUpdatedState(onSaveProgress)

    LaunchedEffect(isTvMode) {
        if (isTvMode) {
            focusRequester.requestFocus()
        }
    }

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
    var isControlsLocked by remember { mutableStateOf(false) }
    var lockedHintVisible by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var retriedTransient by remember(streamUrl) { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
    var doubleTapSeq by remember { mutableIntStateOf(0) }
    var scrubTargetFraction by remember { mutableStateOf<Float?>(null) }
    val scrubInteractionSource = remember { MutableInteractionSource() }
    val isScrubDragged by scrubInteractionSource.collectIsDraggedAsState()
    val isScrubbing = isScrubDragged || scrubTargetFraction != null
    var nextCountdownSec by remember(streamUrl) { mutableIntStateOf(5) }
    var autoNextCancelled by remember(streamUrl) { mutableStateOf(false) }
    var autoSkippedOpening by remember(streamUrl) { mutableStateOf(false) }
    var autoSkipBadgeVisible by remember(streamUrl) { mutableStateOf(false) }

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

    // Picture-in-Picture eligibility & background lifecycle pause
    LaunchedEffect(pipController, isPlaying) {
        pipController?.setPlayerPipEligibility(canEnter = true, isPlaying = isPlaying)
    }

    DisposableEffect(pipController) {
        onDispose {
            pipController?.setPlayerPipEligibility(canEnter = false, isPlaying = false)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer, pipController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val nowInPip = pipController?.isInPictureInPictureMode?.value == true
                if (!nowInPip && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
                currentOnSaveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Media3 MediaSession + system notification playback controls
    val mediaSessionManager = remember(exoPlayer, slug, provider) {
        EpisodeMediaSessionManager(
            context = context,
            exoPlayer = exoPlayer,
            slug = slug,
            provider = provider,
            onPrevEpisode = { currentOnPrev() },
            onNextEpisode = { currentOnNext() }
        )
    }

    DisposableEffect(mediaSessionManager) {
        onDispose {
            mediaSessionManager.release()
        }
    }

    LaunchedEffect(
        mediaSessionManager,
        animeTitle,
        episodeNumber,
        coverUrl,
        hasPrevEpisode,
        hasNextEpisode,
        isPlaying
    ) {
        mediaSessionManager.updateState(
            title = animeTitle,
            episode = episodeNumber,
            cover = coverUrl,
            hasPrev = hasPrevEpisode,
            hasNext = hasNextEpisode,
            isPlaying = isPlaying
        )
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

    // Auto-skip opening once per episode when user preference is enabled
    LaunchedEffect(autoSkipOpening, currentPos, duration, isPlaying, autoSkippedOpening) {
        if (autoSkipOpening &&
            !autoSkippedOpening &&
            isPlaying &&
            duration > 120_000L &&
            currentPos in 5_000L..88_000L
        ) {
            autoSkippedOpening = true
            performSkipOpEd()
            autoSkipBadgeVisible = true
        }
    }

    LaunchedEffect(autoSkipBadgeVisible) {
        if (autoSkipBadgeVisible) {
            delay(2_200L)
            autoSkipBadgeVisible = false
        }
    }

    // Auto-hide ExoPlayer controls (and Skip Opening/Ending inside it) after 3.5s
    LaunchedEffect(controlsVisible, isPlaying, isControlsLocked, isScrubbing) {
        if (isControlsLocked) {
            controlsVisible = false
        } else if (controlsVisible && isPlaying && !isScrubbing) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    // Auto-hide lock chip hint
    LaunchedEffect(lockedHintVisible, isControlsLocked) {
        if (isControlsLocked && lockedHintVisible) {
            delay(2_500L)
            lockedHintVisible = false
        }
    }

    // Auto-clear double-tap feedback badge
    LaunchedEffect(doubleTapFeedback, doubleTapSeq) {
        if (doubleTapFeedback != null) {
            delay(760L)
            doubleTapFeedback = null
        }
    }

    // Auto-next episode countdown when video ends
    LaunchedEffect(isEnded, hasNextEpisode, autoPlayNext, autoNextCancelled) {
        if (isEnded && hasNextEpisode && autoPlayNext && !autoNextCancelled) {
            for (sec in 5 downTo 1) {
                nextCountdownSec = sec
                delay(1_000L)
            }
            onNextEpisode()
        }
    }

    BackHandler(enabled = isControlsLocked && !isInPipMode) {
        lockedHintVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = keyEvent.nativeKeyEvent.keyCode
                if (code == AndroidKeyEvent.KEYCODE_INFO ||
                    code == AndroidKeyEvent.KEYCODE_MENU ||
                    code == AndroidKeyEvent.KEYCODE_M
                ) {
                    onShowTvShortcuts()
                    return@onPreviewKeyEvent true
                }
                if (isControlsLocked) return@onPreviewKeyEvent false

                when (code) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_SPACE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        controlsVisible = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        exoPlayer.play()
                        controlsVisible = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        exoPlayer.pause()
                        controlsVisible = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER -> {
                        if (!controlsVisible) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                        currentPos = target
                        doubleTapFeedback = "-10s"
                        doubleTapSeq++
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!controlsVisible) {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPos = target
                            doubleTapFeedback = "-10s"
                            doubleTapSeq++
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        val rawTarget = exoPlayer.currentPosition + 10_000L
                        val target = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
                        exoPlayer.seekTo(target)
                        currentPos = target
                        doubleTapFeedback = "+10s"
                        doubleTapSeq++
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!controlsVisible) {
                            val rawTarget = exoPlayer.currentPosition + 10_000L
                            val target = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
                            exoPlayer.seekTo(target)
                            currentPos = target
                            doubleTapFeedback = "+10s"
                            doubleTapSeq++
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (!controlsVisible) {
                            if (hasEpisodesList) onOpenEpisodes() else controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_PROG_RED,
                    AndroidKeyEvent.KEYCODE_S -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        performSkipOpEd()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (hasNextEpisode) {
                            onNextEpisode()
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (hasPrevEpisode) {
                            onPrevEpisode()
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_F,
                    AndroidKeyEvent.KEYCODE_PROG_BLUE -> {
                        onToggleFullscreen()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(exoPlayer, duration, isControlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (isControlsLocked) {
                            lockedHintVisible = !lockedHintVisible
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isControlsLocked) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        doubleTapSeq++
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(
                    R.layout.kitsune_exo_player_view,
                    null,
                    false
                ) as PlayerView).apply {
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

        // Double-tap seek feedback with expanding ripple wave
        val rippleScale = remember { Animatable(0.45f) }
        val rippleAlpha = remember { Animatable(0f) }
        LaunchedEffect(doubleTapSeq, doubleTapFeedback) {
            if (doubleTapFeedback != null && doubleTapSeq > 0) {
                rippleScale.snapTo(0.42f)
                rippleAlpha.snapTo(0.65f)
                launch {
                    rippleScale.animateTo(
                        targetValue = 1.55f,
                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                    )
                }
                rippleAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                )
            }
        }

        AnimatedVisibility(
            visible = doubleTapFeedback != null && !isInPipMode,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.82f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            val isRewind = doubleTapFeedback == "-10s"
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.fillMaxSize()) {
                // Soft side radial/horizontal aura
                Box(
                    modifier = Modifier
                        .align(if (isRewind) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxSize(0.45f)
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isRewind) {
                                    listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent)
                                } else {
                                    listOf(Color.Transparent, primaryColor.copy(alpha = 0.22f))
                                }
                            )
                        )
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(if (isRewind) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 42.dp)
                        .size(118.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = size.minDimension * 0.42f
                        drawCircle(
                            color = primaryColor.copy(alpha = rippleAlpha.value * 0.32f),
                            radius = baseRadius * rippleScale.value
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = rippleAlpha.value * 0.65f),
                            radius = baseRadius * rippleScale.value,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                    Surface(
                        color = Color(0xD80A0D16),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.85f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isRewind) {
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
            }
        }

        // Auto-skip opening feedback badge
        AnimatedVisibility(
            visible = autoSkipBadgeVisible && !isInPipMode,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                color = Color(0xD8121622),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Opening omitido (+85s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Lock controls unlock chip
        AnimatedVisibility(
            visible = isControlsLocked && lockedHintVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
        ) {
            Surface(
                color = Color(0xDC121622),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isControlsLocked = false
                        lockedHintVisible = false
                        controlsVisible = true
                    }
                    .testTag("exo_unlock_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_controls_locked),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // End of episode banner (auto-next countdown OR manual choice)
        AnimatedVisibility(
            visible = isEnded && hasNextEpisode && !autoNextCancelled && !isInPipMode,
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
                        text = if (autoPlayNext) {
                            "Siguiente episodio ($nextEpisodeNumber) en ${nextCountdownSec}s"
                        } else {
                            "Episodio finalizado · Siguiente: Ep. $nextEpisodeNumber"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (autoPlayNext) {
                            TextButton(
                                onClick = { autoNextCancelled = true }
                            ) {
                                Text("Cancelar", color = Color.White.copy(alpha = 0.8f))
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Repetir", color = Color.White)
                            }
                        }
                        Button(
                            onClick = onNextEpisode,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (autoPlayNext) "Reproducir ahora" else "Siguiente")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !isControlsLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xD805070E),
                            0.28f to Color(0x3605070E),
                            0.68f to Color(0x3605070E),
                            1.0f to Color(0xE205070E)
                        )
                    )
                    .let { if (isFullscreen) it.displayCutoutPadding() else it }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (isFullscreen) Modifier.weight(1f) else Modifier
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("exo_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = Color.White
                            )
                        }
                        if (isFullscreen) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = animeTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Episodio $episodeNumber · ${provider.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        modifier = Modifier
                            .let { if (isFullscreen) it else it.weight(1f) }
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // Skip Opening / Ending integrated with ExoPlayer controls
                        Surface(
                            color = Color(0xCC14141C),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    performSkipOpEd()
                                }
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

                        if (hasServers) {
                            Surface(
                                color = Color(0x99000000),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        controlsVisible = false
                                        onOpenServers()
                                    }
                                    .testTag("exo_servers_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = stringResource(R.string.servers_title),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .size(18.dp)
                                )
                            }
                        }

                        if (hasEpisodesList) {
                            Surface(
                                color = Color(0x99000000),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        controlsVisible = false
                                        onOpenEpisodes()
                                    }
                                    .testTag("exo_episodes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = stringResource(R.string.player_episodes_sheet_title),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .size(18.dp)
                                )
                            }
                        }

                        // Picture-in-Picture button
                        if (pipController?.isPipSupported == true) {
                            Surface(
                                color = Color(0x99000000),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        controlsVisible = false
                                        pipController.enterPictureInPicture()
                                    }
                                    .testTag("exo_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.player_pip),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .size(18.dp)
                                )
                            }
                        }

                        // Lock controls button
                        Surface(
                            color = Color(0x99000000),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isControlsLocked = true
                                    controlsVisible = false
                                    lockedHintVisible = true
                                }
                                .testTag("exo_lock_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = "Bloquear controles",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .size(18.dp)
                            )
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
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 20.dp else 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullscreen && hasPrevEpisode) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevEpisode()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .testTag("exo_prev_episode")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.player_prev_episode),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

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

                    if (isFullscreen && hasNextEpisode) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNextEpisode()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .testTag("exo_next_episode")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.player_next_episode),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Bottom Scrub Bar, Fullscreen & TV Shortcut Hint
                val displayFraction = scrubTargetFraction ?: if (duration > 0L) {
                    (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val displayedTimeMs = if (duration > 0L && scrubTargetFraction != null) {
                    (scrubTargetFraction!! * duration).toLong()
                } else {
                    currentPos
                }
                val trackHeight by animateDpAsState(
                    targetValue = if (isScrubbing) 7.dp else 3.5.dp,
                    animationSpec = tween(160),
                    label = "scrub_track_height"
                )
                val thumbSize by animateDpAsState(
                    targetValue = if (isScrubbing) 18.dp else 11.dp,
                    animationSpec = tween(160),
                    label = "scrub_thumb_size"
                )
                val trackGlowAlpha by animateFloatAsState(
                    targetValue = if (isScrubbing) 0.45f else 0f,
                    animationSpec = tween(160),
                    label = "scrub_glow_alpha"
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    AnimatedVisibility(
                        visible = isScrubbing && duration > 0L,
                        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 4.dp)
                    ) {
                        Surface(
                            color = Color(0xEA0B0F1A),
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        ) {
                            Text(
                                text = "${formatDuration(displayedTimeMs)} / ${formatDuration(duration)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (isTvMode) {
                        Text(
                            text = "Control TV: OK Pausa · ◀/▶ ±10s · ▲ Episodios · Rojo/S Omitir OP · CH± Ep · Azul/F Cine · Menú Atajos",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(displayedTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium,
                            color = if (isScrubbing) MaterialTheme.colorScheme.primary else Color.White
                        )
                        BoxWithConstraints(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            val totalWidth = maxWidth
                            // Subtle outer glow track while scrubbing
                            if (trackGlowAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(displayFraction.coerceIn(0f, 1f))
                                        .height(trackHeight + 6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = trackGlowAlpha))
                                )
                            }
                            // Inactive track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(trackHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (isScrubbing) 0.36f else 0.24f))
                            )
                            // Active progress track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(displayFraction.coerceIn(0f, 1f))
                                    .height(trackHeight)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            // Expanding thumb
                            val thumbOffsetX = ((totalWidth - thumbSize) * displayFraction.coerceIn(0f, 1f))
                                .coerceAtLeast(0.dp)
                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffsetX)
                                    .size(thumbSize)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            // Transparent M3 Slider preserves drag, tap, D-pad, and TalkBack semantics
                            Slider(
                                value = displayFraction,
                                onValueChange = { fraction ->
                                    if (duration > 0L) {
                                        scrubTargetFraction = fraction.coerceIn(0f, 1f)
                                        val target = (fraction * duration).toLong()
                                        currentPos = target
                                        exoPlayer.seekTo(target)
                                    }
                                },
                                onValueChangeFinished = {
                                    scrubTargetFraction = null
                                },
                                interactionSource = scrubInteractionSource,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Transparent,
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
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
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerEpisodesBottomSheet(
    animeTitle: String,
    currentEpisodeNumber: Int,
    episodes: List<EpisodeItem>,
    isLoading: Boolean,
    onSelectEpisode: (EpisodeItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_episodes_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            text = stringResource(R.string.detail_episodes_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(episodes, key = { it.number }) { ep ->
                        val isCurrent = ep.number == currentEpisodeNumber
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectEpisode(ep) }
                                .testTag("sheet_episode_${ep.number}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Episodio ${ep.number}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (ep.label.isNotBlank() && !ep.label.equals("Episodio ${ep.number}", true)) {
                                        Text(
                                            text = ep.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isCurrent) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Actual",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
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

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerServersBottomSheet(
    servers: List<com.example.model.VideoServer>,
    selectedServerUrl: String,
    selectedServerIndex: Int,
    serverSupportMap: Map<String, ServerPlaybackSupport>,
    provider: AnimeProvider,
    onSelectServer: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.servers_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (provider == AnimeProvider.LATANIME) {
                        "Solo servidores nativos"
                    } else {
                        "Desu (Nativo)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(servers) { idx, server ->
                    val key = server.decodedEmbedUrl.ifBlank { "${server.name}_${server.rawUrl}" }
                    val selected = key == selectedServerUrl || idx == selectedServerIndex
                    val support = serverSupportMap[key] ?: ServerPlaybackSupport.CHECKING
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectServer(idx) }
                            .testTag("sheet_server_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                when (support) {
                                    ServerPlaybackSupport.CHECKING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    ServerPlaybackSupport.NATIVE_STREAM,
                                    ServerPlaybackSupport.UNAVAILABLE -> {
                                        Icon(
                                            imageVector = Icons.Rounded.SmartDisplay,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF4ADE80),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = tagText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
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
