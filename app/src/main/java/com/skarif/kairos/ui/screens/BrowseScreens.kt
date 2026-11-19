package com.skarif.kairos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.OnlineSearch
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.ScreenHeader
import com.skarif.kairos.ui.components.sharedCard
import com.skarif.kairos.ui.components.cardClick
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import com.skarif.kairos.ui.theme.mixedHeadline

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = P.colors
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(c.card).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = T.body.copy(fontSize = 16.sp), color = c.muted)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                singleLine = true, textStyle = T.body.copy(fontSize = 16.sp, color = c.text), cursorBrush = SolidColor(c.accent),
            )
        }
        if (value.isNotEmpty()) CircleButton(Ic.Close, "Clear", { onChange("") }, size = 44.dp, container = c.tint)
        else Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Ic.Search, null, tint = c.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Design reference: the travel "select destination" screen: chips and a photo grid. */
@Composable
fun SearchScreen(vm: MainViewModel, onMenu: () -> Unit, onOpenCollection: (String) -> Unit, onOpenNowPlaying: () -> Unit) {
    val c = P.colors
    val q by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val online by vm.onlineResults.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val albums = remember(songs) {
        songs.groupBy { it.albumId }.values.sortedByDescending { it.size }.take(60).map { it.first() }
    }
    val artistList by vm.artists.collectAsStateWithLifecycle()
    val artists = remember(artistList) { artistList.sortedByDescending { it.songs.size }.take(60) }

    ScreenBackground(art, settings.blurBackground) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 230.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircleButton(Ic.Menu, "Menu", onMenu, container = c.ink, content = c.inkFg)
                    Text(mixedHeadline("Explore\n" to false, "your music" to true), style = T.h1, color = c.text)
                    SearchField(q, vm::setQuery, if (vm.onlineName != null) "Your music and ${vm.onlineName}" else "Songs, artists, albums")
                    if (q.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Albums", { tab = 0 }, selected = tab == 0, icon = Ic.Music)
                            Pill("Artists", { tab = 1 }, selected = tab == 1, icon = Ic.Home)
                        }
                    }
                }
            }
            if (q.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(if (results.isEmpty()) "No songs match \"$q\"." else "${results.size} songs", style = T.small, color = c.muted)
                }
                items(results, key = { it.id }, span = { GridItemSpan(2) }) { s ->
                    SongRow(s, s.id == now.songId, vm) {
                        vm.play(results, results.indexOf(s))
                        onOpenNowPlaying()
                    }
                }
                if (vm.onlineName != null) {
                    item(span = { GridItemSpan(2) }) {
                        val status = when (val o = online) {
                            OnlineSearch.Idle, OnlineSearch.Loading -> "Searching ${vm.onlineName}..."
                            is OnlineSearch.Failed -> o.message
                            is OnlineSearch.Ready -> if (o.songs.isEmpty()) "Nothing on ${vm.onlineName}." else "From ${vm.onlineName}"
                        }
                        Text(status, style = T.small, color = c.muted, modifier = Modifier.padding(top = 12.dp))
                    }
                    val streamed = (online as? OnlineSearch.Ready)?.songs.orEmpty()
                    items(streamed, key = { "o${it.id}" }, span = { GridItemSpan(2) }) { s ->
                        SongRow(s, s.id == now.songId, vm) {
                            vm.play(streamed, streamed.indexOf(s))
                            onOpenNowPlaying()
                        }
                    }
                }
            } else {
                // (cover song, collection key, kicker, title)
                val list = if (tab == 0) albums.map { Quad(it, "album:${it.albumId}", it.artist, it.album) }
                else artists.map { Quad(it.songs.first(), "artist:${it.name}", "${it.songs.size} songs", it.name) }
                items(list, key = { it.key }) { t ->
                    CoverTile(vm, t.song, key = t.key, kicker = t.kicker, title = t.title) { onOpenCollection(t.key) }
                }
            }
        }
    }
}

/** Cover card that grows into the album/artist page when tapped. */
@Composable
fun CoverTile(vm: MainViewModel, song: SongEntity, key: String, kicker: String, title: String, onClick: () -> Unit) {
    val img = rememberCover(vm, song)
    val c = P.colors
    Box(
        Modifier.fillMaxWidth().height(214.dp)
            .sharedCard("col-$key", RoundedCornerShape(30.dp))
            .cardClick("Open $title", onClick)
            .clip(RoundedCornerShape(30.dp)).background(c.tint),
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(kicker.uppercase(), style = T.small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = T.title.copy(fontSize = 17.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PlaylistsScreen(vm: MainViewModel, onBack: () -> Unit, onNew: () -> Unit, onOpenCollection: (String) -> Unit) {
    val c = P.colors
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var smart by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    data class Tile(val key: String, val name: String, val sub: String, val songs: List<SongEntity>)
    val tiles = if (smart) mixes.map { Tile(it.key, it.title, "Smart, ${it.songs.size} songs", it.songs) }
    else playlists.map { Tile("playlist:${it.id}", it.name, "${it.songs.size} songs", it.songs) }

    ScreenBackground(art, settings.blurBackground) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(Ic.Back, "Back", onBack)
                        Box(Modifier.weight(1f))
                        Pill("New playlist", onNew, icon = Ic.Plus)
                    }
                    Text(mixedHeadline("Your " to false, "playlists" to true, ", made " to false, "your way" to true), style = T.h1, color = c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Made by you  ${playlists.size}", { smart = false }, selected = !smart)
                        Pill("Smart  ${mixes.size}", { smart = true }, selected = smart)
                    }
                    if (tiles.isEmpty()) Text("No playlists yet. Tap New playlist to make one.", style = T.body, color = c.muted)
                }
            }
            items(tiles, key = { it.key }) { t ->
                Column(
                    Modifier.sharedCard("col-${t.key}", RoundedCornerShape(30.dp))
                        .cardClick("Open ${t.name}") { onOpenCollection(t.key) }
                        .clip(RoundedCornerShape(30.dp)).background(c.card)
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Mosaic(vm, t.songs, Modifier.fillMaxWidth().aspectRatio(1f))
                    Column(Modifier.padding(horizontal = 6.dp)) {
                        Text(t.name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.sub, style = T.small, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun Mosaic(vm: MainViewModel, songs: List<SongEntity>, modifier: Modifier) {
    val c = P.colors
    val four = songs.distinctBy { it.albumId }.take(4)
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(c.tint), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0..1) {
                    val s = four.getOrNull(row * 2 + col)
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        if (s != null) {
                            val img = rememberCover(vm, s)
                            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewPlaylistScreen(vm: MainViewModel, onDone: () -> Unit, initialSongs: List<Long> = emptyList()) {
    val c = P.colors
    val songs by vm.songs.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var find by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>().apply { addAll(initialSongs) } }
    val shown = remember(songs, find) {
        val t = find.trim().lowercase()
        if (t.isEmpty()) songs.take(300) else songs.filter { it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) }.take(300)
    }
    val pickedSongs = picked.mapNotNull { vm.song(it) }

    ScreenBackground(art, settings.blurBackground) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeader("New playlist", onDone)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Mosaic(vm, pickedSongs, Modifier.size(104.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name", style = T.small, color = c.muted)
                    SearchFieldPlain(name, { name = it }, "Late drives")
                }
            }
            SearchField(find, { find = it }, "Find songs to add")
            LazyColumn(Modifier.weight(1f).clip(RoundedCornerShape(30.dp)).background(c.card.copy(alpha = 0.7f))) {
                items(shown, key = { it.id }) { s ->
                    val on = s.id in picked
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { if (on) picked.remove(s.id) else picked.add(s.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SongArt(vm, s, 46.dp, corner = 14.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = T.small, color = c.muted, maxLines = 1)
                        }
                        Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.muted))
                    }
                }
            }
            Pill(
                if (picked.isEmpty()) "Pick some songs" else "Create with ${picked.size} songs",
                { if (picked.isNotEmpty()) { vm.createPlaylist(name, picked.toList()); onDone() } },
                accent = true, big = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchFieldPlain(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = P.colors
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.card).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = T.body.copy(fontSize = 16.sp), color = c.muted)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = "Playlist name" }, singleLine = true,
            textStyle = T.body.copy(fontSize = 16.sp, color = c.text), cursorBrush = SolidColor(c.accent))
    }
}

/** Album, playlist, artist and mix screen. Design reference: the hotel screen hero. */
@Composable
fun CollectionScreen(vm: MainViewModel, key: String, onBack: () -> Unit, onOpenNowPlaying: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val c = P.colors
    // Re-read when the library, playlists or mixes change.
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val col = remember(key, songs, playlists, mixes) { vm.collection(key) }
    var actionsFor by remember { mutableStateOf<SongEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val first = col?.songs?.firstOrNull()
    val cover = rememberCover(vm, first)
    val isAlbum = key.startsWith("album:")

    actionsFor?.let { s -> SongActionsDialog(vm, s, onOpenAlbum) { actionsFor = null } }
    val pid = col?.playlistId
    val colTitle = col?.title ?: ""
    if (confirmDelete && pid != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.sheet,
            title = { Text("Delete $colTitle?", style = T.h2, color = c.text) },
            text = { Text("The songs stay on your phone. Only the playlist is removed.", style = T.body, color = c.muted) },
            confirmButton = { Pill("Delete", { vm.deletePlaylist(pid); confirmDelete = false; onBack() }, selected = true) },
            dismissButton = { Pill("Keep", { confirmDelete = false }) },
        )
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(380.dp)
                            .sharedCard("col-$key", RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp))
                            .clip(RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp)).background(c.accent),
                    ) {
                        if (cover != null) {
                            if (isAlbum) Image(cover, "${col?.title} cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Mosaic(vm, col?.songs.orEmpty(), Modifier.fillMaxSize())
                        }
                        Box(Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                            CircleButton(Ic.Back, "Back", onBack, container = Color(0xE6FFFFFF), content = Color(0xFF151515))
                            Box(Modifier.weight(1f))
                            if (pid != null) {
                                CircleButton(Ic.Trash, "Delete playlist", { confirmDelete = true }, container = Color(0x38FFFFFF), content = Color.White)
                            }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 110.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isAlbum && first != null) Text(first.artist, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                            Text(col?.title ?: "Not found", style = T.big.copy(fontSize = 38.sp, lineHeight = 40.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(col?.subtitle ?: "", style = T.small, color = Color.White)
                        }
                    }
                    CircleButton(
                        Ic.Play, "Play all", { col?.songs?.let { if (it.isNotEmpty()) { vm.play(it, 0); onOpenNowPlaying() } } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp), size = 76.dp,
                        container = c.accent, content = c.onAccent, iconSize = 26.dp,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Play", { col?.songs?.let { if (it.isNotEmpty()) { vm.play(it, 0); onOpenNowPlaying() } } }, selected = true, big = true, icon = Ic.Play, modifier = Modifier.weight(1f))
                    Pill("Shuffle", { col?.songs?.let { vm.shuffle(it); onOpenNowPlaying() } }, big = true, icon = Ic.Shuffle, modifier = Modifier.weight(1f))
                }
            }
            val list = col?.songs.orEmpty()
            if (list.isNotEmpty()) {
                itemsIndexed(list, key = { i, s -> "$i-${s.id}" }) { i, s ->
                    SongRow(s, s.id == now.songId, vm, number = if (isAlbum) (s.track.takeIf { it > 0 } ?: (i + 1)) else null, onLongClick = { actionsFor = s }) {
                        vm.play(list, i)
                        onOpenNowPlaying()
                    }
                }
            }
        }
    }
}

private data class Quad(val song: SongEntity, val key: String, val kicker: String, val title: String)
