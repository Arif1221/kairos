package com.skarif.kairos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.screens.FoldersScreen
import com.skarif.kairos.ui.screens.LibraryScreen
import com.skarif.kairos.ui.screens.LyricsScreen
import com.skarif.kairos.ui.screens.NowPlayingScreen
import com.skarif.kairos.ui.screens.OnboardingScreen
import com.skarif.kairos.ui.screens.SettingsScreen
import com.skarif.kairos.ui.screens.hasAudioPermission
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.PlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlayerRoot() }
    }
}

private object R0 {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val NOW = "now"
    const val LYRICS = "lyrics"
    const val SETTINGS = "settings"
    const val FOLDERS = "folders"
}

@Composable
private fun PlayerRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val album by vm.albumColors.collectAsStateWithLifecycle()
    PlayerTheme(settings, album) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val start = remember { if (hasAudioPermission(context)) R0.LIBRARY else R0.ONBOARDING }
        // MediaStore may have changed since last time; a rescan is cheap.
        LaunchedEffect(Unit) { if (hasAudioPermission(context)) vm.scanNow() }
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route

        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = start) {
                composable(R0.ONBOARDING) {
                    OnboardingScreen(vm) { nav.navigate(R0.LIBRARY) { popUpTo(R0.ONBOARDING) { inclusive = true } } }
                }
                composable(R0.LIBRARY) {
                    LibraryScreen(vm, onOpenNowPlaying = { nav.navigate(R0.NOW) }, onSettings = { nav.go(R0.SETTINGS) }, onFolders = { nav.navigate(R0.FOLDERS) })
                }
                composable(R0.NOW) { NowPlayingScreen(vm, onBack = { nav.popBackStack() }, onLyrics = { nav.navigate(R0.LYRICS) }) }
                composable(R0.LYRICS) { LyricsScreen(vm, onBack = { nav.popBackStack() }) }
                composable(R0.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }, onFolders = { nav.navigate(R0.FOLDERS) }) }
                composable(R0.FOLDERS) { FoldersScreen(vm, onBack = { nav.popBackStack() }) }
            }
            if (route == R0.LIBRARY || route == R0.SETTINGS) {
                Dock(
                    route = route,
                    onLibrary = { nav.go(R0.LIBRARY) },
                    onNow = { nav.navigate(R0.NOW) },
                    onSettings = { nav.go(R0.SETTINGS) },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                )
            }
        }
    }
}

/** Tab-style navigation: one copy of each tab, Library at the bottom of the stack. */
private fun NavHostController.go(route: String) = navigate(route) {
    popUpTo(R0.LIBRARY)
    launchSingleTop = true
}

/** Design reference: the frosted circle dock with the coloured centre button. */
@Composable
private fun Dock(route: String?, onLibrary: () -> Unit, onNow: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = P.colors
    val idle = if (c.dark) Color(0x1FFFFFFF) else Color(0xB3FFFFFF)
    val dockBg = if (c.dark) Color(0xD93C3C3A) else Color(0xCCC4C7BE)
    Row(
        modifier.clip(RoundedCornerShape(40.dp)).background(dockBg).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val libOn = route == R0.LIBRARY
        CircleButton(Ic.Home, "Library", onLibrary, container = if (libOn) Color.White else idle, content = Color(0xFF151515).takeIf { libOn || !c.dark } ?: c.text)
        CircleButton(Ic.Play, "Now playing", onNow, size = 60.dp, container = c.accent, content = c.onAccent, iconSize = 22.dp)
        val setOn = route == R0.SETTINGS
        CircleButton(Ic.Settings, "Settings", onSettings, container = if (setOn) Color.White else idle, content = Color(0xFF151515).takeIf { setOn || !c.dark } ?: c.text)
    }
}
