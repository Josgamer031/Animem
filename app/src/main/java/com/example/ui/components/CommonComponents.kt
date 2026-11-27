package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R

@Composable
fun AnimePosterCard(
    title: String,
    coverUrl: String,
    badge: String = "",
    subtitle: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    testTag: String = "anime_poster_card"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "poster_press_scale"
    )

    Surface(
        modifier = modifier
            .kitsuneSharedPoster(sharedElementKey)
            .tvFocusHighlight(shape = MaterialTheme.shapes.medium)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    ),
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(R.string.cd_poster, title),
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
                                0.0f to Color(0x18090B12),
                                0.45f to Color.Transparent,
                                0.78f to Color(0xB8090B12),
                                1.0f to Color(0xF5090B12)
                            )
                        )
                    )
            )

            if (badge.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LatestEpisodeCard(
    animeTitle: String,
    episodeLabel: String,
    coverUrl: String,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "latest_episode_card"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "latest_ep_press_scale"
    )

    Surface(
        modifier = modifier
            .tvFocusHighlight(shape = MaterialTheme.shapes.medium)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onPlayClick
            )
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = stringResource(R.string.cd_poster, animeTitle),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x14090B12), Color(0xC2090B12))
                            )
                        )
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = episodeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0B0D14))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.hero_watch_now),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDetailClick)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = animeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    accentLabel: String = "",
    modifier: Modifier = Modifier,
    actionLabel: String = "",
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (accentLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = accentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (onActionClick != null && actionLabel.isNotBlank()) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_sweep")
    val translate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            highlight,
            base.copy(alpha = 0.45f)
        ),
        start = Offset(translate - 360f, translate - 360f),
        end = Offset(translate, translate)
    )
}

@Composable
fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp)
                .clip(MaterialTheme.shapes.large)
                .background(shimmerBrush)
        )
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(188.dp)
                        .height(118.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(shimmerBrush)
                )
            }
        }
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(142.dp)
                        .aspectRatio(0.70f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun ExploreLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(136.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(9) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
fun AnimeDetailLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(shimmerBrush)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(shimmerBrush)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onUnlockCloudflare: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("retry_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.retry))
            }

            if (onUnlockCloudflare != null) {
                Button(
                    onClick = onUnlockCloudflare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("unlock_cloudflare_button")
                ) {
                    Text(text = "Desbloquear")
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(MaterialTheme.shapes.large)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.large
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660B0D14))
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
