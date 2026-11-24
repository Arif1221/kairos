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
import androidx.compose.ui.text.style.TextAlign
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
import com.skarif.kairos.ui.components.CompactTitleBar
import com.skarif.kairos.ui.components.appear
import com.skarif.kairos.ui.components.stretchTitle
import com.skarif.kairos.ui.motion.rubberBand
import kotlinx.coroutines.launch
import com.skarif.kairos.ui.components.sharedCard
import com.skarif.kairos.ui.components.cardClick
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

/**
 * Home uses the same building blocks as Library and Search: the frosted header panel with round
 * chip buttons, neutral cards, and cover tiles. It used to tint every card with album-art colours
 * and show empty "0 songs" tiles, which made it look like a different app from the other tabs.
 */
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
    val mixesReady by vm.mixesReady.collectAsStateWithLifecycle()
    val cont by vm.continueAlbum.collectAsStateWithLifecycle()
    val cur by vm.currentSong.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    val byKey = remember(mixes) { mixes.associateBy { it.key } }
    val hour = remember { java.time.LocalTime.now().hour }
    val part = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "night" }
    // Same panel and chip colours as the Library header.
    val panel = if (c.dark) androidx.compose.ui.graphics.Color(0xE6121212) else c.card.copy(alpha = 0.92f)
    val chip = if (c.dark) androidx.compose.ui.graphics.Color(0xFF2A2A2A) else c.bg
    val card = c.card.copy(alpha = if (c.dark) 0.85f else 0.92f)

    val scroll = rememberScrollState()
    val band = com.skarif.kairos.ui.motion.rememberRubberBand()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Tapping Home again in the dock springs back to the top.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.tabReselected.collect { if (it == "home") scroll.animateScrollTo(0, com.skarif.kairos.ui.motion.Motion.sheet()) }
    }
    val pastTitle by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { scroll.value > with(density) { 150.dp.toPx() } } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ScreenBackground(art, settings.blurBackground) {
        Column(
            Modifier.fillMaxSize()
                .rubberBand(band)
                .verticalScroll(scroll, flingBehavior = com.skarif.kairos.ui.motion.rememberIosFling())
                .padding(bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                    .background(panel)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Ic.Menu, "Menu", onMenu, container = chip, content = c.text)
                    Text("Home", Modifier.weight(1f), color = c.text, style = T.title, textAlign = TextAlign.Center)
                    CircleButton(Ic.Search, "Search", onSearch, container = chip, content = c.text)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        mixedHeadline("Good " to false, part to true), Modifier.stretchTitle { band.pull },
                        style = T.h1.copy(fontSize = 40.sp, lineHeight = 44.sp), color = c.text,
                    )
                    Text(
                        "${java.text.NumberFormat.getIntegerInstance().format(songs.size)} songs, ${playlists.size} playlists",
                        style = T.small, color = c.muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Shuffle all", { vm.shuffleAll(songs); onOpenNowPlaying() }, accent = true, icon = Ic.Shuffle)
                    Pill("Playlists", onPlaylists, icon = Ic.Queue)
                }
            }

            // Quick picks: only the ones that have songs, so a new install isn't a wall of "0 songs".
            val picks = listOf("favourites", "most", "onrepeat", "recent", "lastplayed", "never")
                .mapNotNull { byKey[it] }.filter { it.songs.isNotEmpty() }.take(6)
            if (picks.isNotEmpty()) {
                Column(Modifier.appear(1).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    picks.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { m -> QuickPick(vm, m, card, Modifier.weight(1f)) { onOpenCollection(m.key) } }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Where you left off.
            val album = cont
            val song = cur
            if (album != null && song != null) {
                val idx = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                Column(
                    Modifier.appear(2).padding(horizontal = 18.dp).fillMaxWidth().cardClick("Open player", onOpenNowPlaying)
                        .clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.card)).background(card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SongArt(vm, song, 80.dp, corner = 22.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Continue listening", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                            Text(song.title, style = T.title.copy(fontSize = 18.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        CircleButton(Ic.Play, "Resume", onOpenNowPlaying, size = 52.dp, container = c.accent, content = c.onAccent)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${album.title}, song ${idx + 1} of ${album.songs.size}", style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PillMeter(idx + 1, album.songs.size.coerceIn(1, 12), c.accent, height = 14.dp)
                    }
                }
            }

            // Jump back in: what you played lately.
            val recent = byKey["lastplayed"]?.songs.orEmpty().take(15)
            if (recent.isNotEmpty()) {
                Section("Jump back in", "See all", 3) { onOpenCollection("lastplayed") }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.appear(4),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recent.size, key = { recent[it].id }, contentType = { "song" }) { i ->
                        val s2 = recent[i]
                        Column(
                            Modifier.width(150.dp).cardClick("Play ${s2.title}") { vm.play(recent, i, "Recently played"); onOpenNowPlaying() },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SongArt(vm, s2, 150.dp, corner = 30.dp)
                            Column(Modifier.padding(horizontal = 4.dp)) {
                                Text(s2.title, style = T.title.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s2.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Your mixes, made from what you play.
            val more = mixes.filter { it.songs.isNotEmpty() && it.key !in setOf("favourites", "lastplayed", "recent", "most") }
            if (more.isNotEmpty()) {
                Section("Made for you", "All", 5) { onPlaylists() }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.appear(6),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(more.size, key = { more[it].key }, contentType = { "mix" }) { i ->
                        val m = more[i]
                        MixCard(vm, m, card, Modifier.width(300.dp), onOpen = { onOpenCollection(m.key) }, onPlay = { vm.play(m.songs, 0, m.title); onOpenNowPlaying() })
                    }
                }
            } else if (mixesReady) {
                Text("Play a few songs and mixes made for you will appear here.", Modifier.padding(horizontal = 18.dp), style = T.body, color = c.muted)
            }

            // Fresh in your library: newest albums, as the same cover tiles Library and Search use.
            val fresh = remember(byKey["recent"]) { byKey["recent"]?.songs.orEmpty().distinctBy { it.albumId }.take(12) }
            if (fresh.isNotEmpty()) {
                Section("Fresh in your library", "Library", 7) { onLibrary() }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.appear(8),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fresh.size, key = { "fa" + fresh[it].albumId }, contentType = { "album" }) { i ->
                        val s2 = fresh[i]
                        Box(Modifier.width(170.dp)) {
                            CoverTile(vm, s2, key = "album:${s2.albumId}", kicker = s2.artist, title = s2.album) { onOpenCollection("album:${s2.albumId}") }
                        }
                    }
                }
            }
        }
        CompactTitleBar("Good $part", pastTitle) { scope.launch { scroll.animateScrollTo(0, com.skarif.kairos.ui.motion.Motion.sheet()) } }
    }
}

@Composable
private fun Section(title: String, action: String? = null, order: Int = 0, onAction: () -> Unit = {}) {
    Row(Modifier.appear(order).fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = T.h2.copy(fontSize = 22.sp), color = P.colors.text)
        if (action != null) {
            Text(
                action,
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
                style = T.small.copy(fontWeight = FontWeight.SemiBold), color = P.colors.muted,
            )
        }
    }
}

/** Compact two-up tile: cover, name, count. */
@Composable
private fun QuickPick(vm: MainViewModel, m: Collection, card: androidx.compose.ui.graphics.Color, modifier: Modifier, onOpen: () -> Unit) {
    val c = P.colors
    Row(
        modifier.height(72.dp).cardClick("Open ${m.title}", onOpen)
            .clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item)).background(card).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SongArt(vm, m.songs.firstOrNull(), 56.dp, corner = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(m.title, style = T.title.copy(fontSize = 15.sp, lineHeight = 18.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${m.songs.size} songs", style = T.small, color = c.muted, maxLines = 1)
        }
    }
}

@Composable
private fun MixCard(vm: MainViewModel, m: Collection, card: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = P.colors
    Row(
        modifier
            .sharedCard("col-${m.key}", RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.card))
            .cardClick("Open ${m.title}", onOpen)
            .clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.card)).background(card).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mosaic(vm, m.songs, Modifier.size(112.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.title, style = T.title.copy(fontSize = 17.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                m.songs.take(3).joinToString(", ") { it.artist }.ifEmpty { m.subtitle },
                style = T.small, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${m.songs.size} songs", Modifier.weight(1f), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                CircleButton(Ic.Play, "Play ${m.title}", onPlay, size = 44.dp, container = c.accent, content = c.onAccent)
            }
        }
    }
}
