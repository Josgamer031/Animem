package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.model.FavoriteAnime
import com.example.model.ProfileWatchStats
import com.example.model.WatchHistoryEntry
import com.example.model.WatchStatus
import com.example.model.formatWatchDuration
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.profileAvatarDrawable
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlayEpisode: (slug: String, episode: Int, title: String, coverUrl: String, providerId: String, episodeUrl: String) -> Unit,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.historyEntries.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val filteredFavorites by viewModel.filteredFavorites.collectAsStateWithLifecycle()
    val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val profileStats by viewModel.profileStats.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Historial, 1 = Mi Lista, 2 = Estadísticas

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Atmospheric Header + 3 Segmented Pill Tabs
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = profileAvatarDrawable(activeProfile.avatarKey)),
                        contentDescription = activeProfile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> stringResource(R.string.nav_history)
                                1 -> stringResource(R.string.my_list_title)
                                else -> stringResource(R.string.library_tab_stats)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Perfil: ${activeProfile.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (selectedTab == 0 && entries.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearAll()
                        },
                        modifier = Modifier.testTag("history_clear_all")
                    ) {
                        Text(
                            text = stringResource(R.string.clear_history),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = 0
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Historial (${entries.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("library_tab_history")
                    )
                }

                item {
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = 1
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Mi Lista (${favorites.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("library_tab_mylist")
                    )
                }

                item {
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = 2
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.library_tab_stats),
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("library_tab_stats")
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "library_tab_content",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                0 -> {
                    if (entries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                title = stringResource(R.string.empty_history_title),
                                subtitle = stringResource(R.string.empty_history_subtitle)
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = entries,
                                key = { "${it.profileId}_${it.providerId}_${it.animeSlug}_${it.episodeNumber}" }
                            ) { entry ->
                                HistoryEntryCard(
                                    entry = entry,
                                    onPlay = {
                                        onPlayEpisode(
                                            entry.animeSlug,
                                            entry.episodeNumber,
                                            entry.animeTitle,
                                            entry.coverUrl,
                                            entry.providerId,
                                            entry.episodeUrl
                                        )
                                    },
                                    onOpenDetail = { onOpenAnime(entry.animeSlug, entry.providerId) },
                                    onDelete = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.removeEntry(entry.animeSlug, entry.episodeNumber, entry.providerId)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // WatchStatus Filter Bar
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                val selected = selectedStatusFilter == null
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setStatusFilter(null) },
                                    label = { Text("Todos (${favorites.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("mylist_filter_all")
                                )
                            }
                            items(WatchStatus.entries) { status ->
                                val count = favorites.count { it.watchStatus == status }
                                val selected = selectedStatusFilter == status
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setStatusFilter(status) },
                                    label = { Text("${status.label} ($count)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = watchStatusColor(status),
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("mylist_filter_${status.name.lowercase()}")
                                )
                            }
                        }

                        if (filteredFavorites.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateView(
                                    title = stringResource(R.string.empty_favorites_title),
                                    subtitle = if (favorites.isEmpty()) {
                                        stringResource(R.string.empty_favorites_subtitle)
                                    } else {
                                        "No hay animes marcados como ${selectedStatusFilter?.label.orEmpty()}."
                                    }
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(142.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredFavorites,
                                    key = { "${it.profileId}_${it.providerId}_${it.animeSlug}" }
                                ) { fav ->
                                    FavoriteGridItem(
                                        favorite = fav,
                                        onClick = { onOpenAnime(fav.animeSlug, fav.providerId) },
                                        onChangeStatus = { newStatus ->
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateFavoriteStatus(fav.animeSlug, fav.providerId, newStatus)
                                        },
                                        onRemove = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.removeFavorite(fav.animeSlug, fav.providerId)
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    ProfileStatsTab(
                        profileName = activeProfile.name,
                        stats = profileStats
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsTab(
    profileName: String,
    stats: ProfileWatchStats
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("stats_tab_content")
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatKpiCard(
                    value = stats.totalEpisodesWatched.toString(),
                    label = stringResource(R.string.stats_episodes_watched),
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatKpiCard(
                    value = stats.uniqueAnimesWatched.toString(),
                    label = stringResource(R.string.stats_animes_watched),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatKpiCard(
                    value = formatWatchDuration(stats.totalWatchTimeMs),
                    label = stringResource(R.string.stats_watch_time),
                    accentColor = Color(0xFF4ADE80),
                    modifier = Modifier.weight(1f)
                )
                StatKpiCard(
                    value = stats.totalInList.toString(),
                    label = stringResource(R.string.stats_in_my_list),
                    accentColor = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.stats_status_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val total = stats.totalInList.coerceAtLeast(1)
                    val breakdown = listOf(
                        Triple(WatchStatus.WATCHING, stats.watchingCount, watchStatusColor(WatchStatus.WATCHING)),
                        Triple(WatchStatus.PLAN_TO_WATCH, stats.planToWatchCount, watchStatusColor(WatchStatus.PLAN_TO_WATCH)),
                        Triple(WatchStatus.COMPLETED, stats.completedCount, watchStatusColor(WatchStatus.COMPLETED)),
                        Triple(WatchStatus.DROPPED, stats.droppedCount, watchStatusColor(WatchStatus.DROPPED))
                    )
                    breakdown.forEach { (status, count, barColor) ->
                        val fraction = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = status.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = barColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (stats.totalInList == 0) 0f else fraction },
                                color = barColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.stats_provider_breakdown)} · $profileName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val totalEps = (stats.jkanimeEpisodesCount + stats.latanimeEpisodesCount).coerceAtLeast(1)
                    val jkFrac = stats.jkanimeEpisodesCount.toFloat() / totalEps.toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "JKAnime (Sub): ${stats.jkanimeEpisodesCount} eps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Latanime (Latino): ${stats.latanimeEpisodesCount} eps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (stats.jkanimeEpisodesCount + stats.latanimeEpisodesCount == 0) 0.5f else jkFrac
                        },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun StatKpiCard(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FavoriteGridItem(
    favorite: FavoriteAnime,
    onClick: () -> Unit,
    onChangeStatus: (WatchStatus) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val provider = AnimeProvider.fromId(favorite.providerId)
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AnimePosterCard(
            title = favorite.animeTitle,
            coverUrl = favorite.coverUrl,
            badge = provider.badgeText,
            subtitle = favorite.watchStatus.label,
            onClick = onClick,
            testTag = "favorite_card_${favorite.animeSlug}"
        )

        // Status pill top-start with dropdown switcher
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Surface(
                color = watchStatusColor(favorite.watchStatus).copy(alpha = 0.92f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { statusMenuExpanded = true }
                    .testTag("favorite_status_pill_${favorite.animeSlug}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = favorite.watchStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cambiar estado",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = statusMenuExpanded,
                onDismissRequest = { statusMenuExpanded = false }
            ) {
                WatchStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.label) },
                        leadingIcon = if (status == favorite.watchStatus) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            statusMenuExpanded = false
                            onChangeStatus(status)
                        }
                    )
                }
            }
        }

        Surface(
            color = Color(0xBD0B0D14),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(36.dp)
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("favorite_remove_${favorite.animeSlug}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkRemove,
                    contentDescription = "Quitar de Mi Lista",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun watchStatusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.WATCHING -> Color(0xFFFF4D6D)
    WatchStatus.PLAN_TO_WATCH -> Color(0xFF0EA5E9)
    WatchStatus.COMPLETED -> Color(0xFF22C55E)
    WatchStatus.DROPPED -> Color(0xFF94A3B8)
}

@Composable
private fun HistoryEntryCard(
    entry: WatchHistoryEntry,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusHighlight(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPlay)
            .testTag("history_item_${entry.animeSlug}_${entry.episodeNumber}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenDetail)
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val entryProvider = AnimeProvider.fromId(entry.providerId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Episodio ${entry.episodeNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entryProvider.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (progress > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.hero_watch_now),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar del historial",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
