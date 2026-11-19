package com.skarif.kairos.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.ScanState
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/** Design reference: the finance "history" screen. Dark panel with a big number, list below. */
@Composable
fun LibraryScreen(vm: MainViewModel, onOpenNowPlaying: () -> Unit, onSettings: () -> Unit, onFolders: () -> Unit, onSort: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val c = P.colors
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<SongEntity?>(null) }
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val summary = remember(songs) {
        val gb = songs.sumOf { it.sizeBytes } / 1_000_000_000.0
        "${songs.distinctBy { it.albumId }.size} albums, ${songs.distinctBy { it.artist }.size} artists, " +
            String.format("%.1f", gb) + " GB on this phone"
    }

    actionsFor?.let { s -> SongActionsDialog(vm, s, onOpenAlbum) { actionsFor = null } }
    ScreenBackground(art, settings.blurBackground) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                        .background(Color(0xD6121212))
                        .statusBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(52.dp))
                        Text("Library", Modifier.weight(1f), color = Color.White, style = T.title, textAlign = TextAlign.Center)
                        CircleButton(Ic.Settings, "Settings", onSettings, container = Color(0xFF2A2A2A), content = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your collection", color = Color(0xFFCFCFCB), style = T.body)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${songs.size} songs", Modifier.weight(1f), color = Color.White, style = T.big)
                            CircleButton(
                                Ic.Refresh, "Scan again", vm::scanNow, size = 44.dp,
                                container = Color.Transparent, content = Color.White,
                            )
                        }
                        Text(summary, color = Color(0xFFCFCFCB), style = T.small)
                    }
                    Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x99FFFFFF)))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All songs", Modifier.weight(1f), style = T.h2.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = c.text)
                    CircleButton(Ic.Sort, "Sort songs", onSort, container = c.card)
                    Spacer(Modifier.width(8.dp))
                    CircleButton(Ic.Folder, "Music folders", onFolders, container = c.ink, content = c.inkFg)
                }
            }
            if (songs.isEmpty()) {
                item {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (scan == ScanState.Scanning) "Looking for songs…"
                            else "No songs found. Check which folders are included, or copy music to this phone and scan again.",
                            style = T.body, color = c.muted,
                        )
                        if (scan != ScanState.Scanning) Pill("Choose folders", onFolders, selected = true, icon = Ic.Folder)
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, playing = s.id == now.songId, vm, onLongClick = { actionsFor = s }) {
                    vm.play(songs, i)
                    onOpenNowPlaying()
                }
            }
        }
    }
}
