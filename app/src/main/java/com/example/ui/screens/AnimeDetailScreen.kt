package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.EpisodeItem
import com.example.model.WatchHistoryEntry
import com.example.model.WatchStatus
import com.example.ui.components.AnimeDetailLoadingSkeleton
import com.example.ui.components.CoverPalette
import com.example.ui.components.ErrorStateView
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.kitsuneSharedPoster
import com.example.ui.components.posterSharedKey
import com.example.ui.components.rememberCoverPalette
import com.example.ui.components.rememberPulseGlowFraction
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.AnimeDetailUiState
import com.example.ui.viewmodel.AnimeDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun AnimeDetailScreen(
    viewModel: AnimeDetailViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onNavigateToGenreExplore: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchedMap by viewModel.watchedEpisodes.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val favoriteStatus by viewModel.favoriteStatus.collectAsStateWithLifecycle()
    val episodeQuery by viewModel.episodeQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val successDetail = (uiState as? AnimeDetailUiState.Success)?.detail
    val coverPalette = rememberCoverPalette(
        imageUrl = successDetail?.coverUrl,
        fallbackAccent = MaterialTheme.colorScheme.primary
    )

    val topBarScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 150
        }
    }
    val topBarBgColor by animateColorAsState(
        targetValue = if (topBarScrolled) {
            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(240),
        label = "detail_topbar_bg"
    )
    val topBarButtonBg by animateColorAsState(
        targetValue = if (topBarScrolled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        } else {
            Color(0xAA0B0D14)
        },
        animationSpec = tween(240),
        label = "detail_topbar_btn_bg"
    )

    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favorite_scale"
    )
    val heartBurstScale = remember { Animatable(0.8f) }
    val heartBurstAlpha = remember { Animatable(0f) }
    var prevFav by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (isFavorite && !prevFav) {
            heartBurstScale.snapTo(0.8f)
            heartBurstAlpha.snapTo(0.85f)
            launch {
                heartBurstScale.animateTo(1.75f, tween(380))
            }
            launch {
                heartBurstAlpha.animateTo(0f, tween(380))
            }
        }
        prevFav = isFavorite
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is AnimeDetailUiState.Loading -> {
                    AnimeDetailLoadingSkeleton()
                }
                is AnimeDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorStateView(
                            message = state.message,
                            onRetry = viewModel::loadDetail,
                            onUnlockCloudflare = state.blockedUrl?.let { blocked ->
                                {
                                    onUnlockCloudflare(blocked) {
                                        viewModel.loadDetail()
                                    }
                                }
                            }
                        )
                    }
                }
                is AnimeDetailUiState.Success -> {
                    AnimeDetailContent(
                        detail = state.detail,
                        coverPalette = coverPalette,
                        listState = listState,
                        episodes = state.displayedEpisodes,
                        episodeQuery = episodeQuery,
                        selectedPage = state.selectedPage,
                        isLoadingEpisodes = state.isLoadingEpisodes,
                        isLoadingMoreEpisodes = state.isLoadingMoreEpisodes,
                        sortDescending = state.sortDescending,
                        isFavorite = isFavorite,
                        favoriteStatus = favoriteStatus,
                        watchedMap = watchedMap,
                        onEpisodeQueryChange = viewModel::updateEpisodeQuery,
                        onSelectPage = viewModel::selectEpisodePage,
                        onToggleSort = viewModel::toggleSortOrder,
                        onToggleFavorite = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite()
                        },
                        onSelectWatchStatus = { status ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateWatchStatus(status)
                        },
                        onGenreClick = { genre ->
                            viewModel.onGenreSelected(genre)
                            onNavigateToGenreExplore()
                        },
                        onRetryEpisodes = viewModel::loadDetail,
                        onPlayEpisode = { epNum ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val epUrl = state.detail.episodes.firstOrNull { it.number == epNum }?.url.orEmpty()
                            onPlayEpisode(
                                state.detail.slug,
                                epNum,
                                state.detail.title,
                                state.detail.coverUrl,
                                state.detail.provider.id,
                                epUrl
                            )
                        }
                    )
                }
            }

            // Translucent Sticky Top Bar: Back + Scrolled Title + Favorite + Share
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(topBarBgColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = topBarButtonBg,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = Color.White
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = topBarScrolled && successDetail != null,
                            enter = fadeIn(tween(200)) + slideInVertically { it / 3 },
                            exit = fadeOut(tween(160)) + slideOutVertically { it / 3 },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = successDetail?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (successDetail != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (heartBurstAlpha.value > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(heartBurstScale.value)
                                            .alpha(heartBurstAlpha.value)
                                            .border(2.dp, coverPalette.vibrant, CircleShape)
                                    )
                                }
                                Surface(
                                    color = topBarButtonBg,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleFavorite()
                                        },
                                        modifier = Modifier.testTag("detail_favorite_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = stringResource(R.string.cd_favorite),
                                            tint = if (isFavorite) coverPalette.vibrant else Color.White,
                                            modifier = Modifier.scale(favoriteScale)
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = topBarButtonBg,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val shareUrl = "${successDetail.provider.baseUrl}/${successDetail.slug}/"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Mira ${successDetail.title} en ${successDetail.provider.displayName}: $shareUrl"
                                            )
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Compartir anime")
                                        )
                                    },
                                    modifier = Modifier.testTag("detail_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = stringResource(R.string.cd_share),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = topBarScrolled) {
                    HorizontalDivider(
                        color = coverPalette.vibrant.copy(alpha = 0.28f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeDetailContent(
    detail: AnimeDetail,
    coverPalette: CoverPalette,
    listState: LazyListState,
    episodes: List<EpisodeItem>,
    episodeQuery: String,
    selectedPage: Int,
    isLoadingEpisodes: Boolean,
    isLoadingMoreEpisodes: Boolean,
    sortDescending: Boolean,
    isFavorite: Boolean,
    favoriteStatus: WatchStatus?,
    watchedMap: Map<Int, WatchHistoryEntry>,
    onEpisodeQueryChange: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onToggleSort: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectWatchStatus: (WatchStatus) -> Unit,
    onGenreClick: (String) -> Unit,
    onRetryEpisodes: () -> Unit,
    onPlayEpisode: (Int) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    val pulseFraction = rememberPulseGlowFraction()

    val headerScrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                900f
            }
        }
    }

    val latestWatched = watchedMap.values.maxByOrNull { it.updatedAtMillis }
    val primaryEpisode = latestWatched?.episodeNumber
        ?: episodes.firstOrNull()?.number
        ?: 1

    val filteredEpisodes = remember(episodes, episodeQuery) {
        if (episodeQuery.isBlank()) {
            episodes
        } else {
            episodes.filter { it.number.toString().contains(episodeQuery) }
        }
    }

    val isUpcoming = detail.status.contains("estren", ignoreCase = true) ||
        detail.status.contains("proxim", ignoreCase = true) ||
        detail.status.contains("upcoming", ignoreCase = true)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // Parallax Backdrop & Adaptive Palette Poster Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = headerScrollOffset * 0.45f
                            val scale = 1f + (headerScrollOffset / 1900f).coerceIn(0f, 0.14f)
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - (headerScrollOffset / 560f)).coerceIn(0.15f, 1f)
                        }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.38f)
                    )
                    if (detail.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = detail.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.42f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    coverPalette.ambientGlow,
                                    Color(0xB80B0D14),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .width(122.dp)
                            .aspectRatio(0.70f)
                            .kitsuneSharedPoster(posterSharedKey(detail.provider.id, detail.slug))
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        coverPalette.vibrant.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0.18f)
                                    )
                                ),
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        if (detail.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = detail.coverUrl,
                                contentDescription = stringResource(R.string.cd_poster, detail.title),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            StatusPill(
                                text = "${detail.provider.displayName} · ${detail.provider.shortLabel}",
                                containerColor = if (detail.provider == AnimeProvider.LATANIME) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                            if (detail.status.isNotBlank()) {
                                StatusPill(
                                    text = detail.status,
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (detail.type.isNotBlank()) {
                                StatusPill(
                                    text = detail.type,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }

                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (detail.alternativeTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = detail.alternativeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (detail.totalEpisodesText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Episodios: ${detail.totalEpisodesText}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Primary CTA Row + Mi Lista Button + Clickable Genres + Synopsis
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlayEpisode(primaryEpisode) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = coverPalette.vibrant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .border(
                                width = 1.5.dp,
                                color = coverPalette.vibrant.copy(alpha = pulseFraction),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("detail_primary_watch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (latestWatched != null) {
                                "Continuar Ep. ${latestWatched.episodeNumber}"
                            } else {
                                "Ver Episodio $primaryEpisode"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val myListContainer by animateColorAsState(
                        targetValue = if (isFavorite) {
                            coverPalette.vibrant.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(240),
                        label = "mylist_btn_container"
                    )
                    val myListBorder by animateColorAsState(
                        targetValue = if (isFavorite) coverPalette.vibrant else MaterialTheme.colorScheme.outline,
                        animationSpec = tween(240),
                        label = "mylist_btn_border"
                    )

                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, myListBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = myListContainer,
                            contentColor = if (isFavorite) {
                                coverPalette.vibrant
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("detail_mylist_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) {
                                stringResource(R.string.my_list_added)
                            } else {
                                stringResource(R.string.my_list_add)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Animated Tracking Status Selector when saved in Mi Lista
                AnimatedVisibility(
                    visible = isFavorite,
                    enter = fadeIn(tween(220)) + slideInVertically { -it / 4 },
                    exit = fadeOut(tween(180))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.watch_status_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(WatchStatus.entries) { status ->
                                val selected = favoriteStatus == status
                                val chipScale by animateFloatAsState(
                                    targetValue = if (selected) 1.04f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "watch_status_scale"
                                )
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectWatchStatus(status) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = status.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .scale(chipScale)
                                        .testTag("detail_watch_status_${status.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Clickable Genres -> navigates straight to filtered Explore
                if (detail.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.genres.forEach { genre ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onGenreClick(genre) }
                                    .testTag("detail_genre_${genre.lowercase()}")
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Synopsis
                Column(modifier = Modifier.animateContentSize()) {
                    Text(
                        text = stringResource(R.string.synopsis_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.synopsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detail.synopsis.length > 160) {
                        TextButton(
                            onClick = { synopsisExpanded = !synopsisExpanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (synopsisExpanded) "Ver menos" else "Leer más",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Episodes Header + Episode Number Search + Pagination Range Tabs
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.episodes_title)} (${episodes.size})",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (isLoadingMoreEpisodes) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            IconButton(
                                onClick = { isGridView = false },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (!isGridView) coverPalette.vibrant else Color.Transparent
                                    )
                                    .testTag("detail_episodes_list_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ViewAgenda,
                                    contentDescription = "Vista lista",
                                    tint = if (!isGridView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { isGridView = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isGridView) coverPalette.vibrant else Color.Transparent
                                    )
                                    .testTag("detail_episodes_grid_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = "Vista cuadrícula",
                                    tint = if (isGridView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onToggleSort,
                        modifier = Modifier.testTag("detail_sort_episodes")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (sortDescending) "Mayor a menor" else "Menor a mayor")
                    }
                }
            }

            if (detail.episodes.size > 6 || episodeQuery.isNotBlank()) {
                OutlinedTextField(
                    value = episodeQuery,
                    onValueChange = onEpisodeQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_episode_filter_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (episodeQuery.isNotBlank()) {
                            IconButton(onClick = { onEpisodeQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Limpiar filtro de episodio"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("detail_episode_filter_input")
                )
            }

            if (detail.pageRanges.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = 0) {
                        FilterChip(
                            selected = selectedPage == 0,
                            onClick = { onSelectPage(0) },
                            label = { Text("Todos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    items(detail.pageRanges, key = { it.page }) { range ->
                        FilterChip(
                            selected = selectedPage == range.page,
                            onClick = { onSelectPage(range.page) },
                            label = { Text(range.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        if (isLoadingEpisodes) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (filteredEpisodes.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("detail_episodes_empty")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (episodeQuery.isNotBlank()) {
                                "No hay episodios que coincidan con \"$episodeQuery\"."
                            } else {
                                stringResource(R.string.detail_episodes_empty)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (episodeQuery.isNotBlank()) {
                                        onEpisodeQueryChange("")
                                    } else {
                                        onRetryEpisodes()
                                    }
                                },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (episodeQuery.isNotBlank()) "Limpiar filtro" else stringResource(R.string.retry)
                                )
                            }

                            if (!isUpcoming && episodeQuery.isBlank()) {
                                Button(
                                    onClick = { onPlayEpisode(1) },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Probar Episodio 1")
                                }
                            }
                        }
                    }
                }
            }
        } else if (isGridView) {
            val rows = filteredEpisodes.chunked(4)
            itemsIndexed(rows, key = { _, row -> "grid_${row.first().number}" }) { rowIdx, rowEpisodes ->
                StaggeredReveal(index = rowIdx, maxStaggerItems = 6) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowEpisodes.forEach { ep ->
                            val watchedEntry = watchedMap[ep.number]
                            EpisodeGridTile(
                                episode = ep,
                                watchedEntry = watchedEntry,
                                isLatestWatched = latestWatched?.episodeNumber == ep.number,
                                accentColor = coverPalette.vibrant,
                                onClick = { onPlayEpisode(ep.number) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowEpisodes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            itemsIndexed(filteredEpisodes, key = { _, ep -> ep.number }) { index, ep ->
                val watchedEntry = watchedMap[ep.number]
                StaggeredReveal(index = index, maxStaggerItems = 7) {
                    EpisodeRowItem(
                        episode = ep,
                        fallbackCover = detail.coverUrl,
                        watchedEntry = watchedEntry,
                        isLatestWatched = latestWatched?.episodeNumber == ep.number,
                        accentColor = coverPalette.vibrant,
                        onClick = { onPlayEpisode(ep.number) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EpisodeGridTile(
    episode: EpisodeItem,
    watchedEntry: WatchHistoryEntry?,
    isLatestWatched: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWatched = watchedEntry != null
    val progress = if (watchedEntry != null && watchedEntry.durationMs > 0L) {
        (watchedEntry.positionMs.toFloat() / watchedEntry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isLatestWatched -> accentColor.copy(alpha = 0.22f)
            isWatched -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isLatestWatched) 1.5.dp else 1.dp,
            color = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .height(64.dp)
            .tvFocusHighlight(shape = RoundedCornerShape(12.dp), focusedScale = 1.04f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("episode_grid_item_${episode.number}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Ep. ${episode.number}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isLatestWatched -> accentColor
                        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (isWatched) {
                    Text(
                        text = if (isLatestWatched) "Actual" else "Visto",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }
            }

            if (isWatched) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Visto",
                    tint = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(13.dp)
                )
            }

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: EpisodeItem,
    fallbackCover: String,
    watchedEntry: WatchHistoryEntry?,
    isLatestWatched: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb = episode.thumbnailUrl.ifBlank { fallbackCover }
    val isWatched = watchedEntry != null
    val progress = if (watchedEntry != null && watchedEntry.durationMs > 0L) {
        (watchedEntry.positionMs.toFloat() / watchedEntry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isLatestWatched) {
            accentColor.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isLatestWatched) {
            BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
        } else null,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isWatched && !isLatestWatched) 0.78f else 1f)
            .tvFocusHighlight(shape = MaterialTheme.shapes.small, focusedScale = 1.02f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("episode_item_${episode.number}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = episode.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA0B0D14)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Episodio ${episode.number}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.onSurface
                )
                if (episode.label.isNotBlank() && !episode.label.equals("Episodio ${episode.number}", true)) {
                    Text(
                        text = episode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isWatched) {
                Surface(
                    color = (if (isLatestWatched) accentColor else MaterialTheme.colorScheme.secondary).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Visto",
                            tint = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isLatestWatched) "Actual" else "Visto",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLatestWatched) accentColor else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
