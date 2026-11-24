package com.example.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.example.model.AnimeDetail
import com.example.model.AnimeProvider
import com.example.model.EpisodeItem
import com.example.model.WatchHistoryEntry
import com.example.ui.components.ErrorStateView
import com.example.ui.viewmodel.AnimeDetailUiState
import com.example.ui.viewmodel.AnimeDetailViewModel

@Composable
fun AnimeDetailScreen(
    viewModel: AnimeDetailViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchedMap by viewModel.watchedEpisodes.collectAsStateWithLifecycle()

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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
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
                        episodes = state.displayedEpisodes,
                        selectedPage = state.selectedPage,
                        isLoadingEpisodes = state.isLoadingEpisodes,
                        isLoadingMoreEpisodes = state.isLoadingMoreEpisodes,
                        sortDescending = state.sortDescending,
                        watchedMap = watchedMap,
                        onSelectPage = viewModel::selectEpisodePage,
                        onToggleSort = viewModel::toggleSortOrder,
                        onPlayEpisode = { epNum ->
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

            // Floating Back Button
            Surface(
                color = Color(0xAA0B0D14),
                shape = CircleShape,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(14.dp)
                    .size(48.dp)
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeDetailContent(
    detail: AnimeDetail,
    episodes: List<EpisodeItem>,
    selectedPage: Int,
    isLoadingEpisodes: Boolean,
    isLoadingMoreEpisodes: Boolean,
    sortDescending: Boolean,
    watchedMap: Map<Int, WatchHistoryEntry>,
    onSelectPage: (Int) -> Unit,
    onToggleSort: () -> Unit,
    onPlayEpisode: (Int) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }

    val latestWatched = watchedMap.values.maxByOrNull { it.updatedAtMillis }
    val primaryEpisode = latestWatched?.episodeNumber
        ?: episodes.firstOrNull()?.number
        ?: 1

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // Backdrop & Poster Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(18.dp)
                )
                if (detail.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = detail.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x660B0D14),
                                    Color(0xCC0B0D14),
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
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .width(122.dp)
                            .aspectRatio(0.7f)
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.14f),
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

        // Primary CTA Button
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = { onPlayEpisode(primaryEpisode) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("detail_primary_watch_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (latestWatched != null) {
                            "Continuar Episodio ${latestWatched.episodeNumber}"
                        } else {
                            "Ver Episodio $primaryEpisode"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Genres
                if (detail.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.genres.forEach { genre ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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

        // Episodes Header + Pagination Range Tabs
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
        } else {
            items(episodes, key = { it.number }) { ep ->
                val isWatched = watchedMap.containsKey(ep.number)
                EpisodeRowItem(
                    episode = ep,
                    fallbackCover = detail.coverUrl,
                    isWatched = isWatched,
                    onClick = { onPlayEpisode(ep.number) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
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
private fun EpisodeRowItem(
    episode: EpisodeItem,
    fallbackCover: String,
    isWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb = episode.thumbnailUrl.ifBlank { fallbackCover }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Episodio ${episode.number}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Visto",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(20.dp)
                )
            }
        }
    }
}
