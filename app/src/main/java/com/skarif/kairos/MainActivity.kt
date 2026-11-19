package com.skarif.kairos

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.LocalSharedScope
import com.skarif.kairos.ui.components.ProvideNavScope
import com.skarif.kairos.ui.screens.CollectionScreen
import com.skarif.kairos.ui.screens.EqualizerScreen
import com.skarif.kairos.ui.screens.FoldersScreen
import com.skarif.kairos.ui.screens.HomeScreen
import com.skarif.kairos.ui.screens.LibraryScreen
import com.skarif.kairos.ui.screens.LyricsScreen
import com.skarif.kairos.ui.screens.MiniPlayer
import com.skarif.kairos.ui.screens.MenuDrawer
import com.skarif.kairos.ui.screens.NewPlaylistScreen
import com.skarif.kairos.ui.screens.NowPlayingScreen
import com.skarif.kairos.ui.screens.OnboardingScreen
import com.skarif.kairos.ui.screens.PlaylistsScreen
import com.skarif.kairos.ui.screens.QueueScreen
import com.skarif.kairos.ui.screens.SearchScreen
import com.skarif.kairos.ui.screens.SettingsScreen
import com.skarif.kairos.ui.screens.SleepTimerScreen
import com.skarif.kairos.ui.screens.SortScreen
import com.skarif.kairos.ui.screens.StatsScreen
import com.skarif.kairos.ui.screens.hasAudioPermission
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.PlayerTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A first run (or a restore without the music permission) starts on the welcome screen.
        // Read the stored flag before the first frame so a returning user never sees it flash.
        val onboarded = runBlocking { (application as PlayerApp).container.settings.load().onboarded }
        val start = if (onboarded && hasAudioPermission(this)) "home" else "onboarding"
        setContent { PlayerRoot(start) }
    }
}

private val TABS = listOf("home", "library", "stats", "search")

/** Screens whose top edge is dark in every theme. */
private val DARK_TOP = setOf("library", "stats", "now", "collection/{key}")

/** Player screens and sheets rise from the bottom, like the design's sheets. */
private val sheetEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(260))
}
private val sheetExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(220))
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerRoot(start: String, vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val album by vm.albumColors.collectAsStateWithLifecycle()
    PlayerTheme(settings, album) {
        val context = LocalContext.current
        val nav = rememberNavController()
        var menuOpen by remember { mutableStateOf(false) }
        // MediaStore may have changed since last time; a rescan is cheap.
        LaunchedEffect(Unit) { if (hasAudioPermission(context)) vm.scanNow() }
        fun openCollection(key: String) = nav.navigate("collection/" + Uri.encode(key))
        fun openAlbum(id: Long) = openCollection("album:$id")
        val openMenu = { menuOpen = true }

        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route

        // Bar icons must contrast with what is drawn behind them, which differs per screen:
        // Library's header, the Now Playing and album covers, and Stats are dark at the top
        // even in the light theme, so they get white icons.
        val view = LocalView.current
        val darkUi = P.colors.dark
        val darkTop = darkUi || route in DARK_TOP
        val darkBottom = darkUi || route == "stats"
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTop
                    isAppearanceLightNavigationBars = !darkBottom
                }
            }
        }

        SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                nav, startDestination = start,
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 12 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(280)) { it / 12 } },
            ) {
                screen("onboarding") {
                    OnboardingScreen(vm) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
                }
                screen("home", enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) }) {
                    HomeScreen(
                        vm, onMenu = openMenu, onSearch = { nav.tab("search") }, onOpenCollection = ::openCollection,
                        onOpenNowPlaying = { nav.navigate("now") }, onPlaylists = { nav.navigate("playlists") },
                        onLibrary = { nav.tab("library") },
                    )
                }
                screen("library", enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) }) {
                    LibraryScreen(
                        vm, onOpenNowPlaying = { nav.navigate("now") }, onSettings = { nav.navigate("settings") },
                        onFolders = { nav.navigate("folders") }, onSort = { nav.navigate("sort") }, onOpenAlbum = ::openAlbum, onOpenCollection = ::openCollection,
                    )
                }
                screen("stats", enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) }) { StatsScreen(vm, onMenu = openMenu, onSettings = { nav.navigate("settings") }) }
                screen("search", enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) }) {
                    SearchScreen(vm, onMenu = openMenu, onOpenCollection = ::openCollection, onOpenNowPlaying = { nav.navigate("now") })
                }
                screen("now", enterTransition = sheetEnter, popExitTransition = sheetExit) {
                    NowPlayingScreen(
                        vm, onBack = { nav.popBackStack() }, onLyrics = { nav.navigate("lyrics") }, onQueue = { nav.navigate("queue") },
                        onEqualizer = { nav.navigate("eq") }, onSleep = { nav.navigate("sleep") }, onAlbum = ::openAlbum,
                    )
                }
                screen("lyrics", enterTransition = sheetEnter, popExitTransition = sheetExit) { LyricsScreen(vm, onBack = { nav.popBackStack() }) }
                screen("queue", enterTransition = sheetEnter, popExitTransition = sheetExit) {
                    QueueScreen(vm, onBack = { nav.popBackStack() }, onSaveAsPlaylist = { nav.navigate("newplaylist?fromQueue=true") }, onOpenAlbum = ::openAlbum)
                }
                screen("eq", enterTransition = sheetEnter, popExitTransition = sheetExit) { EqualizerScreen(vm, onBack = { nav.popBackStack() }) }
                screen("sleep", enterTransition = sheetEnter, popExitTransition = sheetExit) { SleepTimerScreen(vm, onBack = { nav.popBackStack() }) }
                screen("sort", enterTransition = sheetEnter, popExitTransition = sheetExit) { SortScreen(vm, onBack = { nav.popBackStack() }) }
                screen("playlists") {
                    PlaylistsScreen(vm, onBack = { nav.popBackStack() }, onNew = { nav.navigate("newplaylist?fromQueue=false") }, onOpenCollection = ::openCollection)
                }
                screen("newplaylist?fromQueue={fromQueue}") { e ->
                    val fromQueue = e.arguments?.getString("fromQueue") == "true"
                    val initial = remember { if (fromQueue) vm.now.value.queue.map { it.songId }.filter { it >= 0 } else emptyList() }
                    NewPlaylistScreen(vm, onDone = { nav.popBackStack() }, initialSongs = initial)
                }
                screen("collection/{key}") { e ->
                    val key = Uri.decode(e.arguments?.getString("key") ?: "")
                    CollectionScreen(vm, key, onBack = { nav.popBackStack() }, onOpenNowPlaying = { nav.navigate("now") }, onOpenAlbum = ::openAlbum)
                }
                screen("settings") {
                    SettingsScreen(vm, onBack = { nav.popBackStack() }, onFolders = { nav.navigate("folders") }, onEqualizer = { nav.navigate("eq") })
                }
                screen("folders") { FoldersScreen(vm, onBack = { nav.popBackStack() }) }
            }

            AnimatedVisibility(
                route in TABS,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(),
            ) {
                ProvideNavScope(this) {
                    // Soft fade behind the mini player and dock, so cards scrolling under them
                    // recede instead of clashing with the controls.
                    val bg = P.colors.bg
                    Column(
                        Modifier.drawBehind {
                            val extra = 140.dp.toPx()
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to bg.copy(alpha = 0f), 0.35f to bg.copy(alpha = 0.82f), 1f to bg.copy(alpha = 0.96f),
                                    startY = -60.dp.toPx(), endY = size.height + extra,
                                ),
                                topLeft = androidx.compose.ui.geometry.Offset(-size.width, -60.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width * 3, size.height + 60.dp.toPx() + extra),
                            )
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiniPlayer(vm, onOpen = { nav.navigate("now") })
                        Dock(route = route, onTab = { nav.tab(it) }, onNow = { nav.navigate("now") })
                    }
                }
            }

            AnimatedVisibility(menuOpen, enter = fadeIn() + slideInHorizontally { -it / 3 }, exit = fadeOut() + slideOutHorizontally { -it / 3 }) {
                MenuDrawer(vm, onClose = { menuOpen = false }) { r ->
                    menuOpen = false
                    if (r in TABS) nav.tab(r) else nav.navigate(r.let { if (it == "newplaylist") "newplaylist?fromQueue=false" else it })
                }
            }
            BackHandler(enabled = menuOpen) { menuOpen = false }
        }
        }
        }
    }
}

/** A NavHost destination that also tells shared-card transitions which animation it is in. */
private fun NavGraphBuilder.screen(
    route: String,
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route,
    enterTransition = enterTransition,
    exitTransition = exitTransition,
    popExitTransition = popExitTransition,
) { entry -> ProvideNavScope(this) { content(entry) } }

/** Tab-style navigation: one copy of each tab, Home at the bottom of the stack. */
private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Design reference: the frosted circle dock with the coloured centre button. */
@Composable
private fun Dock(route: String?, onTab: (String) -> Unit, onNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = P.colors
    val idle = if (c.dark) Color(0x1FFFFFFF) else Color(0xB3FFFFFF)
    val dockBg = if (c.dark) Color(0xD93C3C3A) else Color(0xCCC4C7BE)
    Row(
        modifier.clip(RoundedCornerShape(40.dp)).background(dockBg).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun tab(r: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
            val on = route == r
            CircleButton(
                icon, label, { onTab(r) },
                container = if (on) Color.White else idle,
                content = if (on || !c.dark) Color(0xFF151515) else c.text,
            )
        }
        tab("home", Ic.Home, "Home")
        tab("library", Ic.Library, "Library")
        CircleButton(Ic.Play, "Now playing", onNow, size = 60.dp, container = c.accent, content = c.onAccent, iconSize = 22.dp)
        tab("stats", Ic.Stats, "Statistics")
        tab("search", Ic.Search, "Search")
    }
}
