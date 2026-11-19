package com.skarif.kairos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.SongSort
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.ScanState
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.cardClick
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlinx.coroutines.launch
import java.text.NumberFormat

private enum class LibTab(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), GENRES("Genres") }

/** Items above the first song/album/artist in the grid. */
private const val HEADER_ITEMS = 2

private fun letterOf(s: SongEntity, sort: SongSort): Char {
    val text = when (sort) {
        SongSort.ARTIST -> s.artist
        SongSort.ALBUM -> s.album
        else -> s.title
    }
    val ch = text.trimStart('(', '[', '\'', '"', '.', ' ').firstOrNull()?.uppercaseChar() ?: '#'
    return if (ch in 'A'..'Z') ch else '#'
}

/**
 * Design reference: the finance "history" screen, a dark panel with a big number over a list.
 * Tabs for songs / albums / artists, an A-Z scroller for long lists, and a compact bar that
 * slides in once the big header has scrolled away.
 */
@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpenNowPlaying: () -> Unit,
    onSettings: () -> Unit,
    onFolders: () -> Unit,
    onSort: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val c = P.colors
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibTab.SONGS) }
    var actionsFor by remember { mutableStateOf<SongEntity?>(null) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val fmt = remember { NumberFormat.getIntegerInstance() }

    val albums = remember(songs) {
        songs.groupBy { it.albumId }.values.map { it.first() }.sortedBy { it.album.lowercase() }
    }
    val artists by vm.artists.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val count = when (tab) { LibTab.SONGS -> songs.size; LibTab.ALBUMS -> albums.size; LibTab.ARTISTS -> artists.size; LibTab.GENRES -> genres.size }
    val noun = when (tab) { LibTab.SONGS -> "songs"; LibTab.ALBUMS -> "albums"; LibTab.ARTISTS -> "artists"; LibTab.GENRES -> "genres" }
    val summary = remember(songs) {
        val gb = songs.sumOf { it.sizeBytes } / 1_000_000_000.0
        val mins = songs.sumOf { it.durationMs } / 60_000
        "${mins / 60} hours of music, " + String.format("%.1f", gb) + " GB on this phone"
    }
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    val letterSorted = settings.songSort in setOf(SongSort.TITLE, SongSort.ARTIST, SongSort.ALBUM)
    val letters = remember(songs, settings.songSort) {
        if (!letterSorted) emptyList()
        else songs.mapIndexed { i, s -> letterOf(s, settings.songSort) to i }.distinctBy { it.first }
    }

    actionsFor?.let { s -> SongActionsDialog(vm, s, onOpenAlbum) { actionsFor = null } }

    ScreenBackground(art, settings.blurBackground) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(bottom = 230.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(if (tab == LibTab.ALBUMS || tab == LibTab.GENRES) 10.dp else 0.dp),
        ) {
            item(span = { GridItemSpan(2) }, key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                        .background(Color(0xE6121212))
                        .statusBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(Ic.Folder, "Music folders", onFolders, container = Color(0xFF2A2A2A), content = Color.White)
                        Text("Library", Modifier.weight(1f), color = Color.White, style = T.title, textAlign = TextAlign.Center)
                        CircleButton(Ic.Settings, "Settings", onSettings, container = Color(0xFF2A2A2A), content = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your collection", color = Color(0xFFCFCFCB), style = T.body)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${fmt.format(count)} $noun", Modifier.weight(1f), color = Color.White, style = T.big)
                            CircleButton(Ic.Refresh, "Scan again", vm::scanNow, size = 44.dp, container = Color.Transparent, content = Color.White)
                        }
                        Text(
                            if (scan == ScanState.Scanning) "Scanning your music folders…" else summary,
                            color = Color(0xFFCFCFCB), style = T.small,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF242424)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LibTab.entries.forEach { t ->
                            val on = t == tab
                            Box(
                                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
                                    .background(if (on) Color.White else Color.Transparent)
                                    .selectable(on, role = Role.Tab) {
                                        tab = t
                                        scope.launch { grid.scrollToItem(0) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(t.label, style = T.label.copy(fontSize = 13.sp), maxLines = 1, color = if (on) Color(0xFF151515) else Color.White)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(2) }, key = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("Shuffle", { vm.shuffle(songs); onOpenNowPlaying() }, accent = true, icon = Ic.Shuffle)
                    if (tab == LibTab.SONGS) {
                        Pill(settings.songSort.label, onSort, icon = Ic.Sort)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            when (tab) {
                LibTab.SONGS -> {
                    if (songs.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
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
                    itemsIndexed(songs, key = { _, s -> s.id }, span = { _, _ -> GridItemSpan(2) }, contentType = { _, _ -> "song" }) { i, s ->
                        Box(Modifier.padding(end = if (letters.isNotEmpty()) 22.dp else 0.dp)) {
                            SongRow(s, playing = s.id == now.songId, vm, onLongClick = { actionsFor = s }) {
                                vm.play(songs, i)
                                onOpenNowPlaying()
                            }
                        }
                    }
                }
                LibTab.ALBUMS -> {
                    itemsIndexed(albums, key = { _, s -> "al" + s.albumId }, contentType = { _, _ -> "album" }) { i, s ->
                        Box(Modifier.padding(start = if (i % 2 == 0) 18.dp else 0.dp, end = if (i % 2 == 1) 18.dp else 0.dp)) {
                            CoverTile(vm, s, key = "album:${s.albumId}", kicker = s.artist, title = s.album) { onOpenCollection("album:${s.albumId}") }
                        }
                    }
                }
                LibTab.ARTISTS -> {
                    items(artists, key = { "ar" + it.name.lowercase() }, span = { GridItemSpan(2) }, contentType = { "artist" }) { a ->
                        val name = a.name
                        val list = a.songs
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                                .cardClick("Open $name") { onOpenCollection("artist:$name") }
                                .clip(RoundedCornerShape(24.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SongArt(vm, list.first(), 52.dp, corner = 26.dp)
                            Column(Modifier.weight(1f)) {
                                Text(name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${list.size} songs, ${list.distinctBy { it.albumId }.size} albums", style = T.small, color = c.muted)
                            }
                            CircleButton(Ic.Play, "Play $name", { vm.play(list, 0); onOpenNowPlaying() }, size = 44.dp, container = c.card)
                        }
                    }
                }
                LibTab.GENRES -> {
                    if (genres.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                "No genre tags found in your music. Genres come from the files' tags.",
                                Modifier.padding(18.dp), style = T.body, color = c.muted,
                            )
                        }
                    }
                    itemsIndexed(genres, key = { _, g -> "ge" + g.name.lowercase() }, contentType = { _, _ -> "genre" }) { i, g ->
                        Box(Modifier.padding(start = if (i % 2 == 0) 18.dp else 0.dp, end = if (i % 2 == 1) 18.dp else 0.dp)) {
                            CoverTile(vm, g.songs.first(), key = "genre:${g.name}", kicker = "${g.songs.size} songs", title = g.name) {
                                onOpenCollection("genre:${g.name}")
                            }
                        }
                    }
                }
            }
        }

        // Compact bar once the big header is gone: count, sort, and back to top.
        AnimatedVisibility(
            collapsed,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(260)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xEB121212)).statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = T.title, color = Color.White)
                    Text("${fmt.format(count)} $noun", style = T.small, color = Color(0xFFCFCFCB))
                }
                if (tab == LibTab.SONGS) CircleButton(Ic.Sort, "Sort songs", onSort, size = 44.dp, container = Color(0xFF2A2A2A), content = Color.White)
                CircleButton(Ic.Up, "Back to top", { scope.launch { grid.animateScrollToItem(0) } }, size = 44.dp, container = Color.White, content = Color(0xFF151515))
            }
        }

        if (tab == LibTab.SONGS && letters.size > 3 && songs.size > 40) {
            FastScroller(
                letters = letters.map { it.first },
                onPick = { idx -> scope.launch { grid.scrollToItem(HEADER_ITEMS + letters[idx].second) } },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp, top = 120.dp, bottom = 200.dp),
            )
        }
    }
}

/** A-Z strip: tap or drag to jump; a bubble shows the letter under the finger. */
@Composable
private fun FastScroller(letters: List<Char>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = P.colors
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf(-1) }
    var heightPx by remember { mutableStateOf(1) }
    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i != active) {
            active = i
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPick(i)
        }
    }
    Box(modifier) {
        Column(
            Modifier
                .width(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (active >= 0) c.card.copy(alpha = 0.9f) else Color.Transparent)
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 },
                    ) { change, _ -> change.consume(); pick(change.position.y) }
                }
                .pointerInput(letters) { detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); active = -1 }) }
                .semantics { contentDescription = "Jump to letter" }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { i, l ->
                Text(
                    "$l", style = T.small.copy(fontSize = 10.sp, fontWeight = if (i == active) FontWeight.ExtraBold else FontWeight.SemiBold, lineHeight = 13.sp),
                    color = if (i == active) c.accent else c.muted,
                )
            }
        }
        AnimatedVisibility(
            active >= 0,
            modifier = Modifier.align(Alignment.CenterStart).offset { IntOffset(-90.dp.roundToPx(), 0) },
            enter = scaleIn(tween(120)) + fadeIn(), exit = scaleOut(tween(120)) + fadeOut(),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Text("${letters.getOrElse(active.coerceAtLeast(0)) { ' ' }}", style = T.big.copy(fontSize = 30.sp), color = c.onAccent)
            }
        }
    }
}
