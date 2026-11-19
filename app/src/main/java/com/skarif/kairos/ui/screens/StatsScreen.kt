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
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

fun hoursText(ms: Long): String {
    val m = ms / 60_000
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

/** Design reference: the dark finance statistics screen. Always dark, like the reference. */
@Composable
fun StatsScreen(vm: MainViewModel, onMenu: () -> Unit, onSettings: () -> Unit) {
    val c = P.colors
    val st by vm.stats.collectAsStateWithLifecycle()
    val range by vm.statsRange.collectAsStateWithLifecycle()
    val sel by vm.selectedBar.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val white = Color.White
    val soft = Color(0xFFCFCFCB)
    val selected = if (sel in st.bars.indices) sel else st.bars.indices.maxByOrNull { st.bars[it].listenedMs } ?: -1
    val max = (st.bars.maxOfOrNull { it.listenedMs } ?: 0L).coerceAtLeast(1L)

    Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {
        ScreenBackground(art, settings.blurBackground, Modifier.background(Color(0xFF141414))) {
            Box(Modifier.fillMaxSize().background(Color(0xB3121212)))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Ic.Menu, "Menu", onMenu, container = Color(0xFF2A2A2A), content = white)
                    Text("Statistics", Modifier.weight(1f), style = T.title, color = white, textAlign = TextAlign.Center)
                    CircleButton(Ic.Settings, "Settings", onSettings, container = Color(0xFF2A2A2A), content = white)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hoursText(st.totalMs) + " listened", style = T.big, color = white)
                    Text(st.change, style = T.body, color = soft)
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.accent).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsRange.entries.forEach { r ->
                            val on = r == range
                            Box(
                                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (on) white else Color.Transparent)
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Songs played", "${st.plays}", Modifier.weight(1f))
                    StatTile("Day streak", "${st.streakDays}", Modifier.weight(1f))
                }
                Text("Top songs", style = T.h2.copy(fontSize = 24.sp), color = white)
                if (st.top.isEmpty()) Text("Nothing played in this period yet.", style = T.body, color = soft)
                st.top.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SongArt(vm, t.song, 48.dp, corner = 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.song.title, style = T.title, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.plays} plays", style = T.small, color = soft)
                        }
                        Text(hoursText(t.listenedMs), style = T.title.copy(fontSize = 17.sp), color = white)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF2A2A2A)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = T.small, color = Color(0xFFCFCFCB))
        Text(value, style = T.big.copy(fontSize = 30.sp, lineHeight = 32.sp), color = Color.White)
    }
}
