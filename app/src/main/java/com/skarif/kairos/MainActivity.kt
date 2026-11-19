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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
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
import com.skarif.kairos.online.Flavor
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ProvideNavScope
import com.skarif.kairos.ui.screens.CollectionScreen
import com.skarif.kairos.ui.screens.DiscoverScreen
import com.skarif.kairos.ui.screens.FoldersScreen
import com.skarif.kairos.ui.screens.HomeScreen
import com.skarif.kairos.ui.screens.LibraryScreen
import com.skarif.kairos.ui.screens.MenuDrawer
import com.skarif.kairos.ui.screens.NewPlaylistScreen
import com.skarif.kairos.ui.screens.OnboardingScreen
import com.skarif.kairos.ui.screens.PlaylistsScreen
import com.skarif.kairos.ui.screens.SearchScreen
import com.skarif.kairos.ui.screens.SettingsScreen
import com.skarif.kairos.ui.screens.StatsScreen
import com.skarif.kairos.ui.screens.hasAudioPermission
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.PlayerTheme
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.skarif.kairos.ui.motion.Motion
import com.skarif.kairos.ui.player.LocalPlayer
import com.skarif.kairos.ui.player.PlayerSheet
import com.skarif.kairos.ui.player.PlayerPage
import com.skarif.kairos.ui.player.PlayerController
import com.skarif.kairos.ui.screens.SortSheet
import com.skarif.kairos.ui.screens.SleepTimerSheet
import com.skarif.kairos.ui.screens.EqualizerSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A first run (or a restore without the music permission) starts on the welcome screen.
        // Read the stored flag before the first frame so a returning user never sees it flash.
        val onboarded = runBlocking { (application as PlayerApp).container.settings.load().onboarded }
        // Online build: streaming needs no permission, so a user who skipped the library starts on Discover.
        val start = when {
            onboarded && hasAudioPermission(this) -> "home"
            onboarded && Flavor.online != null -> "discover"
            else -> "onboarding"
        }
        setContent { PlayerRoot(start) }
    }
}

private val TABS = listOf("home", "library", "stats", "search", "discover")

/** Screens whose top edge is dark in every theme. */
private val DARK_TOP = setOf("collection/{key}")

/** Bottom sheets opened over whatever is showing, including the player. */
private enum class Tool { SLEEP, EQ, SORT }

private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(Motion.fadeIn()) + scaleIn(tween(Motion.PAGE_MS, easing = Motion.Emphasized), initialScale = 0.97f)
}
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(Motion.fadeOut()) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerRoot(start: String, vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val album by vm.albumColors.collectAsStateWithLifecycle()
    PlayerTheme(settings, album) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val player = remember { PlayerController(scope) }
        var menuOpen by remember { mutableStateOf(false) }
        var tool by remember { mutableStateOf<Tool?>(null) }
        val song by vm.currentSong.collectAsStateWithLifecycle()
        // MediaStore may have changed since last time; a rescan is cheap.
        LaunchedEffect(Unit) { if (hasAudioPermission(context)) vm.scanNow() }
        fun openCollection(key: String) = nav.navigate("collection/" + Uri.encode(key))
        fun openAlbum(id: Long) = openCollection("album:$id")
        val openMenu = { menuOpen = true }
        val openPlayer = { player.expand() }

        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val playerUp by remember { derivedStateOf { player.progress.value > 0.5f } }

        // Bar icons must contrast with what is behind them: the player's cover is dark at the top,
        // album pages too; everything else follows the theme.
        val view = LocalView.current
        val darkUi = P.colors.dark
        val darkTop = if (playerUp) darkUi || player.page == PlayerPage.MAIN else darkUi || route in DARK_TOP
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTop
                    isAppearanceLightNavigationBars = !darkUi
                }
            }
        }

        // Shown once after a crash, so the report can be copied and sent.
        var crash by remember { mutableStateOf(java.io.File(context.filesDir, PlayerApp.CRASH_FILE).takeIf { it.exists() }?.readText()) }
        crash?.let { text ->
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { java.io.File(context.filesDir, PlayerApp.CRASH_FILE).delete(); crash = null },
                containerColor = P.colors.sheet,
                title = { Text("Kairos stopped last time", color = P.colors.text) },
                text = {
                    Text(
                        text.take(1800), color = P.colors.muted,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton({
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        java.io.File(context.filesDir, PlayerApp.CRASH_FILE).delete(); crash = null
                    }) { Text("Copy report", color = P.colors.text) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton({ java.io.File(context.filesDir, PlayerApp.CRASH_FILE).delete(); crash = null }) { Text("Close", color = P.colors.muted) }
                },
            )
        }

        CompositionLocalProvider(LocalPlayer provides player, com.skarif.kairos.ui.components.LocalBackdrop provides true) {
        Box(Modifier.fillMaxSize()) {
            // One shared background behind every page: pages slide over it instead of each
            // drawing (and blurring) its own copy, which is what made transitions heavy.
            val backArt by vm.artwork.collectAsStateWithLifecycle()
            com.skarif.kairos.ui.components.Backdrop(backArt, settings.blurBackground)
            NavHost(
                nav, startDestination = start,
                // Push: the new page slides in from the right, the old one drifts left and fades.
                // Pop is the mirror image. Translation only, so it stays smooth on long lists.
                enterTransition = { slideInHorizontally(tween(Motion.PAGE_MS, easing = Motion.Emphasized)) { it / 5 } + fadeIn(tween(260, easing = Motion.EmphasizedDecelerate)) },
                exitTransition = { slideOutHorizontally(tween(Motion.PAGE_MS, easing = Motion.Emphasized)) { -it / 10 } + fadeOut(tween(200)) },
                popEnterTransition = { slideInHorizontally(tween(Motion.PAGE_MS, easing = Motion.Emphasized)) { -it / 10 } + fadeIn(tween(260, easing = Motion.EmphasizedDecelerate)) },
                popExitTransition = { slideOutHorizontally(tween(320, easing = Motion.EmphasizedAccelerate)) { it / 5 } + fadeOut(tween(200)) },
            ) {
                screen("onboarding") {
                    OnboardingScreen(vm) {
                        val next = if (hasAudioPermission(context) || Flavor.online == null) "home" else "discover"
                        nav.navigate(next) { popUpTo("onboarding") { inclusive = true } }
                    }
                }
                screen("home", enterTransition = tabEnter, exitTransition = tabExit) {
                    HomeScreen(
                        vm, onMenu = openMenu, onSearch = { nav.tab("search") }, onOpenCollection = ::openCollection,
                        onOpenNowPlaying = openPlayer, onPlaylists = { nav.navigate("playlists") },
                        onLibrary = { nav.tab("library") },
                    )
                }
                screen("library", enterTransition = tabEnter, exitTransition = tabExit) {
                    LibraryScreen(
                        vm, onOpenNowPlaying = openPlayer, onSettings = { nav.navigate("settings") },
                        onFolders = { nav.navigate("folders") }, onSort = { tool = Tool.SORT }, onOpenAlbum = ::openAlbum, onOpenCollection = ::openCollection,
                    )
                }
                screen("stats", enterTransition = tabEnter, exitTransition = tabExit) { StatsScreen(vm, onMenu = openMenu, onSettings = { nav.navigate("settings") }) }
                screen("search", enterTransition = tabEnter, exitTransition = tabExit) {
                    SearchScreen(vm, onMenu = openMenu, onOpenCollection = ::openCollection, onOpenNowPlaying = openPlayer)
                }
                screen("discover", enterTransition = tabEnter, exitTransition = tabExit) {
                    DiscoverScreen(vm, onMenu = openMenu, onOpenNowPlaying = openPlayer)
                }
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
                    CollectionScreen(vm, key, onBack = { nav.popBackStack() }, onOpenNowPlaying = openPlayer, onOpenAlbum = ::openAlbum)
                }
                screen("settings") {
                    SettingsScreen(vm, onBack = { nav.popBackStack() }, onFolders = { nav.navigate("folders") }, onEqualizer = { tool = Tool.EQ })
                }
                screen("folders") { FoldersScreen(vm, onBack = { nav.popBackStack() }) }
            }

            // Where the mini player rests: above the dock on tabs, near the bottom edge elsewhere.
            val showPlayer = route != null && route != "onboarding" && song != null
            val density = androidx.compose.ui.platform.LocalDensity.current
            val navBar = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density).toFloat()
            val targetBottom = navBar + with(density) { (if (route in TABS) 12.dp + 76.dp + 10.dp else 12.dp).toPx() }
            val miniBottom by androidx.compose.animation.core.animateFloatAsState(targetBottom, Motion.spatial(), label = "miniBottom")

            // A soft fade from just above the mini player down to the bottom edge, so content
            // scrolling underneath recedes. It follows the mini player as it moves and fades out
            // as the player opens. Drawn under the dock and the player.
            if (showPlayer) {
                val bg = P.colors.bg
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val p = player.progress.value.coerceIn(0f, 1f)
                        if (p >= 1f) return@drawBehind
                        val top = size.height - miniBottom - 64.dp.toPx() - 48.dp.toPx()
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to bg.copy(alpha = 0f), 0.35f to bg.copy(alpha = 0.8f), 1f to bg.copy(alpha = 0.95f),
                                startY = top, endY = size.height,
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
                            alpha = 1f - p,
                        )
                    },
                )
            }

            // Dock. It sinks away as the player opens, tied to the same progress as the player.
            AnimatedVisibility(
                route in TABS,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.spatial()) { it } + fadeIn(Motion.fadeIn()),
                exit = slideOutVertically(Motion.spatial()) { it } + fadeOut(Motion.fadeOut()),
            ) {
                val bg = P.colors.bg
                Column(
                    Modifier
                        .graphicsLayer {
                            val p = player.progress.value
                            translationY = p * size.height
                            alpha = 1f - p
                        }
                        // Soft fade behind the dock, so cards scrolling under it recede. With a song
                        // loaded the player layer draws this fade instead, so it moves with the mini player.
                        .drawBehind {
                            if (song == null) drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to bg.copy(alpha = 0f), 0.3f to bg.copy(alpha = 0.8f), 1f to bg.copy(alpha = 0.95f),
                                    startY = -40.dp.toPx(), endY = size.height,
                                ),
                                topLeft = androidx.compose.ui.geometry.Offset(-size.width, -40.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width * 3, size.height + 40.dp.toPx()),
                            )
                        }
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (song != null) Spacer(Modifier.height(74.dp)) // room for the mini player
                    Dock(route = route, online = vm.onlineName != null, onTab = { nav.tab(it) }, onNow = openPlayer)
                }
            }

            // The player: mini player above the dock, the whole screen when open.
            if (showPlayer) {
                PlayerSheet(
                    vm, player, miniBottom,
                    onAlbum = ::openAlbum,
                    onSleep = { tool = Tool.SLEEP },
                    onEqualizer = { tool = Tool.EQ },
                    onSaveQueue = { nav.navigate("newplaylist?fromQueue=true") },
                )
            }

            AnimatedVisibility(menuOpen, enter = fadeIn(Motion.fadeIn()) + slideInHorizontally(Motion.spatial()) { -it / 3 }, exit = fadeOut(Motion.fadeOut()) + slideOutHorizontally(Motion.spatial()) { -it / 3 }) {
                MenuDrawer(vm, onClose = { menuOpen = false }) { r ->
                    menuOpen = false
                    when (r) {
                        "now" -> player.expand()
                        "queue" -> player.expand(PlayerPage.QUEUE)
                        "eq" -> tool = Tool.EQ
                        "sleep" -> tool = Tool.SLEEP
                        in TABS -> nav.tab(r)
                        "newplaylist" -> nav.navigate("newplaylist?fromQueue=false")
                        else -> nav.navigate(r)
                    }
                }
            }
            BackHandler(enabled = menuOpen) { menuOpen = false }

            when (tool) {
                Tool.SLEEP -> SleepTimerSheet(vm, onBack = { tool = null })
                Tool.EQ -> EqualizerSheet(vm, onBack = { tool = null })
                Tool.SORT -> SortSheet(vm, onBack = { tool = null })
                null -> Unit
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
private fun Dock(route: String?, online: Boolean, onTab: (String) -> Unit, onNow: () -> Unit, modifier: Modifier = Modifier) {
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
        // Online build: Discover takes Stats' place (Stats stays in the menu); six buttons don't fit narrow phones.
        if (online) tab("discover", Ic.Discover, "Discover") else tab("library", Ic.Library, "Library")
        CircleButton(Ic.Play, "Now playing", onNow, size = 60.dp, container = c.accent, content = c.onAccent, iconSize = 22.dp)
        if (online) tab("library", Ic.Library, "Library") else tab("stats", Ic.Stats, "Statistics")
        tab("search", Ic.Search, "Search")
    }
}
