package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.UserProfile
import com.example.ui.components.PROFILE_AVATAR_CHOICES
import com.example.ui.components.profileAvatarDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class LaunchPhase {
    INTRO,
    PROFILES
}

@Composable
fun LaunchProfileGateScreen(
    profiles: List<UserProfile>,
    activeProfile: UserProfile,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (name: String, avatarKey: String) -> Unit,
    onUpdateProfile: (profileId: String, name: String, avatarKey: String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onEnterApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by rememberSaveable { mutableStateOf(LaunchPhase.INTRO) }
    var isManaging by remember { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(phase) {
        if (phase == LaunchPhase.INTRO) {
            delay(2_100L)
            phase = LaunchPhase.PROFILES
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("launch_profile_gate")
    ) {
        // Soft ambient cinema backdrop
        Image(
            painter = painterResource(id = R.drawable.img_cinema_backdrop_1795549224193),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (phase == LaunchPhase.INTRO) 0.24f else 0.14f)
        )

        // Radial aura scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.22f),
                            backgroundColor.copy(alpha = 0.88f),
                            backgroundColor.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(animationSpec = tween(520)) + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(520, easing = FastOutSlowInEasing)
                )) togetherWith (fadeOut(animationSpec = tween(360)) + scaleOut(
                    targetScale = 1.05f,
                    animationSpec = tween(360)
                ))
            },
            label = "launch_phase_transition",
            modifier = Modifier.fillMaxSize()
        ) { currentPhase ->
            when (currentPhase) {
                LaunchPhase.INTRO -> {
                    LaunchSplashAnimation(
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        tertiaryColor = tertiaryColor,
                        onSkipIntro = { phase = LaunchPhase.PROFILES }
                    )
                }

                LaunchPhase.PROFILES -> {
                    StartupProfileChooser(
                        profiles = profiles,
                        activeProfile = activeProfile,
                        isManaging = isManaging,
                        onToggleManage = { isManaging = !isManaging },
                        onSelectAndEnter = { profileId ->
                            onSelectProfile(profileId)
                            onEnterApp()
                        },
                        onEditProfile = { profile ->
                            editingProfile = profile
                            editorVisible = true
                        },
                        onDeleteProfile = onDeleteProfile,
                        onOpenCreate = {
                            editingProfile = null
                            editorVisible = true
                        },
                        onContinueActive = {
                            onSelectProfile(activeProfile.id)
                            onEnterApp()
                        }
                    )
                }
            }
        }
    }

    if (editorVisible) {
        LaunchProfileEditorDialog(
            editingProfile = editingProfile,
            defaultAvatarIndex = profiles.size % PROFILE_AVATAR_CHOICES.size,
            onDismiss = {
                editorVisible = false
                editingProfile = null
            },
            onSave = { name, avatarKey ->
                val target = editingProfile
                if (target != null) {
                    onUpdateProfile(target.id, name, avatarKey)
                } else {
                    onCreateProfile(name, avatarKey)
                }
                editorVisible = false
                editingProfile = null
            }
        )
    }
}

@Composable
private fun LaunchSplashAnimation(
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    onSkipIntro: () -> Unit
) {
    val emblemScale = remember { Animatable(0.32f) }
    val emblemAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(38f) }
    val dividerWidth = remember { Animatable(0f) }
    val badgeAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "kitsune_intro_ambient")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )
    val emberPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ember_phase"
    )

    LaunchedEffect(Unit) {
        launch {
            emblemAlpha.animateTo(1f, tween(360))
        }
        launch {
            emblemScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = 220f
                )
            )
        }
        delay(260L)
        launch {
            titleAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        delay(180L)
        launch {
            dividerWidth.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            badgeAlpha.animateTo(1f, tween(480))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSkipIntro
            )
            .testTag("launch_intro_stage"),
        contentAlignment = Alignment.Center
    ) {
        // Animated Kitsune spirit seal + floating embers Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.43f)
            val baseRadius = (minOf(size.width, size.height) * 0.24f).coerceIn(140f, 240f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.34f * emblemAlpha.value),
                        secondaryColor.copy(alpha = 0.14f * emblemAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.15f * pulseScale
                ),
                radius = baseRadius * 2.15f * pulseScale,
                center = center
            )

            // Outer celestial dashed ring
            rotate(degrees = ringRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.7f),
                            tertiaryColor.copy(alpha = 0.45f),
                            primaryColor.copy(alpha = 0.85f)
                        ),
                        center = center
                    ),
                    radius = baseRadius * 1.18f * emblemScale.value,
                    center = center,
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f), 0f)
                    )
                )
            }

            // Counter-rotating inner arcane halo
            rotate(degrees = counterRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            secondaryColor.copy(alpha = 0.65f),
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = baseRadius * 0.94f * emblemScale.value,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }

            // Rising anime spirit embers
            val particleCount = 16
            for (i in 0 until particleCount) {
                val progress = (emberPhase + i / particleCount.toFloat()) % 1f
                val angle = (i * 2.39996f) + progress * 1.4f
                val horizontalDrift = cos(angle.toDouble()).toFloat() * (baseRadius * (0.65f + (i % 4) * 0.28f))
                val verticalRise = center.y + baseRadius * 1.4f - progress * size.height * 0.56f
                val sparkX = center.x + horizontalDrift
                val alpha = sin((progress * PI)).toFloat().coerceIn(0f, 1f) * 0.85f
                val sparkRadius = (3.5f + (i % 3) * 2.5f) * (1f - progress * 0.3f)
                val sparkColor = when (i % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                }
                drawCircle(
                    color = sparkColor.copy(alpha = alpha * emblemAlpha.value),
                    radius = sparkRadius,
                    center = Offset(sparkX, verticalRise)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                        alpha = emblemAlpha.value
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(primaryColor, secondaryColor)
                            )
                        )
                        .padding(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_kitsune_avatar_1795551386599),
                        contentDescription = stringResource(R.string.cd_app_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .width((172 * dividerWidth.value).dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(primaryColor, secondaryColor, tertiaryColor)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                modifier = Modifier.graphicsLayer {
                    alpha = badgeAlpha.value
                }
            ) {
                Text(
                    text = stringResource(R.string.launch_tagline),
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.launch_tap_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f * badgeAlpha.value),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun StartupProfileChooser(
    profiles: List<UserProfile>,
    activeProfile: UserProfile,
    isManaging: Boolean,
    onToggleManage: () -> Unit,
    onSelectAndEnter: (String) -> Unit,
    onEditProfile: (UserProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onContinueActive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("launch_profile_chooser"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.img_kitsune_avatar_1795551386599),
                    contentDescription = stringResource(R.string.cd_app_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            OutlinedButton(
                onClick = onToggleManage,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("launch_manage_profiles_button")
            ) {
                Icon(
                    imageVector = if (isManaging) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isManaging) {
                        stringResource(R.string.launch_done)
                    } else {
                        stringResource(R.string.launch_manage_profiles)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.profile_picker_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isManaging) {
                stringResource(R.string.launch_manage_hint)
            } else {
                stringResource(R.string.profile_picker_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Responsive Crunchyroll-style profile grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        ) {
            itemsIndexed(profiles, key = { _, item -> item.id }) { index, profile ->
                var itemVisible by remember { mutableStateOf(false) }
                LaunchedEffect(profile.id) {
                    delay((index * 55L).coerceAtMost(350L))
                    itemVisible = true
                }

                AnimatedVisibility(
                    visible = itemVisible,
                    enter = fadeIn(tween(340)) +
                        scaleIn(
                            initialScale = 0.86f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) +
                        slideInVertically(tween(340)) { it / 4 }
                ) {
                    LaunchProfileTile(
                        profile = profile,
                        isActive = profile.id == activeProfile.id,
                        isManaging = isManaging,
                        canDelete = profiles.size > 1,
                        onSelect = { onSelectAndEnter(profile.id) },
                        onEdit = { onEditProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) }
                    )
                }
            }

            item(key = "create_new_profile_tile") {
                LaunchCreateProfileTile(onClick = onOpenCreate)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary quick-enter CTA for the currently highlighted profile
        Button(
            onClick = onContinueActive,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(54.dp)
                .testTag("launch_continue_active_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.launch_enter_as, activeProfile.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LaunchProfileTile(
    profile: UserProfile,
    isActive: Boolean,
    isManaging: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        },
        border = BorderStroke(
            width = if (isActive) 1.75.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        ),
        tonalElevation = if (isActive) 6.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable {
                if (isManaging) onEdit() else onSelect()
            }
            .testTag("launch_profile_card_${profile.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = profileAvatarDrawable(profile.avatarKey)),
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isActive) 3.dp else 2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = CircleShape
                        )
                )
                if (isActive && !isManaging) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Perfil activo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isManaging) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("launch_edit_profile_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar ${profile.name}",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("launch_delete_profile_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Eliminar ${profile.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (isActive) "Seleccionado" else "Toca para entrar",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun LaunchCreateProfileTile(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("launch_create_profile_tile")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
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
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.profile_add_new),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.profile_add_new),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Personalizar avatar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LaunchProfileEditorDialog(
    editingProfile: UserProfile?,
    defaultAvatarIndex: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarKey: String) -> Unit
) {
    var draftName by remember(editingProfile) {
        mutableStateOf(editingProfile?.name.orEmpty())
    }
    var draftAvatarKey by remember(editingProfile) {
        mutableStateOf(
            editingProfile?.avatarKey ?: PROFILE_AVATAR_CHOICES[defaultAvatarIndex].key
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("launch_profile_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingProfile != null) {
                            stringResource(R.string.profile_edit_title)
                        } else {
                            stringResource(R.string.profile_create_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                OutlinedTextField(
                    value = draftName,
                    onValueChange = { if (it.length <= 24) draftName = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    placeholder = { Text("Ej. Sakura, Kenji…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launch_profile_name_input")
                )

                Text(
                    text = stringResource(R.string.profile_choose_avatar),
                    style = MaterialTheme.typography.titleSmall
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(PROFILE_AVATAR_CHOICES, key = { it.key }) { choice ->
                        val selected = draftAvatarKey == choice.key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { draftAvatarKey = choice.key }
                                .padding(6.dp)
                                .testTag("launch_avatar_choice_${choice.key}")
                        ) {
                            Box {
                                Image(
                                    painter = painterResource(id = choice.drawableRes),
                                    contentDescription = choice.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                )
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = choice.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = { onSave(draftName, draftAvatarKey) },
                        enabled = draftName.trim().isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("launch_profile_save_button")
                    ) {
                        Text(stringResource(R.string.profile_save))
                    }
                }
            }
        }
    }
}
