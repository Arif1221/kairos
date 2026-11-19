package com.skarif.kairos.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.ArtworkRepository
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/** Album art for a song, loaded off the main thread and cached. */
@Composable
fun SongArt(vm: MainViewModel, song: SongEntity?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 16.dp, large: Boolean = false) {
    val c = P.colors
    val bmp by produceState<Bitmap?>(null, song?.id, large) {
        value = song?.let { if (large) vm.cover(it) else vm.thumbnail(it) }
    }
    val img = bmp
    if (img != null) {
        Image(img.asImageBitmap(), null, modifier.size(size).clip(RoundedCornerShape(corner)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(c.tint), contentAlignment = Alignment.Center) {
            Icon(Ic.Music, null, tint = c.muted, modifier = Modifier.size(size / 2.6f))
        }
    }
}

/** Accent colour taken from a song's art, for tinting cards (Home mixes, Search tiles). */
@Composable
fun artColor(vm: MainViewModel, song: SongEntity?): Color {
    val c = P.colors
    val argb by produceState<Int?>(null, song?.id) {
        value = song?.let { s -> vm.thumbnail(s)?.let { ArtworkRepository.paletteAccent(it) } }
    }
    return argb?.let { Color(ArtworkRepository.tuneAccent(it, c.dark)) } ?: c.accent
}

fun tintOf(card: Color, accent: Color, dark: Boolean): Color = lerp(card, accent, if (dark) 0.28f else 0.22f)

/** Large cover image used by heroes. */
@Composable
fun rememberCover(vm: MainViewModel, song: SongEntity?): ImageBitmap? {
    val bmp by produceState<Bitmap?>(null, song?.id) { value = song?.let { vm.cover(it) } }
    return bmp?.asImageBitmap()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongEntity,
    playing: Boolean,
    vm: MainViewModel,
    number: Int? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = P.colors
    val fg = if (playing) c.onAccent else c.text
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (playing) c.accent else Color.Transparent)
            .combinedClickable(
                role = Role.Button, onClickLabel = "Play", onClick = onClick,
                onLongClickLabel = "More options", onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (number != null) {
            Text("$number", Modifier.padding(start = 6.dp), style = T.small.copy(fontSize = 14.sp), color = fg)
        } else {
            SongArt(vm, song, 52.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = T.title, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = T.small, color = if (playing) fg.copy(alpha = 0.8f) else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), style = T.title.copy(fontSize = 15.sp), color = fg)
    }
}

/** Long-press menu: play next, add to a playlist, favourite. */
@Composable
fun SongActionsDialog(vm: MainViewModel, song: SongEntity, onOpenAlbum: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = P.colors
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text(song.title, style = T.h2, color = c.text, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DialogItem("Play next") { vm.playNext(listOf(song)); onDismiss() }
                DialogItem(if (song.id in favs) "Remove from favourites" else "Add to favourites") { vm.toggleFavorite(song.id); onDismiss() }
                DialogItem("Go to album") { onOpenAlbum(song.albumId); onDismiss() }
                if (playlists.isNotEmpty()) Text("Add to playlist", Modifier.padding(top = 10.dp, bottom = 4.dp), style = T.small, color = c.muted)
                playlists.forEach { p -> DialogItem(p.name) { vm.addToPlaylist(p.id, listOf(song.id)); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.text) } },
    )
}

@Composable
private fun DialogItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.fillMaxWidth(), style = T.title, color = P.colors.text)
    }
}

/** Seek bar that reads the playback position itself, so only it redraws 5 times a second. */
@Composable
fun LiveWaveformSeek(vm: MainViewModel, durationMs: Long, seed: Long, modifier: Modifier = Modifier, barsHeight: Dp = 66.dp, bars: Int = 26) {
    val pos by vm.position.collectAsStateWithLifecycle()
    com.skarif.kairos.ui.components.WaveformSeek(pos, durationMs, seed, vm::seekTo, modifier, barsHeight, bars)
}

@Composable
fun LiveTime(vm: MainViewModel, durationMs: Long, color: Color) {
    val pos by vm.position.collectAsStateWithLifecycle()
    Text("${formatTime(pos)} / ${formatTime(durationMs)}", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
}
