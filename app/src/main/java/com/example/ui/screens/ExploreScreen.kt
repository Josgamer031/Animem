package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AnimeProvider
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
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
    "sci-fi" to "Sci-Fi",
    "seinen" to "Seinen",
    "shounen" to "Shounen",
    "isekai" to "Isekai"
)

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenAnime: (slug: String, providerId: String) -> Unit,
    onUnlockCloudflare: (targetUrl: String, onUnlocked: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_explore),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimeProvider.entries.forEach { provider ->
                    val selected = state.filter.provider == provider
                    val isLatino = provider == AnimeProvider.LATANIME
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectProvider(provider) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isLatino) Icons.Rounded.Translate else Icons.Rounded.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "${provider.displayName} · ${provider.shortLabel}",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
                            .testTag("explore_provider_${provider.id}")
                    )
                }
            }

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
                            contentDescription = null
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
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("explore_search_button")
                ) {
                    Text(text = stringResource(R.string.cd_search))
                }
            }
        }

        // Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(STATUS_OPTIONS) { (key, label) ->
                val selected = state.filter.status == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectStatus(key) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            items(TYPE_OPTIONS) { (key, label) ->
                val selected = state.filter.type == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectType(key) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
            }
            items(GENRE_OPTIONS) { (key, label) ->
                val selected = state.filter.genre == key
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectGenre(key) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
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
                        OutlinedButton(
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
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(136.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { "${it.provider.id}_${it.slug}" }) { anime ->
                        AnimePosterCard(
                            title = anime.title,
                            coverUrl = anime.coverUrl,
                            badge = anime.type.ifBlank { anime.provider.badgeText },
                            subtitle = anime.episodeBadge.ifBlank { anime.provider.shortLabel },
                            onClick = { onOpenAnime(anime.slug, anime.provider.id) },
                            testTag = "explore_card_${anime.slug}"
                        )
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
