package com.raja.bingehwa.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.raja.bingehwa.BingeApp
import com.raja.bingehwa.ui.screens.CommentsScreen
import com.raja.bingehwa.ui.screens.DetailScreen
import com.raja.bingehwa.ui.screens.DiscoverScreen
import com.raja.bingehwa.ui.screens.LibraryScreen
import com.raja.bingehwa.ui.screens.NotifSettingsScreen
import com.raja.bingehwa.ui.screens.ProfileScreen
import com.raja.bingehwa.ui.screens.ReaderScreen
import com.raja.bingehwa.ui.screens.SourcesScreen
import com.raja.bingehwa.ui.screens.TasteScreen
import com.raja.bingehwa.ui.screens.UpdatesScreen
import com.raja.bingehwa.ui.screens.WelcomeScreen
import com.raja.bingehwa.ui.theme.Palette

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabItems = listOf(
    Tab(Routes.LIBRARY, "Library", Icons.Filled.Home),
    Tab(Routes.UPDATES, "Updates", Icons.Filled.Notifications),
    Tab(Routes.DISCOVER, "Discover", Icons.Filled.Search),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

private const val SLIDE_MS = 340
private const val FADE_MS = 220

// Motion rules from the mockup: tabs fade, deeper screens slide in from the right.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (targetState.destination.route in Routes.tabs) fadeIn(tween(FADE_MS))
    else slideInHorizontally(tween(SLIDE_MS)) { it / 4 } + fadeIn(tween(SLIDE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (targetState.destination.route in Routes.tabs) fadeOut(tween(FADE_MS))
    else slideOutHorizontally(tween(SLIDE_MS)) { -it / 6 } + fadeOut(tween(SLIDE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    if (targetState.destination.route in Routes.tabs) fadeIn(tween(FADE_MS))
    else slideInHorizontally(tween(SLIDE_MS)) { -it / 6 } + fadeIn(tween(SLIDE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_MS)) { it / 4 } + fadeOut(tween(SLIDE_MS))

@Composable
fun BingeHwaApp(nav: NavHostController = rememberNavController()) {
    val container = (LocalContext.current.applicationContext as BingeApp).container
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val start = if (container.settings.current.onboarded) Routes.LIBRARY else Routes.WELCOME

    val isReader = route?.startsWith("reader") == true
    val insets = when {
        isReader -> Modifier
        route in Routes.tabs || route?.startsWith("comments") == true -> Modifier.statusBarsPadding()
        else -> Modifier.statusBarsPadding().navigationBarsPadding()
    }

    val back: () -> Unit = { nav.popBackStack() }
    val openTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (route in Routes.tabs) BottomBar(route, openTab) },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(pad).then(insets),
            enterTransition = { enter() },
            exitTransition = { exit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
        ) {
            composable(Routes.WELCOME) { WelcomeScreen(onStart = { nav.navigate(Routes.TASTE) }) }
            composable(Routes.TASTE) {
                TasteScreen(
                    onDone = { picks ->
                        container.settings.update { it.copy(tastePicks = picks, onboarded = true) }
                        nav.navigate(Routes.LIBRARY) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    },
                    onBack = back,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenSeries = { nav.navigate(Routes.detail(it)) },
                    onOpenChapter = { c, s -> nav.navigate(Routes.reader(c, s)) },
                    onFindSomething = { openTab(Routes.DISCOVER) },
                )
            }
            composable(Routes.UPDATES) {
                UpdatesScreen(
                    onOpenChapter = { c, s -> nav.navigate(Routes.reader(c, s)) },
                    onOpenSeries = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.DISCOVER) { DiscoverScreen(onOpenSeries = { nav.navigate(Routes.detail(it)) }) }
            composable(Routes.PROFILE) {
                ProfileScreen(onAlerts = { nav.navigate(Routes.NOTIF_SETTINGS) }, onSources = { nav.navigate(Routes.SOURCES) })
            }
            composable(Routes.NOTIF_SETTINGS) { NotifSettingsScreen(onBack = back) }
            composable(Routes.SOURCES) { SourcesScreen(onBack = back) }

            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "bingehwa://series/{seriesId}" }),
            ) { e ->
                val id = e.arguments?.getString("seriesId").orEmpty()
                DetailScreen(
                    seriesId = id,
                    onBack = back,
                    onOpenChapter = { c, s -> nav.navigate(Routes.reader(c, s)) },
                    onOpenComments = { nav.navigate(Routes.comments(id, null)) },
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("seriesId") { type = NavType.StringType },
                ),
                deepLinks = listOf(navDeepLink { uriPattern = "bingehwa://reader/{chapterId}/{seriesId}" }),
            ) { e ->
                val chapterId = e.arguments?.getString("chapterId").orEmpty()
                val seriesId = e.arguments?.getString("seriesId").orEmpty()
                ReaderScreen(
                    chapterId = chapterId,
                    seriesId = seriesId,
                    onBack = back,
                    onOpenChapter = { next ->
                        nav.navigate(Routes.reader(next, seriesId)) { popUpTo(Routes.READER) { inclusive = true } }
                    },
                    onOpenComments = { nav.navigate(Routes.comments(seriesId, it)) },
                )
            }
            composable(
                Routes.COMMENTS,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                ),
            ) { e ->
                val seriesId = e.arguments?.getString("seriesId").orEmpty()
                val chapterId = e.arguments?.getString("chapterId").orEmpty()
                CommentsScreen(seriesId, chapterId.takeIf { it != "-" && it.isNotBlank() }, back)
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Palette.Background) {
        tabItems.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.AccentSoft,
                    selectedTextColor = Palette.AccentSoft,
                    indicatorColor = Palette.Accent.copy(alpha = 0.18f),
                    unselectedIconColor = Palette.Muted,
                    unselectedTextColor = Palette.Muted,
                ),
            )
        }
    }
}
