package com.skarif.kairos.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Online flavor: what you streamed recently, shelves of YouTube Music picks led by the artists you
 * play most, music videos (audio only) and mood chips. Offline, it shows what's in the stream cache.
 */
@Composable
fun DiscoverScreen(vm: MainViewModel, onMenu: () -> Unit, onOpenNowPlaying: () -> Unit) {
    val c = P.colors
    val o = vm.onlineUi
    val state by o.discover.collectAsStateWithLifecycle()
    val mood by o.mood.collectAsStateWithLifecycle()
    val moodSongs by o.moodSongs.collectAsStateWithLifecycle()
    val up by o.networkUp.collectAsStateWithLifecycle()
    val recent by o.recentlyStreamed.collectAsStateWithLifecycle()
    val offline by o.availableOffline.collectAsStateWithLifecycle()
    val update by o.update.collectAsStateWithLifecycle()
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var actionsFor by remember { mutableStateOf<SongEntity?>(null) }
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { o.loadDiscover() }

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
                        CircleButton(Ic.Refresh, "Refresh", { o.loadDiscover(force = true) })
                    }
                    Text(mixedHeadline("Discover\n" to false, "something new" to true), style = T.h1, color = c.text)
                }
            }
            (update as? com.skarif.kairos.ui.UpdateState.Available)?.let { u ->
                item {
                    Row(
                        Modifier.padding(horizontal = 18.dp).clip(RoundedCornerShape(24.dp)).background(c.card).padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Kairos Online ${u.version} is out", style = T.title, color = c.text)
                            Text("Fixes and new features. Installs over this version.", style = T.small, color = c.muted)
                        }
                        Pill("Get it", { uri.openUri(u.url) }, selected = true)
                    }
                }
            }
            if (!up) item {
                Column(
                    Modifier.padding(horizontal = 18.dp).clip(RoundedCornerShape(24.dp)).background(c.card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("You're offline", style = T.title, color = c.text)
                    Text(
                        if (offline.isEmpty()) "Songs you stream are saved on this phone and play here without a connection."
                        else "${offline.size} streamed ${if (offline.size == 1) "song plays" else "songs play"} without a connection. The rest comes back when you're online.",
                        style = T.small, color = c.muted,
                    )
                }
            }
            if (!up && offline.isNotEmpty()) item { ShelfRow("Available offline", offline, vm, { actionsFor = it }, ::play) }
            if (recent.isNotEmpty() && up && mood < 0) item { ShelfRow("Recently streamed", recent, vm, { actionsFor = it }, ::play) }
            if (up) item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("For you", { if (mood >= 0) o.selectMood(mood) }, selected = mood < 0, icon = Ic.Discover)
                    DISCOVER_MOODS.forEachIndexed { i, (label, _) -> Pill(label, { o.selectMood(i) }, selected = mood == i) }
                }
            }
            if (!up) {
                // Nothing to fetch; the banner and offline shelf above are the page.
            } else if (mood >= 0) {
                when (val m = moodSongs) {
                    is OnlineSearch.Ready -> itemsIndexed(m.songs, key = { _, s -> "m${s.id}" }) { i, s ->
                        SongRow(s, s.id == now.songId, vm, onLongClick = { actionsFor = s }) { play(m.songs, i) }
                    }
                    is OnlineSearch.Failed -> item { Status(m.message) }
                    else -> item { Status("Loading ${DISCOVER_MOODS[mood].first}...") }
                }
            } else {
                when (val st = state) {
                    DiscoverState.Loading -> item { Status("Finding music for you...") }
                    is DiscoverState.Failed -> item {
                        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(st.message, style = T.body, color = c.muted)
                            Pill("Try again", { o.loadDiscover(force = true) }, icon = Ic.Refresh)
                        }
                    }
                    is DiscoverState.Ready -> items(st.shelves, key = { it.title }) { shelf ->
                        ShelfRow(shelf.title, shelf.songs, vm, { actionsFor = it }, ::play)
                    }
                }
            }
        }
    }
    actionsFor?.let { s -> SongActionsDialog(vm, s, onOpenAlbum = {}) { actionsFor = null } }
}

@Composable
private fun ShelfRow(title: String, songs: List<SongEntity>, vm: MainViewModel, onMore: (SongEntity) -> Unit, onPlay: (List<SongEntity>, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = T.h2, color = P.colors.text, modifier = Modifier.padding(horizontal = 18.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> ShelfCard(vm, s, onLongClick = { onMore(s) }) { onPlay(songs, i) } }
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(text, style = T.body, color = P.colors.muted, modifier = Modifier.padding(horizontal = 18.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(vm: MainViewModel, song: SongEntity, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = P.colors
    Column(
        Modifier.width(148.dp).clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                role = Role.Button, onClickLabel = "Play ${song.title}", onClick = onClick,
                onLongClickLabel = "More options", onLongClick = onLongClick,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SongArt(vm, song, 148.dp, corner = 24.dp, large = true)
        Text(song.title, style = T.title.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
