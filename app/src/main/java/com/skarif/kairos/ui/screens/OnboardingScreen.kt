package com.skarif.kairos.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.ScanState
import com.skarif.kairos.ui.components.Card
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val c = P.colors
    val context = LocalContext.current
    val scan by vm.scan.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    var step by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[audioPermission] == true || hasAudioPermission(context)) {
            denied = false
            vm.scanNow()
        } else {
            denied = true
        }
    }

    ScreenBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (scan == ScanState.Idle && step == 0) {
                // Welcome: what the app does, before asking for anything.
                Spacer(Modifier.weight(0.4f))
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Ic.Music, null, tint = c.onAccent, modifier = Modifier.size(34.dp))
                }
                Text(mixedHeadline("Welcome to " to false, "Kairos" to true), style = T.h1.copy(fontSize = 44.sp, lineHeight = 47.sp), color = c.text)
                Text("An offline player for the music already on your phone.", style = T.body.copy(fontSize = 17.sp, lineHeight = 24.sp), color = c.muted)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WelcomeRow(Ic.Lyrics, "Synced lyrics", "Read from your files' tags or .lrc files")
                    WelcomeRow(Ic.Sliders, "Hi-res and bit-perfect output", "With a 10-band equalizer")
                    WelcomeRow(Ic.Shuffle, "Mixes that build themselves", "From what you play and what you've added")
                }
                Spacer(Modifier.weight(1f))
                Pill("Get started", { step = 1 }, selected = true, big = true, modifier = Modifier.fillMaxWidth())
            } else if (scan == ScanState.Idle) {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(230.dp).border(2.dp, c.line, CircleShape))
                    Box(Modifier.size(160.dp).border(2.dp, c.line, CircleShape))
                    Box(Modifier.size(96.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Ic.Music, null, tint = c.onAccent, modifier = Modifier.size(36.dp))
                    }
                }
                Text(mixedHeadline("All " to false, "your music" to true, ", right on " to false, "this phone" to true), style = T.h1, color = c.text)
                Text(
                    "To build your library the app needs to read the audio files stored here. Nothing is uploaded and nothing leaves your phone.",
                    style = T.body.copy(fontSize = 16.sp, lineHeight = 23.sp), color = c.muted,
                )
                if (denied) {
                    Text(
                        "Access was not allowed. You can allow it in Android Settings > Apps > Kairos > Permissions.",
                        style = T.body, color = c.text,
                    )
                }
                Pill("Allow access to music", {
                    val perms = buildList {
                        add(audioPermission)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    request.launch(perms.toTypedArray())
                }, selected = true, big = true, modifier = Modifier.fillMaxWidth())
            } else {
                Text(mixedHeadline("Finding " to false, "your" to true, " songs" to false), style = T.h1, color = c.text)
                Card(color = c.accent) {
                    Text("Songs found", style = T.small, color = c.onAccent)
                    Text(
                        when (val st = scan) {
                            is ScanState.Done -> "${st.songs}"
                            else -> "…"
                        },
                        style = T.big.copy(fontSize = 72.sp, lineHeight = 74.sp), color = c.onAccent,
                    )
                    Text(
                        if (scan == ScanState.Scanning) "Reading your music folders" else "Lyrics are read when you open a song.",
                        style = T.small, color = c.onAccent,
                    )
                }
                if (scan is ScanState.Done) {
                    Pill("Open my library", {
                        vm.updateSettings { it.copy(onboarded = true) }
                        onDone()
                    }, selected = true, big = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun WelcomeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    val c = P.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item)).background(c.card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = T.title, color = c.text)
            Text(sub, style = T.small, color = c.muted)
        }
    }
}
