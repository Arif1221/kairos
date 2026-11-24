package com.skarif.kairos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.StatsRange
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.HatchedBars
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.CompactTitleBar
import com.skarif.kairos.ui.components.appear
import com.skarif.kairos.ui.components.stretchTitle
import com.skarif.kairos.ui.motion.rubberBand
import kotlinx.coroutines.launch
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/** Short duration: "45s", "12m", "3h 05m". */
fun hoursText(ms: Long): String {
    val sec = ms / 1000
    val m = sec / 60
    return when {
        sec < 60 -> "${sec}s"
        m < 60 -> "${m}m"
        else -> "${m / 60}h " + "%02dm".format(m % 60)
    }
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n " + if (n == 1) one else many

/** Listening statistics. Follows the theme; the chart card carries the accent. */
@Composable
fun StatsScreen(vm: MainViewModel, onMenu: () -> Unit, onSettings: () -> Unit) {
    val c = P.colors
    val st by vm.stats.collectAsStateWithLifecycle()
    val range by vm.statsRange.collectAsStateWithLifecycle()
    val sel by vm.selectedBar.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recap by vm.recap.collectAsStateWithLifecycle()
    val selected = if (sel in st.bars.indices) sel else st.bars.indices.maxByOrNull { st.bars[it].listenedMs } ?: -1
    val max = (st.bars.maxOfOrNull { it.listenedMs } ?: 0L).coerceAtLeast(1L)

    val scroll = rememberScrollState()
    val band = com.skarif.kairos.ui.motion.rememberRubberBand()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.tabReselected.collect { if (it == "stats") scroll.animateScrollTo(0, com.skarif.kairos.ui.motion.Motion.sheet()) }
    }
    val pastTitle by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { scroll.value > with(density) { 140.dp.toPx() } } }

    ScreenBackground(art, settings.blurBackground) {
        Column(
            Modifier.fillMaxSize().rubberBand(band)
                .verticalScroll(scroll, flingBehavior = com.skarif.kairos.ui.motion.rememberIosFling()).statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Ic.Menu, "Menu", onMenu, container = c.ink, content = c.inkFg)
                Text("Statistics", Modifier.weight(1f), style = T.title, color = c.text, textAlign = TextAlign.Center)
                CircleButton(Ic.Settings, "Settings", onSettings)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hoursText(st.totalMs) + " listened", Modifier.stretchTitle { band.pull }, style = T.big, color = c.text)
                Text(st.change, style = T.body, color = c.muted)
            }
            Column(
                Modifier.appear(1).fillMaxWidth().clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.card)).background(c.accent).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatsRange.entries.forEach { r ->
                        val on = r == range
                        Box(
                            Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (on) Color.White else Color.Transparent)
                                .selectable(on, role = Role.Tab) { vm.setStatsRange(r) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(r.label, style = T.label.copy(fontSize = 14.sp), color = if (on) Color(0xFF151515) else c.onAccent)
                        }
                    }
                }
                HatchedBars(
                    values = st.bars.map { it.listenedMs.toFloat() / max },
                    labels = st.bars.map { it.label },
                    selected = selected,
                    tooltip = st.bars.getOrNull(selected)?.let { hoursText(it.listenedMs) },
                    onSelect = vm::selectBar,
                )
            }
            Row(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Plays", "${st.plays}", Modifier.weight(1f))
                StatTile("Different songs", "${st.uniqueSongs}", Modifier.weight(1f))
            }
            Row(Modifier.appear(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Daily average", hoursText(st.avgPerDayMs), Modifier.weight(1f))
                StatTile("Day streak", plural(st.streakDays, "day"), Modifier.weight(1f))
            }
            Row(Modifier.appear(4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Played to the end", "${st.completedPct}%", Modifier.weight(1f))
                StatTile("Usually listening at", st.peakHour.ifEmpty { "–" }, Modifier.weight(1f))
            }

            Text("Top songs", style = T.h2.copy(fontSize = 24.sp), color = c.text)
            if (st.top.isEmpty()) Text("Nothing played in this period yet.", style = T.body, color = c.muted)
            st.top.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${i + 1}", Modifier.width(18.dp), style = T.title, color = c.muted)
                    SongArt(vm, t.song, 48.dp, corner = 24.dp)
                    Column(Modifier.weight(1f)) {
                        Text(t.song.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(plural(t.plays, "play"), style = T.small, color = c.muted)
                    }
                    Text(hoursText(t.listenedMs), style = T.title.copy(fontSize = 17.sp), color = c.text)
                }
            }

            if (st.topArtists.isNotEmpty()) {
                Text("Top artists", style = T.h2.copy(fontSize = 24.sp), color = c.text)
                val most = st.topArtists.first().second.coerceAtLeast(1L)
                st.topArtists.forEach { (name, ms) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(name, Modifier.weight(1f), style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hoursText(ms), style = T.title, color = c.text)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.card)) {
                            Box(Modifier.fillMaxWidth(ms.toFloat() / most).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.accent))
                        }
                    }
                }
            }
            recap?.let { Box(Modifier.appear(6)) { RecapCard(vm, it) } }
            Text("Stats update while you listen.", style = T.small, color = c.muted)
        }
        CompactTitleBar("Statistics", pastTitle) { scope.launch { scroll.animateScrollTo(0, com.skarif.kairos.ui.motion.Motion.sheet()) } }
    }
}

/** Wrapped-style "your year in music", from your own listening on this phone. */
@Composable
private fun RecapCard(vm: MainViewModel, r: com.skarif.kairos.data.Recap) {
    val c = P.colors
    val month = java.time.Month.of(r.topMonth).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.card)).background(c.ink).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your ${r.year} in music", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.inkFg.copy(alpha = 0.7f))
        Text("${java.text.NumberFormat.getIntegerInstance().format(r.minutes)} minutes", style = T.big.copy(fontSize = 36.sp, lineHeight = 38.sp), color = c.inkFg)
        Text(
            "${plural(r.plays, "play")} of ${plural(r.songs, "song")} by ${plural(r.artists, "artist")}" +
                (if (r.newArtists > 0) ", ${r.newArtists} of them new to you" else "") + ". You listened most in $month.",
            style = T.body, color = c.inkFg.copy(alpha = 0.85f),
        )
        r.topArtists.firstOrNull()?.let { (name, _) -> RecapLine("Top artist", name) }
        r.topGenre?.let { RecapLine("Top genre", it) }
        Text("Top songs", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.inkFg.copy(alpha = 0.7f))
        r.topSongs.forEachIndexed { i, (song, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", Modifier.width(18.dp), style = T.title, color = c.inkFg.copy(alpha = 0.6f))
                SongArt(vm, song, 44.dp, corner = 14.dp)
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = T.title, color = c.inkFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = T.small, color = c.inkFg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(hoursText(ms), style = T.small, color = c.inkFg)
            }
        }
    }
}

@Composable
private fun RecapLine(label: String, value: String) {
    val c = P.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = T.body, color = c.inkFg.copy(alpha = 0.7f))
        Text(value, style = T.title.copy(fontSize = 18.sp), color = c.inkFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val c = P.colors
    Column(modifier.clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item)).background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = T.big.copy(fontSize = 28.sp, lineHeight = 30.sp), color = c.text, maxLines = 1)
    }
}
