package com.example.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AnimeProvider
import com.example.model.AnimeSummary
import com.example.model.DeviceMode
import com.example.model.FavoriteAnime
import com.example.model.HomeFeed
import com.example.model.UserPreferences
import com.example.model.UserProfile
import com.example.model.WatchHistoryEntry
import com.example.notifications.PremiereAlertScheduler
import com.example.ui.components.AnimatedProviderSwitcher
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.CoverPalette
import com.example.ui.components.ErrorStateView
import com.example.ui.components.HomeLoadingSkeleton
import com.example.ui.components.LatestEpisodeCard
import com.example.ui.components.ProfileSwitcherDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.kitsuneSharedPoster
import com.example.ui.components.posterSharedKey
import com.example.ui.components.profileAvatarDrawable
import com.example.ui.components.rememberCoverPalette
import com.example.ui.components.rememberPulseGlowFraction
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.widget.KitsuneHomeWidgetProvider
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenExplore: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit,
    onShowTvShortcuts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isTvMode = userPreferences.deviceMode == DeviceMode.SMART_TV
    val haptics = LocalHapticFeedback.current

    var showProfileDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    if (showProfileDialog) {
        ProfileSwitcherDialog(
            profiles = profiles,
            activeProfile = activeProfile,
            onSelectProfile = viewModel::switchProfile,
            onCreateProfile = viewModel::createProfile,
            onUpdateProfile = viewModel::updateProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onOpenCloudflareConfig = {
                showProfileDialog = false
                onUnlockCloudflare("${selectedProvider.baseUrl}/") {
                    viewModel.loadHome(isRefresh = true, provider = selectedProvider)
                }
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showSettingsSheet) {
        PlaybackSettingsBottomSheet(
            preferences = userPreferences,
            activeProfile = activeProfile,
            selectedProvider = selectedProvider,
            onToggleAutoPlayNext = viewModel::setAutoPlayNext,
            onToggleAutoSkipOpening = viewModel::setAutoSkipOpening,
            onTogglePremiereAlerts = viewModel::setPremiereAlertsEnabled,
            onSelectDeviceMode = viewModel::setDeviceMode,
            onOpenTvShortcuts = {
                showSettingsSheet = false
                onShowTvShortcuts()
            },
            onOpenProfileSwitcher = {
                showSettingsSheet = false
                showProfileDialog = true
            },
            onOpenConnectionConfig = {
                showSettingsSheet = false
                onUnlockCloudflare("${selectedProvider.baseUrl}/") {
                    viewModel.loadHome(isRefresh = true, provider = selectedProvider)
                }
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    val listState = rememberLazyListState()
    var activeHeroCoverUrl by remember { mutableStateOf<String?>(null) }
    val heroPalette = rememberCoverPalette(
        imageUrl = activeHeroCoverUrl,
        fallbackAccent = if (selectedProvider == AnimeProvider.LATANIME) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        }
    )
    val isHeaderScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
        }
    }
    val topBarContainerColor by animateColorAsState(
        targetValue = if (isHeaderScrolled) {
            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(240),
        label = "home_topbar_bg"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle atmospheric dynamic glow behind the top area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            heroPalette.ambientGlow,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadHome(isRefresh = true, provider = selectedProvider) },
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedProvider,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(240)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(180)))
                },
                label = "home_provider_transition",
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                when (val state = uiState) {
                    is HomeUiState.Loading -> HomeLoadingSkeleton(
                        modifier = Modifier.padding(top = 118.dp)
                    )
                    is HomeUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 118.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorStateView(
                                message = state.message,
                                onRetry = { viewModel.loadHome(isRefresh = false, provider = selectedProvider) },
                                onUnlockCloudflare = state.blockedUrl?.let { blocked ->
                                    {
                                        onUnlockCloudflare(blocked) {
                                            viewModel.loadHome(isRefresh = false, provider = selectedProvider)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    is HomeUiState.Success -> {
                        HomeFeedContent(
                            feed = state.feed,
                            listState = listState,
                            heroPalette = heroPalette,
                            onActiveHeroChanged = { cover -> activeHeroCoverUrl = cover },
                            continueWatching = continueWatching,
                            favorites = favorites,
                            isTvMode = isTvMode,
                            onOpenAnime = onOpenAnime,
                            onPlayEpisode = onPlayEpisode,
                            onOpenExplore = onOpenExplore,
                            onSurpriseMe = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.pickRandomAnime()?.let { picked ->
                                    onOpenAnime(picked.slug, picked.provider.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Translucent Sticky Top Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(topBarContainerColor)
        ) {
            HomeTopHeader(
                selectedProvider = selectedProvider,
                activeProfile = activeProfile,
                isTvMode = isTvMode,
                onShowTvShortcuts = onShowTvShortcuts,
                onAvatarClick = { showProfileDialog = true },
                onSearchClick = onOpenSearch,
                onSettingsClick = { showSettingsSheet = true },
                onRefreshClick = { viewModel.loadHome(isRefresh = true, provider = selectedProvider) }
            )

            AnimatedProviderSwitcher(
                selectedProvider = selectedProvider,
                onSelectProvider = viewModel::selectProvider,
                tagPrefix = "provider_chip",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            AnimatedVisibility(visible = isHeaderScrolled) {
                HorizontalDivider(
                    color = heroPalette.vibrant.copy(alpha = 0.28f),
                    thickness = 1.dp
                )
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    selectedProvider: AnimeProvider,
    activeProfile: UserProfile,
    isTvMode: Boolean,
    onShowTvShortcuts: () -> Unit,
    onAvatarClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onAvatarClick)
                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                .testTag("home_avatar_button")
        ) {
            Image(
                painter = painterResource(id = profileAvatarDrawable(activeProfile.avatarKey)),
                contentDescription = stringResource(R.string.cd_app_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cambiar perfil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "${activeProfile.name} · ${selectedProvider.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedProvider == AnimeProvider.LATANIME) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTvMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onShowTvShortcuts)
                        .testTag("home_tv_mode_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_mode_badge),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeedContent(
    feed: HomeFeed,
    listState: LazyListState,
    heroPalette: CoverPalette,
    onActiveHeroChanged: (String?) -> Unit,
    continueWatching: List<WatchHistoryEntry>,
    favorites: List<FavoriteAnime>,
    isTvMode: Boolean,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onOpenExplore: () -> Unit,
    onSurpriseMe: () -> Unit
) {
    val posterWidth = if (isTvMode) 166.dp else 142.dp
    val latestWidth = if (isTvMode) 216.dp else 188.dp

    // Deduplicate sharedElement keys across sections so Compose SharedTransitionScope never sees duplicate keys
    val featuredKeys = remember(feed.featured) {
        feed.featured.map { posterSharedKey(it.provider.id, it.slug) }.toSet()
    }
    val favoriteKeys = remember(favorites, featuredKeys) {
        favorites.take(12).map { posterSharedKey(it.providerId, it.animeSlug) }
            .filterNot { it in featuredKeys }
            .toSet()
    }
    val airingKeys = remember(feed.airing, featuredKeys, favoriteKeys) {
        feed.airing.map { posterSharedKey(it.provider.id, it.slug) }
            .filterNot { it in featuredKeys || it in favoriteKeys }
            .toSet()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 122.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (feed.featured.isNotEmpty()) {
            item {
                FeaturedHeroCarousel(
                    items = feed.featured,
                    heroPalette = heroPalette,
                    isTvMode = isTvMode,
                    onActiveHeroChanged = onActiveHeroChanged,
                    onOpenAnime = { slug, providerId -> onOpenAnime(slug, providerId) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onSurpriseMe,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = heroPalette.vibrant.copy(alpha = 0.16f),
                        contentColor = heroPalette.vibrant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_surprise_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_surprise_me),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenExplore,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_browse_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nav_explore),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_continue_watching),
                        accentLabel = "${continueWatching.size}"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = continueWatching.take(10),
                            key = { _, entry -> "${entry.providerId}_${entry.animeSlug}_${entry.episodeNumber}" }
                        ) { index, entry ->
                            StaggeredReveal(index = index, maxStaggerItems = 5) {
                                ContinueWatchingCard(
                                    entry = entry,
                                    isTvMode = isTvMode,
                                    onClick = {
                                        onPlayEpisode(
                                            entry.animeSlug,
                                            entry.episodeNumber,
                                            entry.animeTitle,
                                            entry.coverUrl,
                                            entry.providerId,
                                            entry.episodeUrl
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.my_list_title),
                        accentLabel = "${favorites.size}"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = favorites.take(12),
                            key = { _, fav -> "fav_${fav.profileId}_${fav.providerId}_${fav.animeSlug}" }
                        ) { index, fav ->
                            val sharedKey = posterSharedKey(fav.providerId, fav.animeSlug)
                            StaggeredReveal(index = index, maxStaggerItems = 5) {
                                AnimePosterCard(
                                    title = fav.animeTitle,
                                    coverUrl = fav.coverUrl,
                                    badge = fav.type.ifBlank { AnimeProvider.fromId(fav.providerId).badgeText },
                                    subtitle = fav.status.ifBlank { fav.totalEpisodesText },
                                    sharedElementKey = if (sharedKey in favoriteKeys) sharedKey else null,
                                    onClick = { onOpenAnime(fav.animeSlug, fav.providerId) },
                                    modifier = Modifier.width(posterWidth),
                                    testTag = "home_fav_card_${fav.animeSlug}"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (feed.latestEpisodes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_latest_episodes),
                        accentLabel = if (feed.provider == AnimeProvider.LATANIME) "Latino" else "Hoy"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = feed.latestEpisodes,
                            key = { _, ep -> "${ep.provider.id}_${ep.animeSlug}_${ep.episodeNumber}" }
                        ) { index, ep ->
                            StaggeredReveal(index = index, maxStaggerItems = 5) {
                                LatestEpisodeCard(
                                    animeTitle = ep.animeTitle,
                                    episodeLabel = ep.episodeLabel,
                                    coverUrl = ep.coverUrl,
                                    onPlayClick = {
                                        onPlayEpisode(
                                            ep.animeSlug,
                                            ep.episodeNumber,
                                            ep.animeTitle,
                                            ep.coverUrl,
                                            ep.provider.id,
                                            ep.episodeUrl
                                        )
                                    },
                                    onDetailClick = { onOpenAnime(ep.animeSlug, ep.provider.id) },
                                    modifier = Modifier.width(latestWidth),
                                    testTag = "latest_ep_${ep.animeSlug}_${ep.episodeNumber}"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (feed.airing.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_airing_spotlight),
                        accentLabel = "${feed.airing.size}",
                        actionLabel = stringResource(R.string.home_see_all),
                        onActionClick = onOpenExplore
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = feed.airing,
                            key = { _, anime -> "air_${anime.provider.id}_${anime.slug}" }
                        ) { index, anime ->
                            val sharedKey = posterSharedKey(anime.provider.id, anime.slug)
                            StaggeredReveal(index = index, maxStaggerItems = 5) {
                                AnimePosterCard(
                                    title = anime.title,
                                    coverUrl = anime.coverUrl,
                                    badge = anime.type.ifBlank { "Emisión" },
                                    subtitle = anime.episodeBadge,
                                    sharedElementKey = if (sharedKey in airingKeys) sharedKey else null,
                                    onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                                    modifier = Modifier.width(posterWidth),
                                    testTag = "airing_card_${anime.slug}"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (feed.recentAdded.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.section_recent_animes),
                        accentLabel = "${feed.recentAdded.size}",
                        actionLabel = stringResource(R.string.home_see_all),
                        onActionClick = onOpenExplore
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = feed.recentAdded,
                            key = { _, anime -> "rec_${anime.provider.id}_${anime.slug}" }
                        ) { index, anime ->
                            val sharedKey = posterSharedKey(anime.provider.id, anime.slug)
                            val uniqueKey = if (
                                sharedKey !in featuredKeys &&
                                sharedKey !in favoriteKeys &&
                                sharedKey !in airingKeys
                            ) {
                                sharedKey
                            } else null
                            StaggeredReveal(index = index, maxStaggerItems = 5) {
                                AnimePosterCard(
                                    title = anime.title,
                                    coverUrl = anime.coverUrl,
                                    badge = anime.episodeBadge.ifBlank { anime.type },
                                    subtitle = if (anime.episodeBadge.isNotBlank()) anime.type else anime.provider.badgeText,
                                    sharedElementKey = uniqueKey,
                                    onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                                    modifier = Modifier.width(posterWidth),
                                    testTag = "recent_card_${anime.slug}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedHeroCarousel(
    items: List<AnimeSummary>,
    heroPalette: CoverPalette,
    isTvMode: Boolean,
    onActiveHeroChanged: (String?) -> Unit,
    onOpenAnime: (slug: String, providerId: String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val pulseAlpha = rememberPulseGlowFraction()

    LaunchedEffect(pagerState.currentPage, items) {
        onActiveHeroChanged(items.getOrNull(pagerState.currentPage)?.coverUrl)
    }

    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5_500L)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val anime = items[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            val cardScale = 1f - (0.06f * pageOffset)
            val cardAlpha = 1f - (0.24f * pageOffset)
            val isFirstOccurrence = remember(items, page) {
                items.indexOfFirst { it.provider.id == anime.provider.id && it.slug == anime.slug } == page
            }
            val sharedKey = if (isFirstOccurrence) {
                posterSharedKey(anime.provider.id, anime.slug)
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTvMode) 336.dp else 296.dp)
                    .tvFocusHighlight(shape = MaterialTheme.shapes.large)
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    }
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                heroPalette.vibrant.copy(alpha = 0.72f),
                                Color.White.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ),
                        shape = MaterialTheme.shapes.large
                    )
                    .clickable { onOpenAnime(anime.slug, anime.provider.id) }
                    .testTag("hero_card_${anime.slug}")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (anime.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = stringResource(R.string.cd_poster, anime.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .kitsuneSharedPoster(sharedKey)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0x360B0D14),
                                    0.38f to Color(0x780B0D14),
                                    0.72f to Color(0xDA0B0D14),
                                    1.0f to Color(0xFA0B0D14)
                                )
                            )
                        )
                )

                // Editorial Top Badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = heroPalette.vibrant.copy(alpha = 0.90f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "DESTACADO · ${anime.provider.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (items.size > 1) {
                        Surface(
                            color = Color(0x99090B12),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Text(
                                text = "${page + 1} / ${items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (anime.type.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = anime.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (anime.episodeBadge.isNotBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = anime.episodeBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (anime.type.isNotBlank() || anime.episodeBadge.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (anime.synopsisSnippet.isNotBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = anime.synopsisSnippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = heroPalette.vibrant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = heroPalette.vibrant.copy(alpha = pulseAlpha),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("hero_watch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.hero_watch_now),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0x66090B12),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.hero_details))
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(items.size) { index ->
                        val selected = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 22.dp else 7.dp,
                            animationSpec = tween(250),
                            label = "hero_dot_width"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (selected) heroPalette.vibrant else MaterialTheme.colorScheme.outline,
                            animationSpec = tween(250),
                            label = "hero_dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsBottomSheet(
    preferences: UserPreferences,
    activeProfile: UserProfile,
    selectedProvider: AnimeProvider,
    onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleAutoSkipOpening: (Boolean) -> Unit,
    onTogglePremiereAlerts: (Boolean) -> Unit,
    onSelectDeviceMode: (DeviceMode) -> Unit,
    onOpenTvShortcuts: () -> Unit,
    onOpenProfileSwitcher: () -> Unit,
    onOpenConnectionConfig: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onTogglePremiereAlerts(true)
            PremiereAlertScheduler.schedulePeriodic(context)
        } else {
            onTogglePremiereAlerts(false)
            Toast.makeText(
                context,
                context.getString(R.string.settings_premiere_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_interface_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isPhone = preferences.deviceMode == DeviceMode.PHONE
                        FilterChip(
                            selected = isPhone,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDeviceMode(DeviceMode.PHONE)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.device_mode_phone_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .testTag("settings_mode_phone")
                        )

                        FilterChip(
                            selected = !isPhone,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDeviceMode(DeviceMode.SMART_TV)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.device_mode_tv_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .testTag("settings_mode_tv")
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenTvShortcuts,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("settings_tv_shortcuts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsRemote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.tv_shortcuts_title))
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_next_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_next_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.autoPlayNext,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleAutoPlayNext(it)
                            },
                            modifier = Modifier.testTag("settings_auto_next_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_skip_op_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_skip_op_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.autoSkipOpening,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleAutoSkipOpening(it)
                            },
                            modifier = Modifier.testTag("settings_auto_skip_switch")
                        )
                    }
                }
            }

            // Premiere Alerts Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_premiere_alerts_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_premiere_alerts_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.premiereAlertsEnabled,
                            onCheckedChange = { wantEnabled ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (!wantEnabled) {
                                    onTogglePremiereAlerts(false)
                                    PremiereAlertScheduler.cancelPeriodic(context)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onTogglePremiereAlerts(true)
                                    PremiereAlertScheduler.schedulePeriodic(context)
                                }
                            },
                            modifier = Modifier.testTag("settings_premiere_alerts_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PremiereAlertScheduler.triggerImmediateCheck(context)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_premiere_check_queued),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("settings_check_premieres_now")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_check_premieres_now))
                    }
                }
            }

            // Home Screen Widget Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_widget_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_widget_pin_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val manager = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, KitsuneHomeWidgetProvider::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                manager.isRequestPinAppWidgetSupported
                            ) {
                                manager.requestPinAppWidget(provider, null, null)
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_widget_pin_unsupported),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("settings_pin_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_widget_pin_button))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenProfileSwitcher,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("settings_switch_profile_button")
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Perfil (${activeProfile.name})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenConnectionConfig,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("settings_connection_button")
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Red ${selectedProvider.displayName}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0.15f
    val entryProvider = AnimeProvider.fromId(entry.providerId)

    Surface(
        modifier = Modifier
            .width(if (isTvMode) 214.dp else 190.dp)
            .tvFocusHighlight(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("continue_card_${entry.animeSlug}_${entry.episodeNumber}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (entry.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = entry.coverUrl,
                        contentDescription = stringResource(R.string.cd_poster, entry.animeTitle),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x660B0D14))
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Ep. ${entry.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (entryProvider == AnimeProvider.LATANIME) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Latino",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0B0D14)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.hero_watch_now),
                        tint = Color.White
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            Text(
                text = entry.animeTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
