package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AnimeProvider
import com.example.model.AnimeSummary
import com.example.model.FavoriteAnime
import com.example.model.HomeFeed
import com.example.model.UserProfile
import com.example.model.WatchHistoryEntry
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.ErrorStateView
import com.example.ui.components.HomeLoadingSkeleton
import com.example.ui.components.LatestEpisodeCard
import com.example.ui.components.ProfileSwitcherDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.profileAvatarDrawable
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onOpenSearch: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    var showProfileDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            HomeTopHeader(
                selectedProvider = selectedProvider,
                activeProfile = activeProfile,
                onAvatarClick = { showProfileDialog = true },
                onSearchClick = onOpenSearch,
                onRefreshClick = { viewModel.loadHome(isRefresh = true, provider = selectedProvider) }
            )

            ProviderSwitcherBar(
                selectedProvider = selectedProvider,
                onSelectProvider = viewModel::selectProvider
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadHome(isRefresh = true, provider = selectedProvider) },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> HomeLoadingSkeleton()
                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                        continueWatching = continueWatching,
                        favorites = favorites,
                        onOpenAnime = onOpenAnime,
                        onPlayEpisode = onPlayEpisode
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    selectedProvider: AnimeProvider,
    activeProfile: UserProfile,
    onAvatarClick: () -> Unit,
    onSearchClick: () -> Unit,
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
                        color = MaterialTheme.colorScheme.onBackground
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
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ProviderSwitcherBar(
    selectedProvider: AnimeProvider,
    onSelectProvider: (AnimeProvider) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimeProvider.entries.forEach { provider ->
            val selected = provider == selectedProvider
            val isLatino = provider == AnimeProvider.LATANIME
            FilterChip(
                selected = selected,
                onClick = { onSelectProvider(provider) },
                leadingIcon = {
                    Icon(
                        imageVector = if (isLatino) Icons.Rounded.Translate else Icons.Rounded.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = "${provider.displayName} · ${provider.shortLabel}",
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (isLatino) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    selectedLabelColor = if (isLatino) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        Color.White
                    },
                    selectedLeadingIconColor = if (isLatino) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        Color.White
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("provider_chip_${provider.id}")
            )
        }
    }
}

@Composable
private fun HomeFeedContent(
    feed: HomeFeed,
    continueWatching: List<WatchHistoryEntry>,
    favorites: List<FavoriteAnime>,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (feed.featured.isNotEmpty()) {
            item {
                FeaturedHeroCarousel(
                    items = feed.featured,
                    onOpenAnime = { slug, providerId -> onOpenAnime(slug, providerId) }
                )
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
                        items(
                            items = continueWatching.take(10),
                            key = { "${it.providerId}_${it.animeSlug}_${it.episodeNumber}" }
                        ) { entry ->
                            ContinueWatchingCard(
                                entry = entry,
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
                        items(
                            items = favorites.take(12),
                            key = { "fav_${it.profileId}_${it.providerId}_${it.animeSlug}" }
                        ) { fav ->
                            AnimePosterCard(
                                title = fav.animeTitle,
                                coverUrl = fav.coverUrl,
                                badge = fav.type.ifBlank { AnimeProvider.fromId(fav.providerId).badgeText },
                                subtitle = fav.status.ifBlank { fav.totalEpisodesText },
                                onClick = { onOpenAnime(fav.animeSlug, fav.providerId) },
                                modifier = Modifier.width(142.dp),
                                testTag = "home_fav_card_${fav.animeSlug}"
                            )
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
                        items(
                            items = feed.latestEpisodes,
                            key = { "${it.provider.id}_${it.animeSlug}_${it.episodeNumber}" }
                        ) { ep ->
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
                                modifier = Modifier.width(188.dp),
                                testTag = "latest_ep_${ep.animeSlug}_${ep.episodeNumber}"
                            )
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
                        accentLabel = "${feed.airing.size}"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(feed.airing, key = { "air_${it.provider.id}_${it.slug}" }) { anime ->
                            AnimePosterCard(
                                title = anime.title,
                                coverUrl = anime.coverUrl,
                                badge = anime.type.ifBlank { "Emisión" },
                                subtitle = anime.episodeBadge,
                                onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                                modifier = Modifier.width(142.dp),
                                testTag = "airing_card_${anime.slug}"
                            )
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
                        accentLabel = "${feed.recentAdded.size}"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(feed.recentAdded, key = { "rec_${it.provider.id}_${it.slug}" }) { anime ->
                            AnimePosterCard(
                                title = anime.title,
                                coverUrl = anime.coverUrl,
                                badge = anime.episodeBadge.ifBlank { anime.type },
                                subtitle = if (anime.episodeBadge.isNotBlank()) anime.type else anime.provider.badgeText,
                                onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                                modifier = Modifier.width(142.dp),
                                testTag = "recent_card_${anime.slug}"
                            )
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
    onOpenAnime: (slug: String, providerId: String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val anime = items[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(268.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
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
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0x280B0D14),
                                    0.42f to Color(0x880B0D14),
                                    1.0f to Color(0xF60B0D14)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    if (anime.type.isNotBlank() || anime.episodeBadge.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = anime.type.ifBlank { anime.episodeBadge },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (anime.synopsisSnippet.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = anime.synopsisSnippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("hero_watch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.hero_watch_now))
                        }

                        OutlinedButton(
                            onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.height(42.dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onClick: () -> Unit
) {
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0.15f
    val entryProvider = AnimeProvider.fromId(entry.providerId)

    Surface(
        modifier = Modifier
            .width(190.dp)
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
