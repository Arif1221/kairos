package com.skarif.kairos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.DISCOVER_MOODS
import com.skarif.kairos.ui.DiscoverState
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.OnlineSearch
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

/** Online flavor: shelves of YouTube Music picks, led by the artists you play most, plus mood chips. */
@Composable
fun DiscoverScreen(vm: MainViewModel, onMenu: () -> Unit, onOpenNowPlaying: () -> Unit) {
    val c = P.colors
    val state by vm.discover.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val moodSongs by vm.moodSongs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadDiscover() }

    fun play(list: List<SongEntity>, i: Int) {
        vm.play(list, i)
        onOpenNowPlaying()
    }

    ScreenBackground(art, settings.blurBackground) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircleButton(Ic.Menu, "Menu", onMenu, container = c.ink, content = c.inkFg)
                        Box(Modifier.weight(1f))
                        CircleButton(Ic.Refresh, "Refresh", { vm.loadDiscover(force = true) })
                    }
                    Text(mixedHeadline("Discover\n" to false, "something new" to true), style = T.h1, color = c.text)
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("For you", { if (mood >= 0) vm.selectMood(mood) }, selected = mood < 0, icon = Ic.Discover)
                    DISCOVER_MOODS.forEachIndexed { i, (label, _) -> Pill(label, { vm.selectMood(i) }, selected = mood == i) }
                }
            }
            if (mood >= 0) {
                when (val m = moodSongs) {
                    is OnlineSearch.Ready -> itemsIndexed(m.songs, key = { _, s -> "m${s.id}" }) { i, s ->
                        SongRow(s, s.id == now.songId, vm) { play(m.songs, i) }
                    }
                    is OnlineSearch.Failed -> item { Status(m.message) }
                    else -> item { Status("Loading ${DISCOVER_MOODS[mood].first}...") }
                }
            } else {
                when (val st = state) {
                    DiscoverState.Loading -> item { Status("Finding music for you...") }
                    is DiscoverState.Failed -> item { Status(st.message) }
                    is DiscoverState.Ready -> items(st.shelves, key = { it.title }) { shelf ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(shelf.title, style = T.h2, color = c.text, modifier = Modifier.padding(horizontal = 18.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(shelf.songs, key = { _, s -> s.id }) { i, s -> ShelfCard(vm, s) { play(shelf.songs, i) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(text, style = T.body, color = P.colors.muted, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun ShelfCard(vm: MainViewModel, song: SongEntity, onClick: () -> Unit) {
    val c = P.colors
    Column(
        Modifier.width(148.dp).clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "Play ${song.title}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SongArt(vm, song, 148.dp, corner = 24.dp, large = true)
        Text(song.title, style = T.title.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
