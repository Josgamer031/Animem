package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.ui.viewmodel.ExploreViewModel

private val STATUS_OPTIONS = listOf(
    "emision" to "En emisión",
    "finalizado" to "Finalizado",
    "estreno" to "Por estrenar"
)

private val TYPE_OPTIONS = listOf(
    "animes" to "Anime",
    "peliculas" to "Película",
    "ovas" to "OVA"
)

private val GENRE_OPTIONS = listOf(
    "accion" to "Acción",
    "aventura" to "Aventura",
    "comedia" to "Comedia",
    "drama" to "Drama",
    "fantasia" to "Fantasía",
    "romance" to "Romance",
    "sci-fi" to "Ciencia Ficción",
    "seinen" to "Seinen",
    "shounen" to "Shounen",
    "isekai" to "Isekai",
    "sobrenatural" to "Sobrenatural",
    "misterio" to "Misterio",
    "deportes" to "Deportes",
    "escolar" to "Escolar"
)

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var filtersExpanded by rememberSaveable { mutableStateOf(true) }

    val activeFilterCount = listOf(
        state.filter.status,
        state.filter.type,
        state.filter.genre
    ).count { it.isNotBlank() }

    val hasActiveFilters = activeFilterCount > 0 || state.filter.query.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Atmospheric Search Header
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.nav_explore),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Catálogo completo · ${state.filter.provider.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { filtersExpanded = !filtersExpanded },
                    shape = RoundedCornerShape(999.dp),
                    color = if (activeFilterCount > 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (activeFilterCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("explore_toggle_filters")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Filtros",
                            tint = if (activeFilterCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (activeFilterCount > 0) "Filtros ($activeFilterCount)" else "Filtros",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeFilterCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Icon(
                            imageVector = if (filtersExpanded) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedProviderSwitcher(
                selectedProvider = state.filter.provider,
                onSelectProvider = viewModel::selectProvider,
                tagPrefix = "explore_provider"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = {
                        Text(
                            text = if (state.filter.provider == AnimeProvider.LATANIME) {
                                "Buscar en Latanime (Latino)…"
                            } else {
                                stringResource(R.string.search_placeholder)
                            },
                            maxLines = 1
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
                        if (state.filter.query.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.updateQuery("")
                                    viewModel.submitSearch()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Limpiar búsqueda"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.submitSearch()
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("explore_search_input")
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitSearch()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("explore_search_button")
                ) {
                    Text(
                        text = stringResource(R.string.cd_search),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Recent Search History Chips
            AnimatedVisibility(
                visible = recentSearches.isNotEmpty() && state.filter.query.isBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.explore_recent_searches),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = viewModel::clearRecentSearches,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Limpiar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSearches, key = { it }) { term ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    focusManager.clearFocus()
                                    viewModel.applyRecentSearch(term)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeRecentSearch(term) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Quitar búsqueda",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.testTag("recent_search_chip_$term")
                            )
                        }
                    }
                }
            }
        }

        // Structured Filter Panel
        AnimatedVisibility(
            visible = filtersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ESTADO Y FORMATO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasActiveFilters) {
                            TextButton(
                                onClick = viewModel::clearAllFilters,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("explore_clear_filters")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.clear_filters),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(STATUS_OPTIONS) { (key, label) ->
                            val selected = state.filter.status == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectStatus(key) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_status_$key")
                            )
                        }
                        items(TYPE_OPTIONS) { (key, label) ->
                            val selected = state.filter.type == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectType(key) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.testTag("filter_type_$key")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GÉNEROS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GENRE_OPTIONS) { (key, label) ->
                            val selected = state.filter.genre == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectGenre(key) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.tertiary
                                ),
                                modifier = Modifier.testTag("filter_genre_$key")
                            )
                        }
                    }
                }
            }
        }

        // Results Summary Bar
        if (!state.isLoading && state.items.isNotEmpty()) {
            val activeSummary = buildList {
                STATUS_OPTIONS.firstOrNull { it.first == state.filter.status }?.second?.let(::add)
                TYPE_OPTIONS.firstOrNull { it.first == state.filter.type }?.second?.let(::add)
                GENRE_OPTIONS.firstOrNull { it.first == state.filter.genre }?.second?.let(::add)
            }.joinToString(" · ")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.items.size} títulos encontrados",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeSummary.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = activeSummary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Results Grid / States with animated provider transition
        AnimatedContent(
            targetState = state.filter.provider,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(240)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(180)))
            },
            label = "explore_provider_transition",
            modifier = Modifier.fillMaxSize()
        ) { _ ->
            when {
                state.isLoading && state.items.isEmpty() -> {
                    ExploreLoadingSkeleton()
                }
                state.errorMessage != null && state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorStateView(
                            message = state.errorMessage.orEmpty(),
                            onRetry = { viewModel.searchOrFilter(resetPage = true) },
                            onUnlockCloudflare = state.blockedUrl?.let { blocked ->
                                {
                                    onUnlockCloudflare(blocked) {
                                        viewModel.searchOrFilter(resetPage = true)
                                    }
                                }
                            }
                        )
                    }
                }
                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EmptyStateView(
                                title = stringResource(R.string.empty_search_title),
                                subtitle = stringResource(R.string.empty_search_subtitle)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (hasActiveFilters) {
                                    OutlinedButton(
                                        onClick = viewModel::clearAllFilters,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(text = stringResource(R.string.clear_filters))
                                    }
                                }
                                Button(
                                    onClick = { viewModel.searchOrFilter(resetPage = true) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("explore_empty_retry_button")
                                ) {
                                    Text(text = stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(136.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, anime -> "${anime.provider.id}_${anime.slug}" }
                        ) { index, anime ->
                            StaggeredReveal(index = index, maxStaggerItems = 8) {
                                AnimePosterCard(
                                    title = anime.title,
                                    coverUrl = anime.coverUrl,
                                    badge = anime.type.ifBlank { anime.status.ifBlank { anime.provider.badgeText } },
                                    subtitle = anime.episodeBadge.ifBlank { anime.status.ifBlank { anime.provider.shortLabel } },
                                    sharedElementKey = posterSharedKey(anime.provider.id, anime.slug),
                                    onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                                    modifier = Modifier.animateItem(),
                                    testTag = "explore_card_${anime.slug}"
                                )
                            }
                        }

                        if (state.hasNextPage) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.loadNextPage() },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("explore_load_more")
                                        ) {
                                            Text(text = stringResource(R.string.load_more))
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
