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
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

    val byKey = remember(mixes) { mixes.associateBy { it.key } }
    val hour = remember { java.time.LocalTime.now().hour }
    val part = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "night" }

    ScreenBackground(art, settings.blurBackground) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(top = 12.dp, bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Ic.Menu, "Menu", onMenu, container = c.ink, content = c.inkFg)
                    Box(Modifier.weight(1f))
                    CircleButton(Ic.Search, "Search", onSearch)
                    Box(Modifier.size(8.dp))
                    Pill("Shuffle all", { vm.shuffle(songs); onOpenNowPlaying() }, icon = Ic.Shuffle)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        mixedHeadline("Good " to false, part to true),
                        style = T.h1.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp), color = c.text,
                    )
                    Text(
                        "${java.text.NumberFormat.getIntegerInstance().format(songs.size)} songs and ${playlists.size} playlists on this phone",
                        style = T.body, color = c.muted,
                    )
                }
                // Quick picks: the auto playlists you reach for most, one tap away.
                val picks = listOf("favourites", "most", "recent", "onrepeat", "lastplayed", "never").mapNotNull { byKey[it] }
                picks.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m -> QuickPick(vm, m, Modifier.weight(1f)) { onOpenCollection(m.key) } }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            // Where you left off.
            val album = cont
            val song = cur
            if (album != null && song != null) {
                val idx = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                Row(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().cardClick("Open player", onOpenNowPlaying)
                        .clip(RoundedCornerShape(30.dp)).background(c.tint).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SongArt(vm, song, 64.dp, corner = 20.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Continue", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                        Text(album.title, style = T.title.copy(fontSize = 18.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Song ${idx + 1} of ${album.songs.size}", style = T.small, color = c.muted)
                    }
                    CircleButton(Ic.Play, "Resume", onOpenNowPlaying, container = c.accent, content = c.onAccent)
                }
            }

            // Jump back in: what you played lately.
            val recent = byKey["lastplayed"]?.songs.orEmpty().take(15)
            if (recent.isNotEmpty()) {
                Section("Jump back in")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recent.size, key = { recent[it].id }) { i ->
                        val s2 = recent[i]
                        Column(
                            Modifier.width(140.dp).cardClick("Play ${s2.title}") { vm.play(recent, i); onOpenNowPlaying() },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SongArt(vm, s2, 140.dp, corner = 26.dp)
                            Text(s2.title, style = T.title.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s2.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Your mixes, made from what you play.
            val more = mixes.filter { it.songs.isNotEmpty() && it.key !in setOf("favourites", "lastplayed", "never") }
            if (more.isNotEmpty()) {
                Section("Your mixes")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(more.size, key = { more[it].key }) { i ->
                        val m = more[i]
                        MixCard(vm, m, Modifier.width(300.dp), onOpen = { onOpenCollection(m.key) }, onPlay = { vm.play(m.songs, 0); onOpenNowPlaying() })
                    }
                }
            } else {
                Text("Play a few songs and your mixes will appear here.", Modifier.padding(horizontal = 18.dp), style = T.body, color = c.muted)
            }

            // Fresh in your library: newest albums.
            val fresh = remember(byKey["recent"]) { byKey["recent"]?.songs.orEmpty().distinctBy { it.albumId }.take(12) }
            if (fresh.isNotEmpty()) {
                Section("Fresh in your library")
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(fresh.size, key = { "fa" + fresh[it].albumId }) { i ->
                        val s2 = fresh[i]
                        Column(
                            Modifier.width(120.dp).cardClick("Open ${s2.album}") { onOpenCollection("album:${s2.albumId}") },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SongArt(vm, s2, 120.dp, corner = 60.dp)
                            Text(s2.album, style = T.title.copy(fontSize = 14.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s2.artist, style = T.small.copy(fontSize = 12.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(horizontal = 18.dp), style = T.h2.copy(fontSize = 24.sp), color = P.colors.text)
}

/** Compact two-up tile: cover, name, count. */
@Composable
private fun QuickPick(vm: MainViewModel, m: Collection, modifier: Modifier, onOpen: () -> Unit) {
    val c = P.colors
    val accent = artColor(vm, m.songs.firstOrNull())
    Row(
        modifier.height(64.dp).cardClick("Open ${m.title}", onOpen)
            .clip(RoundedCornerShape(20.dp)).background(tintOf(c.card, accent, c.dark)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (m.songs.isNotEmpty()) SongArt(vm, m.songs.first(), 48.dp, corner = 14.dp)
        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.tint), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Ic.Music, null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(m.title, style = T.title.copy(fontSize = 14.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.songs.size} songs", style = T.small.copy(fontSize = 12.sp), color = c.muted, maxLines = 1)
        }
    }
}

@Composable
private fun MixCard(vm: MainViewModel, m: Collection, modifier: Modifier = Modifier, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = P.colors
    val accent = artColor(vm, m.songs.firstOrNull())
    Row(
        modifier
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
