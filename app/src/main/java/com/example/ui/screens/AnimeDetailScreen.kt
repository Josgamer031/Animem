package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onNavigateToGenreExplore: () -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchedMap by viewModel.watchedEpisodes.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val episodeQuery by viewModel.episodeQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favorite_scale"
    )

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
                        episodeQuery = episodeQuery,
                        selectedPage = state.selectedPage,
                        isLoadingEpisodes = state.isLoadingEpisodes,
                        isLoadingMoreEpisodes = state.isLoadingMoreEpisodes,
                        sortDescending = state.sortDescending,
                        isFavorite = isFavorite,
                        watchedMap = watchedMap,
                        onEpisodeQueryChange = viewModel::updateEpisodeQuery,
                        onSelectPage = viewModel::selectEpisodePage,
                        onToggleSort = viewModel::toggleSortOrder,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onGenreClick = { genre ->
                            viewModel.onGenreSelected(genre)
                            onNavigateToGenreExplore()
                        },
                        onRetryEpisodes = viewModel::loadDetail,
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

            // Floating Top Bar: Back + Favorite + Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xAA0B0D14),
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

                val successDetail = (uiState as? AnimeDetailUiState.Success)?.detail
                if (successDetail != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = Color(0xAA0B0D14),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = viewModel::toggleFavorite,
                                modifier = Modifier.testTag("detail_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(R.string.cd_favorite),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.scale(favoriteScale)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xAA0B0D14),
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeDetailContent(
    detail: AnimeDetail,
    episodes: List<EpisodeItem>,
    episodeQuery: String,
    selectedPage: Int,
    isLoadingEpisodes: Boolean,
    isLoadingMoreEpisodes: Boolean,
    sortDescending: Boolean,
    isFavorite: Boolean,
    watchedMap: Map<Int, WatchHistoryEntry>,
    onEpisodeQueryChange: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onToggleSort: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGenreClick: (String) -> Unit,
    onRetryEpisodes: () -> Unit,
    onPlayEpisode: (Int) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }

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
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
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
                                "Continuar Ep. ${latestWatched.episodeNumber}"
                            } else {
                                "Ver Episodio $primaryEpisode"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isFavorite) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (isFavorite) {
                                MaterialTheme.colorScheme.primary
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
        } else {
            items(filteredEpisodes, key = { it.number }) { ep ->
                val watchedEntry = watchedMap[ep.number]
                EpisodeRowItem(
                    episode = ep,
                    fallbackCover = detail.coverUrl,
                    watchedEntry = watchedEntry,
                    onClick = { onPlayEpisode(ep.number) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .animateItem()
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
    watchedEntry: WatchHistoryEntry?,
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
                if (progress > 0f) {
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
