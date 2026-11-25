package com.example.ui.navigation

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.ui.components.CloudflareUnlockDialog
import com.example.ui.screens.AnimeDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.viewmodel.AnimeDetailViewModel
import com.example.ui.viewmodel.ExploreViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.JkAnimeViewModelFactory
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.ScheduleViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.model.UserProfile
import com.example.ui.screens.LaunchProfileGateScreen
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ExploreRoute

@Serializable
object ScheduleRoute

@Serializable
object HistoryRoute

@Serializable
data class AnimeDetailRoute(
    val slug: String,
    val providerId: String = AnimeProvider.JKANIME.id
)

@Serializable
data class PlayerRoute(
    val slug: String,
    val episodeNumber: Int,
    val animeTitle: String = "",
    val coverUrl: String = "",
    val providerId: String = AnimeProvider.JKANIME.id,
    val episodeUrl: String = ""
)

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_DESTINATIONS = listOf(
    TopLevelDestination(
        route = HomeRoute,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopLevelDestination(
        route = ExploreRoute,
        labelRes = R.string.nav_explore,
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_explore"
    ),
    TopLevelDestination(
        route = ScheduleRoute,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_schedule"
    ),
    TopLevelDestination(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    )
)

@Composable
fun KitsuneApp(repository: JkAnimeRepository) {
    val defaultProfile = remember { UserProfile("default", "Otaku", "kitsune") }
    val profiles by (repository.profiles ?: flowOf(listOf(defaultProfile)))
        .collectAsStateWithLifecycle(initialValue = listOf(defaultProfile))
    val activeProfile by (repository.activeProfile ?: flowOf(defaultProfile))
        .collectAsStateWithLifecycle(initialValue = defaultProfile)
    var showLaunchGate by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    DisposableEffect(repository) {
        onDispose {
            repository.cloudflareBridge.destroy()
        }
    }

    AnimatedContent(
        targetState = showLaunchGate,
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.96f, animationSpec = tween(420))) togetherWith
                fadeOut(tween(300))
        },
        label = "launch_gate_to_app",
        modifier = Modifier.fillMaxSize()
    ) { inLaunchGate ->
        if (inLaunchGate) {
            LaunchProfileGateScreen(
                profiles = profiles,
                activeProfile = activeProfile,
                onSelectProfile = { profileId ->
                    scope.launch {
                        repository.switchProfile(profileId)
                    }
                },
                onCreateProfile = { name, avatarKey ->
                    scope.launch {
                        repository.createProfile(name, avatarKey)
                    }
                },
                onUpdateProfile = { profileId, name, avatarKey ->
                    scope.launch {
                        repository.updateProfile(profileId, name, avatarKey)
                    }
                },
                onDeleteProfile = { profileId ->
                    scope.launch {
                        repository.deleteProfile(profileId)
                    }
                },
                onEnterApp = {
                    showLaunchGate = false
                }
            )
        } else {
            KitsuneMainShell(repository = repository)
        }
    }
}

@Composable
private fun KitsuneMainShell(repository: JkAnimeRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    var unlockRequest by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    unlockRequest?.let { (targetUrl, onUnlocked) ->
        CloudflareUnlockDialog(
            targetUrl = targetUrl,
            httpClient = repository.httpClient,
            onUnlocked = {
                unlockRequest = null
                onUnlocked()
            },
            onDismiss = { unlockRequest = null }
        )
    }

    val openCloudflareUnlock: (String, () -> Unit) -> Unit = { url, callback ->
        unlockRequest = url to callback
    }

    val bridgeActive by repository.cloudflareBridge.bridgeActive.collectAsStateWithLifecycle()
    val needsInteractive by repository.cloudflareBridge.needsInteractiveChallenge.collectAsStateWithLifecycle()

    val isTopLevel = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<ExploreRoute>() ||
            dest.hasRoute<ScheduleRoute>() ||
            dest.hasRoute<HistoryRoute>()
    } ?: true

    fun navigateTopLevel(target: Any) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    AnimatedVisibility(
                        visible = isTopLevel && !useRail,
                        enter = slideInVertically { it } + fadeIn(tween(220)),
                        exit = slideOutVertically { it } + fadeOut(tween(180))
                    ) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            TOP_DESTINATIONS.forEach { dest ->
                                val selected = when (dest.route) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is ExploreRoute -> currentDest?.hasRoute<ExploreRoute>() == true
                                    is ScheduleRoute -> currentDest?.hasRoute<ScheduleRoute>() == true
                                    is HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedVisibility(
                        visible = isTopLevel && useRail,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180))
                    ) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            TOP_DESTINATIONS.forEach { dest ->
                                val selected = when (dest.route) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is ExploreRoute -> currentDest?.hasRoute<ExploreRoute>() == true
                                    is ScheduleRoute -> currentDest?.hasRoute<ScheduleRoute>() == true
                                    is HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                                    else -> false
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = { fadeIn(tween(240)) },
                        exitTransition = { fadeOut(tween(180)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(180)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<HomeRoute> {
                            val vm: HomeViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(repository)
                            )
                            HomeScreen(
                                viewModel = vm,
                                onOpenAnime = { slug, providerId ->
                                    navController.navigate(AnimeDetailRoute(slug, providerId))
                                },
                                onPlayEpisode = { slug, ep, title, cover, providerId, epUrl ->
                                    navController.navigate(
                                        PlayerRoute(
                                            slug = slug,
                                            episodeNumber = ep,
                                            animeTitle = title,
                                            coverUrl = cover,
                                            providerId = providerId,
                                            episodeUrl = epUrl
                                        )
                                    )
                                },
                                onOpenSearch = { navigateTopLevel(ExploreRoute) },
                                onUnlockCloudflare = openCloudflareUnlock,
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable<ExploreRoute> {
                            val vm: ExploreViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(repository)
                            )
                            ExploreScreen(
                                viewModel = vm,
                                onOpenAnime = { slug, providerId ->
                                    navController.navigate(AnimeDetailRoute(slug, providerId))
                                },
                                onUnlockCloudflare = openCloudflareUnlock,
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable<ScheduleRoute> {
                            val vm: ScheduleViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(repository)
                            )
                            ScheduleScreen(
                                viewModel = vm,
                                onOpenAnime = { slug, providerId ->
                                    navController.navigate(AnimeDetailRoute(slug, providerId))
                                },
                                onUnlockCloudflare = openCloudflareUnlock,
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable<HistoryRoute> {
                            val vm: HistoryViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(repository)
                            )
                            HistoryScreen(
                                viewModel = vm,
                                onPlayEpisode = { slug, ep, title, cover, providerId, epUrl ->
                                    navController.navigate(
                                        PlayerRoute(
                                            slug = slug,
                                            episodeNumber = ep,
                                            animeTitle = title,
                                            coverUrl = cover,
                                            providerId = providerId,
                                            episodeUrl = epUrl
                                        )
                                    )
                                },
                                onOpenAnime = { slug, providerId ->
                                    navController.navigate(AnimeDetailRoute(slug, providerId))
                                },
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable<AnimeDetailRoute> { entry ->
                            val route: AnimeDetailRoute = entry.toRoute()
                            val vm: AnimeDetailViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(
                                    repository = repository,
                                    slug = route.slug,
                                    providerId = route.providerId
                                )
                            )
                            AnimeDetailScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onPlayEpisode = { slug, ep, title, cover, providerId, epUrl ->
                                    navController.navigate(
                                        PlayerRoute(
                                            slug = slug,
                                            episodeNumber = ep,
                                            animeTitle = title,
                                            coverUrl = cover,
                                            providerId = providerId,
                                            episodeUrl = epUrl
                                        )
                                    )
                                },
                                onNavigateToGenreExplore = { navigateTopLevel(ExploreRoute) },
                                onUnlockCloudflare = openCloudflareUnlock
                            )
                        }

                        composable<PlayerRoute> { entry ->
                            val route: PlayerRoute = entry.toRoute()
                            val vm: PlayerViewModel = viewModel(
                                factory = JkAnimeViewModelFactory(
                                    repository = repository,
                                    slug = route.slug,
                                    episodeNumber = route.episodeNumber,
                                    animeTitle = route.animeTitle,
                                    coverUrl = route.coverUrl,
                                    providerId = route.providerId,
                                    episodeUrl = route.episodeUrl
                                )
                            )
                            PlayerScreen(
                                viewModel = vm,
                                okHttpClient = repository.httpClient.okHttpClient,
                                onBack = { navController.popBackStack() },
                                onUnlockCloudflare = openCloudflareUnlock
                            )
                        }
                    }
                }
            }
        }

        // Attached WebView bridge: sits behind Scaffold at realistic size during silent auto-verification;
        // rises above Scaffold via zIndex only if Turnstile requires a tap.
        if (bridgeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (needsInteractive) 2f else 0f)
                    .then(
                        if (needsInteractive) {
                            Modifier
                                .background(Color(0xB8000000))
                                .padding(20.dp)
                        } else {
                            Modifier.alpha(0f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (needsInteractive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Verificando acceso a jkanime.net",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(
                                    onClick = { repository.cloudflareBridge.dismissInteractiveChallenge() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cerrar"
                                    )
                                }
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                repository.cloudflareBridge.obtainOrCreateWebView(ctx) ?: View(ctx)
                            },
                            update = { view ->
                                view.importantForAccessibility = if (needsInteractive) {
                                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                } else {
                                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}
