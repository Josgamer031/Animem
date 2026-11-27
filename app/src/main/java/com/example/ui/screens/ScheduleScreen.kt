package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AnimeProvider
import com.example.ui.components.AnimatedProviderSwitcher
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ExploreLoadingSkeleton
import com.example.ui.components.StaggeredReveal
import com.example.ui.components.posterSharedKey
import com.example.ui.viewmodel.ScheduleUiState
import com.example.ui.viewmodel.ScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Atmospheric Gradient Header
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${selectedProvider.displayName} · ${selectedProvider.shortLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedProvider == AnimeProvider.LATANIME) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = { viewModel.loadSchedule(isRefresh = true, provider = selectedProvider) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("schedule_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Animated Provider Switcher
            AnimatedProviderSwitcher(
                selectedProvider = selectedProvider,
                onSelectProvider = viewModel::selectProvider,
                tagPrefix = "schedule_provider",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadSchedule(isRefresh = true, provider = selectedProvider) },
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedProvider,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(240)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(180)))
                },
                label = "schedule_provider_transition",
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                when (val state = uiState) {
                    is ScheduleUiState.Loading -> {
                        ExploreLoadingSkeleton()
                    }

                    is ScheduleUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorStateView(
                                message = state.message,
                                onRetry = { viewModel.loadSchedule(isRefresh = false, provider = selectedProvider) },
                                onUnlockCloudflare = state.blockedUrl?.let { blocked ->
                                    {
                                        onUnlockCloudflare(blocked) {
                                            viewModel.loadSchedule(isRefresh = false, provider = selectedProvider)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    is ScheduleUiState.Success -> {
                        val days = state.schedule.days
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (days.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(days) { index, day ->
                                        val selected = index == selectedDayIndex
                                        val chipScale by animateFloatAsState(
                                            targetValue = if (selected) 1.05f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "schedule_day_scale"
                                        )
                                        val label = if (day.items.isNotEmpty()) {
                                            "${day.dayLabel} (${day.items.size})"
                                        } else {
                                            day.dayLabel
                                        }
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.selectDay(index)
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .scale(chipScale)
                                                .testTag("schedule_day_${day.dayKey}")
                                        )
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = selectedDayIndex,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    (slideInHorizontally(tween(240)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(180)))
                                },
                                label = "schedule_day_grid",
                                modifier = Modifier.fillMaxSize()
                            ) { dayIdx ->
                                val activeDay = days.getOrNull(dayIdx)
                                val items = activeDay?.items.orEmpty()
                                if (items.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EmptyStateView(
                                            title = stringResource(R.string.schedule_empty_title),
                                            subtitle = stringResource(R.string.schedule_empty_subtitle)
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(136.dp),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(
                                            items = items,
                                            key = { _, item -> "${item.provider.id}_${activeDay?.dayKey}_${item.slug}" }
                                        ) { index, item ->
                                            StaggeredReveal(index = index, maxStaggerItems = 8) {
                                                AnimePosterCard(
                                                    title = item.title,
                                                    coverUrl = item.coverUrl,
                                                    badge = item.releaseTime.ifBlank { item.provider.badgeText },
                                                    subtitle = item.episodeLabel.ifBlank { activeDay?.dayLabel.orEmpty() },
                                                    sharedElementKey = posterSharedKey(item.provider.id, item.slug),
                                                    onClick = { onOpenAnime(item.slug, item.provider.id) },
                                                    modifier = Modifier.animateItem(),
                                                    testTag = "schedule_card_${item.slug}"
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
        }
    }
}
