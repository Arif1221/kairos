package com.skarif.kairos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.skarif.kairos.data.OutputMode
import com.skarif.kairos.data.SongSort
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.Card
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.HatchedBars
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.PillMeter
import com.skarif.kairos.ui.components.RadioCard
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.ScreenHeader
import com.skarif.kairos.ui.components.Segmented
import com.skarif.kairos.ui.components.ToggleRow
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(vm: MainViewModel, onBack: () -> Unit, onSaveAsPlaylist: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val c = P.colors
    var menuFor by remember { mutableStateOf<Pair<Int, com.skarif.kairos.data.db.SongEntity>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val now by vm.now.collectAsStateWithLifecycle()
    val cur by vm.currentSong.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val left = (now.queue.size - now.queuePosition - 1).coerceAtLeast(0)
    val list = rememberLazyListState()
    val header = 5 // items before the queue rows
    // Open on the current song, and follow it as songs change.
    LaunchedEffect(Unit) { list.scrollToItem((header + now.queuePosition - 1).coerceAtLeast(0)) }
    LaunchedEffect(now.queuePosition) {
        val target = header + now.queuePosition
        val visible = list.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!visible) list.animateScrollToItem((target - 1).coerceAtLeast(0))
    }

    menuFor?.let { (i, song) -> SongActionsDialog(vm, song, onOpenAlbum, queueIndex = i) { menuFor = null } }
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.sheet,
            title = { Text("Clear up next?", style = T.h2, color = c.text) },
            text = { Text("Removes the $left songs after the current one. The current song keeps playing.", style = T.body, color = c.muted) },
            confirmButton = { androidx.compose.material3.TextButton({ vm.clearUpcoming(); confirmClear = false }) { Text("Clear", color = c.text, style = T.title) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirmClear = false }) { Text("Cancel", color = c.muted) } },
        )
    }

    ScreenBackground(art, settings.blurBackground) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(), state = list,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenHeader("Queue", onBack) }
            item { Text(mixedHeadline("Up " to false, "next" to true, ", $left songs to " to false, "go" to true), style = T.h1, color = c.text) }
            item {
                Card(color = c.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SongArt(vm, cur, 64.dp, corner = 20.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cur?.title ?: "Nothing playing", style = T.h2, color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cur?.artist ?: "", style = T.small, color = c.onAccent)
                        }
                        LiveTime(vm, now.durationMs, c.onAccent)
                    }
                    PillMeter(
                        filled = if (now.queue.isEmpty()) 0 else ((now.queuePosition + 1) * 12 / now.queue.size).coerceIn(1, 12),
                        total = 12, color = c.onAccent,
                    )
                    Text("Song ${now.queuePosition + 1} of ${now.queue.size}", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Up next", Modifier.weight(1f), style = T.h2, color = c.text)
                    Pill("Shuffle", vm::toggleShuffle, selected = now.shuffle, icon = Ic.Shuffle)
                    CircleButton(
                        if (now.repeatMode == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat,
                        when (now.repeatMode) { Player.REPEAT_MODE_ALL -> "Repeat all, on"; Player.REPEAT_MODE_ONE -> "Repeat one, on"; else -> "Repeat, off" },
                        vm::cycleRepeat, size = 46.dp,
                        container = if (now.repeatMode != Player.REPEAT_MODE_OFF) c.ink else c.card,
                        content = if (now.repeatMode != Player.REPEAT_MODE_OFF) c.inkFg else c.text,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Pill("Save as playlist", onSaveAsPlaylist, icon = Ic.PlaylistAdd)
                        Pill("Now playing", {
                            scope.launch { list.animateScrollToItem((header + now.queuePosition - 1).coerceAtLeast(0)) }
                        }, icon = Ic.Music)
                        if (left > 0) Pill("Clear up next", { confirmClear = true }, icon = Ic.Trash)
                        if (now.queuePosition > 0) Pill("Clear played", vm::clearPlayed, icon = Ic.Close)
                    }
                    Text(
                        if (now.shuffle) "Swipe left to remove, hold a song for more. Turn shuffle off to reorder."
                        else "Swipe right to play next, left to remove. Hold a song for more.",
                        style = T.small, color = c.muted,
                    )
                }
            }
            itemsIndexed(now.queue, key = { _, e -> e.index }) { pos, e ->
                val s = vm.song(e.songId)
                val playing = pos == now.queuePosition
                val played = pos < now.queuePosition
                Box(Modifier.animateItem()) {
                    SwipeActions(
                        "Play next", Ic.Next, if (playing || now.shuffle) null else ({ vm.moveToNext(e.index) }),
                        "Remove", Ic.Close, if (playing) null else ({ vm.removeQueueItem(e.index) }),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(if (playing) c.accent else c.card.copy(alpha = if (played) 0.45f else 0.85f))
                                .combinedClickable(
                                    role = Role.Button, onClickLabel = "Play", onLongClickLabel = "More options",
                                    onLongClick = { if (s != null) menuFor = e.index to s },
                                ) { vm.playQueueItem(e.index) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SongArt(vm, s, 46.dp, corner = 14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(s?.title ?: "Unknown", style = T.title, color = if (playing) c.onAccent else c.text.copy(alpha = if (played) 0.55f else 1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s?.artist ?: "", style = T.small, color = if (playing) c.onAccent else c.muted, maxLines = 1)
                            }
                            if (!now.shuffle) {
                                val tint = if (playing) c.onAccent else c.text
                                if (e.index > 0) CircleButton(Ic.Up, "Move ${s?.title} up", { vm.moveQueueItem(e.index, e.index - 1) }, size = 40.dp, container = Color.Transparent, content = tint, iconSize = 18.dp)
                                if (e.index < now.queue.size - 1) CircleButton(Ic.DownArrow, "Move ${s?.title} down", { vm.moveQueueItem(e.index, e.index + 1) }, size = 40.dp, container = Color.Transparent, content = tint, iconSize = 18.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val BANDS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private val PRESETS = mapOf(
    "Flat" to List(10) { 0f },
    "Bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
    "Acoustic" to listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 3f, 3f, 2f),
    "Rock" to listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 5f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = P.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    var band by remember { mutableIntStateOf(6) }
    val gains = s.eqGains
    val preset = PRESETS.entries.firstOrNull { it.value == gains }?.key ?: "Custom"
    fun setGain(i: Int, d: Float) = vm.updateSettings { st ->
        st.copy(eqEnabled = true, eqGains = st.eqGains.toMutableList().also { it[i] = (it[i] + d).coerceIn(-12f, 12f) })
    }

    ScreenBackground(art, s.blurBackground) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenHeader("Equalizer", onBack)
            Text(mixedHeadline("Shape " to false, "your" to true, " sound" to false), style = T.h1, color = c.text)
            if (s.outputMode == OutputMode.BIT_PERFECT) {
                Text("Bit-perfect output is on, so the equalizer is bypassed. Switch to Hi-res in Settings to use it.", style = T.body, color = c.text)
            }
            Card { ToggleRow("Equalizer", s.eqEnabled, { v -> vm.updateSettings { it.copy(eqEnabled = v) } }) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (PRESETS.keys + "Custom").forEach { name ->
                    Pill(name, { PRESETS[name]?.let { g -> vm.updateSettings { it.copy(eqEnabled = true, eqGains = g) } } }, selected = name == preset)
                }
            }
            Card(color = c.accent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${BANDS[band]} Hz band", Modifier.weight(1f), style = T.title, color = c.onAccent)
                    CircleButton(Ic.Minus, "Lower ${BANDS[band]} hertz", { setGain(band, -1f) }, size = 44.dp, container = Color.White, content = Color(0xFF151515))
                    CircleButton(Ic.Plus, "Raise ${BANDS[band]} hertz", { setGain(band, 1f) }, size = 44.dp, container = Color.White, content = Color(0xFF151515))
                }
                HatchedBars(
                    values = gains.map { (it + 12f) / 24f },
                    labels = BANDS,
                    selected = band,
                    tooltip = gains[band].let { g -> (if (g > 0) "+" else "") + g.toInt() + " dB" },
                    onSelect = { band = it },
                    chartHeight = 170.dp,
                )
            }
            Card {
                EffectRow("Bass boost", s.bassBoost, { v -> vm.updateSettings { it.copy(bassBoost = v) } })
                EffectRow("Wider sound, for headphones", s.virtualizer, { v -> vm.updateSettings { it.copy(virtualizer = v) } })
            }
        }
    }
}

@Composable
private fun EffectRow(title: String, value: Int, onChange: (Int) -> Unit) {
    val c = P.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.title, color = c.text)
                Text("$value of 10", style = T.small, color = c.muted)
            }
            CircleButton(Ic.Minus, "Less", { onChange((value - 1).coerceAtLeast(0)) }, size = 44.dp, container = c.tint)
            CircleButton(Ic.Plus, "More", { onChange((value + 1).coerceAtMost(10)) }, size = 44.dp, container = c.tint)
        }
        PillMeter(value, 10, c.accent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = P.colors
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val cur by vm.currentSong.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var minutes by remember { mutableIntStateOf(30) }
    var endOfSong by remember { mutableStateOf(false) }
    var fade by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep.active) { while (sleep.active) { nowMs = System.currentTimeMillis(); delay(1000) } }
    val clock = DateFormat.getTimeInstance(DateFormat.SHORT)

    ScreenBackground(art, settings.blurBackground) {
        Box(Modifier.fillMaxSize().background(Color(0x61000000)))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)).background(c.sheet)
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.muted.copy(alpha = 0.5f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mixedHeadline("Sleep " to false, "timer" to true), Modifier.weight(1f), style = T.h1.copy(fontSize = 34.sp), color = c.text)
                CircleButton(Ic.Close, "Close", onBack)
            }
            if (sleep.active) {
                Card(color = c.accent) {
                    val text = if (sleep.endOfSong) "End of song" else formatTime((sleep.endsAt - nowMs).coerceAtLeast(0))
                    Text(text, style = T.big, color = c.onAccent)
                    Text(
                        if (sleep.endOfSong) "Stops when ${cur?.title ?: "this song"} ends" else "Music stops at ${clock.format(Date(sleep.endsAt))}",
                        style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent,
                    )
                }
                Pill("Cancel timer", vm::cancelSleep, selected = true, big = true, modifier = Modifier.fillMaxWidth())
            } else {
                Card(color = c.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(Ic.Minus, "Five minutes less", { minutes = (minutes - 5).coerceAtLeast(5); endOfSong = false }, size = 44.dp, container = Color.White, content = Color(0xFF151515))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (endOfSong) "End of song" else "$minutes min", style = T.big, color = c.onAccent)
                            Text(
                                if (endOfSong) "Stops when ${cur?.title ?: "this song"} ends"
                                else "Music stops at ${clock.format(Date(System.currentTimeMillis() + minutes * 60_000L))}",
                                style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent,
                            )
                        }
                        CircleButton(Ic.Plus, "Five minutes more", { minutes = (minutes + 5).coerceAtMost(180); endOfSong = false }, size = 44.dp, container = Color.White, content = Color(0xFF151515))
                    }
                    PillMeter(if (endOfSong) 12 else (minutes / 5).coerceAtMost(12), 12, c.onAccent)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60).forEach { m -> Pill("$m min", { minutes = m; endOfSong = false }, selected = !endOfSong && minutes == m) }
                    Pill("End of song", { endOfSong = true }, selected = endOfSong)
                }
                ToggleRow("Fade out", fade, { fade = it }, "Lower the volume over the last 30 seconds")
                Pill("Start timer", {
                    if (endOfSong) vm.startSleepEndOfSong(fade) else vm.startSleep(minutes, fade)
                    onBack()
                }, accent = true, big = true, icon = Ic.Clock, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun SortScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = P.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val labels = when (s.songSort) {
        SongSort.DATE_ADDED -> listOf("Newest first", "Oldest first")
        SongSort.LENGTH -> listOf("Shortest first", "Longest first")
        SongSort.MOST_PLAYED -> listOf("Most played first", "Least played first")
        else -> listOf("A to Z", "Z to A")
    }
    // Date added and Most played read naturally newest/most first, so "first" option = descending there.
    val naturalDesc = s.songSort == SongSort.DATE_ADDED || s.songSort == SongSort.MOST_PLAYED
    val selectedIndex = if (s.sortDescending == naturalDesc) 0 else 1

    ScreenBackground(art, s.blurBackground) {
        Box(Modifier.fillMaxSize().background(Color(0x61000000)))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)).background(c.sheet)
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.muted.copy(alpha = 0.5f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mixedHeadline("Sort " to false, "songs" to true), Modifier.weight(1f), style = T.h1.copy(fontSize = 34.sp), color = c.text)
                CircleButton(Ic.Close, "Close", onBack)
            }
            SongSort.entries.forEach { o ->
                RadioCard(o.label, "", s.songSort == o, {
                    vm.updateSettings { it.copy(songSort = o, sortDescending = o == SongSort.DATE_ADDED || o == SongSort.MOST_PLAYED) }
                })
            }
            Segmented(labels, selectedIndex, { i -> vm.updateSettings { it.copy(sortDescending = if (i == 0) naturalDesc else !naturalDesc) } })
            Pill("Show ${songs.size} songs", onBack, selected = true, big = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Slide-in menu with every screen. */
@Composable
fun MenuDrawer(vm: MainViewModel, onClose: () -> Unit, onNavigate: (String) -> Unit) {
    val c = P.colors
    val cur by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val items = listOf(
        Triple("Home", "home", Ic.Home), Triple("Library", "library", Ic.Library), Triple("Playlists", "playlists", Ic.Queue),
        Triple("Search", "search", Ic.Search), Triple("Statistics", "stats", Ic.Stats), Triple("Queue", "queue", Ic.Queue),
        Triple("Equalizer", "eq", Ic.Sliders), Triple(if (sleep.active) "Sleep timer (on)" else "Sleep timer", "sleep", Ic.Clock),
        Triple("Music folders", "folders", Ic.Folder), Triple("Settings", "settings", Ic.Settings),
    ).let { base -> if (vm.onlineName != null) listOf(Triple("Discover", "discover", Ic.Discover)) + base else base }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x61000000)).clickable(onClickLabel = "Close menu", onClick = onClose))
        Column(
            Modifier.fillMaxSize().padding(end = 70.dp).clip(RoundedCornerShape(topEnd = 38.dp, bottomEnd = 38.dp)).background(c.sheet)
                .verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircleButton(Ic.Close, "Close menu", onClose, container = c.ink, content = c.inkFg)
            Text(mixedHeadline("Your " to false, "music" to true), style = T.h1.copy(fontSize = 32.sp), color = c.text)
            if (cur != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.accent)
                        .clickable(role = Role.Button, onClickLabel = "Open now playing") { onNavigate("now") }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SongArt(vm, cur, 48.dp, corner = 15.dp)
                    Column(Modifier.weight(1f)) {
                        Text(cur?.title ?: "", style = T.title, color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(cur?.artist ?: "", style = T.small, color = c.onAccent, maxLines = 1)
                    }
                    CircleButton(if (now.isPlaying) Ic.Pause else Ic.Play, if (now.isPlaying) "Pause" else "Play", vm::toggle, size = 44.dp, container = c.ink, content = c.inkFg)
                }
            }
            items.forEach { (label, route, icon) ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                        .selectable(false, role = Role.Button) { onNavigate(route) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(c.tint), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp))
                    }
                    Text(label, style = T.title.copy(fontSize = 18.sp), color = c.text)
                }
            }
            Box(Modifier.width(1.dp).height(8.dp))
        }
    }
}
