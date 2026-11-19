package com.skarif.kairos.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.LyricsPreference
import com.skarif.kairos.data.OutputMode
import com.skarif.kairos.data.ReplayGainMode
import com.skarif.kairos.data.ThemeMode
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.ScanState
import com.skarif.kairos.ui.components.Card
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.RadioCard
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.Segmented
import com.skarif.kairos.ui.components.ToggleRow
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

private val SWATCHES = listOf(
    0xFFBD3657.toInt() to "Raspberry", 0xFF288278.toInt() to "Teal", 0xFF5B85B4.toInt() to "Blue",
    0xFFEF75AD.toInt() to "Pink", 0xFFF8C630.toInt() to "Yellow",
)

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Ic.Back, "Back", onBack)
        Text(title, Modifier.weight(1f), style = T.title, color = P.colors.text, textAlign = TextAlign.Center)
        Box(Modifier.size(52.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onFolders: () -> Unit, onEqualizer: () -> Unit) {
    val c = P.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.addLrcFolder(uri)
    }

    ScreenBackground(art, s.blurBackground) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header("Settings", onBack)
            Text(mixedHeadline("Make it " to false, "yours" to true), style = T.h1, color = c.text)

            Card {
                Text("Theme", style = T.h2, color = c.text)
                Segmented(listOf("Light", "Dark", "System"), s.themeMode.ordinal, { i ->
                    vm.updateSettings { it.copy(themeMode = ThemeMode.entries[i]) }
                })
                Text("Colour", style = T.small, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("From album art", { vm.updateSettings { it.copy(colorFromArt = true) } }, selected = s.colorFromArt, icon = Ic.Palette)
                    SWATCHES.forEach { (argb, name) ->
                        val on = !s.colorFromArt && s.fixedColor == argb
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .border(3.dp, if (on) c.text else Color.Transparent, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .selectable(selected = on, role = Role.RadioButton) {
                                    vm.updateSettings { it.copy(colorFromArt = false, fixedColor = argb) }
                                }
                                .semantics { contentDescription = "Use $name everywhere" },
                        )
                    }
                }
                ToggleRow("Blurred album background", s.blurBackground, { v -> vm.updateSettings { it.copy(blurBackground = v) } })
                ToggleRow("Pure black in dark mode", s.pureBlack, { v -> vm.updateSettings { it.copy(pureBlack = v) } }, "Saves battery on AMOLED screens")
            }

            Card {
                Text("Lyrics", style = T.h2, color = c.text)
                ToggleRow(
                    "Read embedded lyrics", s.readEmbeddedLyrics, { v -> vm.updateSettings { it.copy(readEmbeddedLyrics = v) } },
                    "Synced and plain lyrics inside MP3, FLAC and M4A files",
                )
                ToggleRow(
                    "Use .lrc files", s.readLrcFiles, { v -> vm.updateSettings { it.copy(readLrcFiles = v) } },
                    "Same name as the song, in the same folder",
                )
                Text("When a song has both, use", style = T.small, color = c.muted)
                Segmented(listOf("Embedded", ".lrc file"), s.lyricsPreference.ordinal, { i ->
                    vm.updateSettings { it.copy(lyricsPreference = LyricsPreference.entries[i]) }
                })
                Text(
                    "Android only lets this app see audio files. Allow the folders that hold your .lrc files:",
                    style = T.small, color = c.muted,
                )
                s.lrcFolderUris.forEach { uri ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(readableTree(uri), Modifier.weight(1f), style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CircleButton(Ic.Close, "Remove ${readableTree(uri)}", { vm.removeLrcFolder(uri) }, size = 44.dp, container = c.tint)
                    }
                }
                Pill("Allow a folder", { pickFolder.launch(null) }, icon = Ic.Plus, selected = true)
            }

            Card {
                Text("Audio quality", style = T.h2, color = c.text)
                now.output?.let { Text("Output now: $it", style = T.title, color = c.text) }
                RadioCard(
                    "Hi-res", "32-bit float output, so 24-bit files keep their detail. EQ works.",
                    s.outputMode == OutputMode.HI_RES, { vm.updateSettings { it.copy(outputMode = OutputMode.HI_RES) } },
                )
                RadioCard(
                    "Bit-perfect (USB DAC)",
                    if (Build.VERSION.SDK_INT >= 34) "Sends the file to a USB DAC untouched. Volume, EQ and ReplayGain are turned off."
                    else "Needs Android 14 or newer.",
                    s.outputMode == OutputMode.BIT_PERFECT,
                    { vm.updateSettings { it.copy(outputMode = OutputMode.BIT_PERFECT) } },
                    enabled = Build.VERSION.SDK_INT >= 34,
                )
                RadioCard(
                    "Standard", "16-bit through Android's mixer. Works with every speaker and headphone.",
                    s.outputMode == OutputMode.STANDARD, { vm.updateSettings { it.copy(outputMode = OutputMode.STANDARD) } },
                )
                if (now.outputNeedsRestart) {
                    Text("Close and reopen the app to switch output mode.", style = T.small, color = c.text)
                }
                Text("Volume levelling (ReplayGain)", style = T.small, color = c.muted)
                Segmented(listOf("Off", "Track", "Album"), s.replayGain.ordinal, { i ->
                    vm.updateSettings { it.copy(replayGain = ReplayGainMode.entries[i]) }
                })
            }

            Pill("Music folders", onFolders, icon = Ic.Folder, big = true, modifier = Modifier.fillMaxWidth())
            Pill("Equalizer", onEqualizer, icon = Ic.Sliders, big = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun readableTree(uri: String): String = runCatching {
    "/" + DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':')
}.getOrDefault(uri)

@Composable
fun FoldersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = P.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    LaunchedEffect(s.skipShortFiles) { vm.loadFolders() }
    val total = folders.filter { it.key !in s.excludedFolders }.sumOf { it.count }

    ScreenBackground(art, s.blurBackground) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header("Music folders", onBack)
            Text(mixedHeadline("Choose " to false, "where" to true, " to look for " to false, "music" to true), style = T.h1, color = c.text)
            Card {
                if (folders.isEmpty()) Text("No audio files found on this phone yet.", style = T.body, color = c.muted)
                folders.forEach { f ->
                    ToggleRow(f.label, f.key !in s.excludedFolders, { include ->
                        vm.updateSettings { st ->
                            st.copy(excludedFolders = if (include) st.excludedFolders - f.key else st.excludedFolders + f.key)
                        }
                    }, "${f.count} files")
                }
            }
            Card {
                ToggleRow(
                    "Skip short files", s.skipShortFiles, { v -> vm.updateSettings { it.copy(skipShortFiles = v) } },
                    "Ignore audio under 30 seconds, like ringtones and voice notes",
                )
            }
            Pill(
                if (scan == ScanState.Scanning) "Scanning…" else "Scan $total files",
                vm::scanNow, accent = true, big = true, modifier = Modifier.fillMaxWidth(),
            )
            (scan as? ScanState.Done)?.let { Text("Library updated: ${it.songs} songs.", style = T.body, color = c.text) }
        }
    }
}
