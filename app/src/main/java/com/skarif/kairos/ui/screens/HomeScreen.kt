package com.skarif.kairos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.ui.Collection
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.PillMeter
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.sharedCard
import com.skarif.kairos.ui.components.cardClick
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

/** Design reference: the automation app (headline, big-number cards, pill meters). */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlaylists: () -> Unit,
    onLibrary: () -> Unit,
) {
    val c = P.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val cont by vm.continueAlbum.collectAsStateWithLifecycle()
    val cur by vm.currentSong.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    ScreenBackground(art, settings.blurBackground) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Ic.Menu, "Menu", onMenu, container = c.ink, content = c.inkFg)
                Box(Modifier.weight(1f))
                CircleButton(Ic.Search, "Search", onSearch)
                Box(Modifier.size(8.dp))
                Pill("Shuffle all", { vm.shuffle(songs) }, icon = Ic.Shuffle, modifier = Modifier.padding(0.dp))
            }
            Text(
                mixedHeadline("Start " to false, "Listening" to true, " in Seconds " to false, "with" to true, " Ready-Made " to false, "Mixes" to true),
                style = T.h1.copy(fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-0.6).sp), color = c.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Songs  ${songs.size}", onLibrary, selected = true)
                Pill("Playlists  ${playlists.size}", onPlaylists)
            }

            val album = cont
            val song = cur
            if (album != null && song != null) {
                val idx = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                Column(
                    Modifier.fillMaxWidth().cardClick("Open now playing", onOpenNowPlaying)
                        .clip(RoundedCornerShape(34.dp)).background(c.tint).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SongArt(vm, song, 68.dp, corner = 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text(album.title, style = T.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = T.body, color = c.muted, maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${idx + 1}", style = T.big.copy(fontSize = 60.sp, lineHeight = 60.sp), color = c.text)
                        Text("  of ${album.songs.size} songs", Modifier.padding(bottom = 8.dp).weight(1f), style = T.body, color = c.muted)
                        Pill("Resume", onOpenNowPlaying, icon = Ic.Play)
                    }
                    PillMeter(idx + 1, album.songs.size.coerceAtMost(12).coerceAtLeast(1), c.accent, height = 34.dp)
                }
            }

            mixes.take(4).filter { it.songs.isNotEmpty() }.forEach { m ->
                MixCard(vm, m, onOpen = { onOpenCollection(m.key) }, onPlay = { vm.play(m.songs, 0); onOpenNowPlaying() })
            }
            if (mixes.take(4).all { it.songs.isEmpty() }) {
                Text("Play a few songs and your mixes will appear here.", style = T.body, color = c.muted)
            }
        }
    }
}

@Composable
private fun MixCard(vm: MainViewModel, m: Collection, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = P.colors
    val accent = artColor(vm, m.songs.firstOrNull())
    Row(
        Modifier.fillMaxWidth()
            .sharedCard("col-${m.key}", RoundedCornerShape(34.dp))
            .cardClick("Open ${m.title}", onOpen)
            .clip(RoundedCornerShape(34.dp)).background(tintOf(c.card, accent, c.dark)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                SongArt(vm, m.songs.getOrNull(0), 46.dp, corner = 23.dp)
            }
            Box(Modifier.offset(y = (-14).dp).size(68.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                SongArt(vm, m.songs.getOrNull(1) ?: m.songs.getOrNull(0), 46.dp, corner = 23.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(m.title, style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Text(
                m.songs.take(3).joinToString(", ") { it.title },
                style = T.small, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).border(1.5.dp, c.muted.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${m.songs.size}", style = T.small.copy(fontWeight = FontWeight.Bold), color = c.text)
                }
                Pill("Play", onPlay, icon = Ic.Play)
            }
        }
    }
}
