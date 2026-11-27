package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AnimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.kitsuneSharedPoster(sharedKey: String?): Modifier {
    if (sharedKey.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@kitsuneSharedPoster.sharedElement(
            state = rememberSharedContentState(key = sharedKey),
            animatedVisibilityScope = animatedScope
        )
    }
}

fun posterSharedKey(providerId: String, slug: String): String =
    "poster_${providerId.lowercase()}_${slug.lowercase()}"

@Composable
fun StaggeredReveal(
    index: Int,
    modifier: Modifier = Modifier,
    maxStaggerItems: Int = 8,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(22f) }

    LaunchedEffect(Unit) {
        val delayMs = (index.coerceIn(0, maxStaggerItems) * 32L)
        if (delayMs > 0L) delay(delayMs)
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetYAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim.value
            translationY = offsetYAnim.value
        }
    ) {
        content()
    }
}

@Composable
fun rememberPulseGlowFraction(): Float {
    val transition = rememberInfiniteTransition(label = "cta_pulse_glow")
    val value by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_pulse_alpha"
    )
    return value
}

@Composable
fun AnimatedProviderSwitcher(
    selectedProvider: AnimeProvider,
    onSelectProvider: (AnimeProvider) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "provider_chip"
) {
    val haptics = LocalHapticFeedback.current
    val providers = AnimeProvider.entries
    val selectedIndex = providers.indexOf(selectedProvider).coerceAtLeast(0)
    val isLatino = selectedProvider == AnimeProvider.LATANIME

    val indicatorColor by animateColorAsState(
        targetValue = if (isLatino) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(260),
        label = "provider_indicator_color"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val count = providers.size.coerceAtLeast(1)
            val segmentWidth = maxWidth / count
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "provider_indicator_offset"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                indicatorColor,
                                indicatorColor.copy(alpha = 0.86f)
                            )
                        )
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                providers.forEach { provider ->
                    val selected = provider == selectedProvider
                    val latino = provider == AnimeProvider.LATANIME
                    val contentColor by animateColorAsState(
                        targetValue = when {
                            selected && latino -> MaterialTheme.colorScheme.onSecondary
                            selected -> Color.White
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(200),
                        label = "provider_text_color"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectProvider(provider)
                                }
                            }
                            .testTag("${tagPrefix}_${provider.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (latino) Icons.Rounded.Translate else Icons.Rounded.Subtitles,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${provider.displayName} · ${provider.shortLabel}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

data class SlidingTabItem(
    val label: String,
    val icon: ImageVector,
    val accentColor: Color,
    val selectedContentColor: Color,
    val testTag: String
)

@Composable
fun SlidingPillTabBar(
    tabs: List<SlidingTabItem>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    val activeTab = tabs[safeIndex]
    val pillColor by animateColorAsState(
        targetValue = activeTab.accentColor,
        animationSpec = tween(260),
        label = "sliding_tab_color"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val offset by animateDpAsState(
                targetValue = tabWidth * safeIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "sliding_tab_offset"
            )

            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillColor)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == safeIndex
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) tab.selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "sliding_tab_label_color"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectTab(index)
                                }
                            }
                            .testTag(tab.testTag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
