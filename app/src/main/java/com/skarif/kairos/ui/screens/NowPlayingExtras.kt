package com.skarif.kairos.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.data.db.contentUri
import com.skarif.kairos.data.db.formatLabel
import com.skarif.kairos.data.normalize
import com.skarif.kairos.data.splitArtists
import com.skarif.kairos.ui.LyricsState
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.Pill
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/*
 * Everything on the player beyond the controls, matching what Spotify's Now Playing has:
 * the ⋮ menu (radio, share, speed, credits, hide...), elapsed/remaining time, and the cards
 * under the controls: up next, lyrics preview, about the artist and song credits.
 */

/** Shares the song as a message, or the audio file itself. */
fun shareSong(context: android.content.Context, song: SongEntity, file: Boolean) {
    val send = Intent(Intent.ACTION_SEND).apply {
        if (file) {
            type = song.mimeType.ifEmpty { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, song.contentUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Listening to ${song.title} by ${song.artist}" + if (song.album.isNotBlank()) " (${song.album})" else "")
        }
    }
    context.startActivity(Intent.createChooser(send, if (file) "Share song file" else "Share song"))
}

/** The player's ⋮ menu: the usual song actions plus the player-only ones. */
@Composable
fun PlayerMenu(
    vm: MainViewModel, song: SongEntity, onOpenAlbum: (Long) -> Unit, onOpenArtist: (String) -> Unit,
    onSleep: () -> Unit, onEqualizer: () -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    when {
        speed -> SpeedDialog(vm) { speed = false; onDismiss() }
        info -> SongInfoDialog(vm, song) { info = false; onDismiss() }
        else -> SongActionsDialog(
            vm, song, onOpenAlbum, onOpenArtist = onOpenArtist,
            extra = { dismiss ->
                DialogItem("Start song radio") { vm.startRadio(song); dismiss() }
                DialogItem("Share") { shareSong(ctx, song, file = false); dismiss() }
                DialogItem("Share the audio file") { shareSong(ctx, song, file = true); dismiss() }
                DialogItem("Playback speed") { speed = true }
                DialogItem("Sleep timer") { onSleep(); dismiss() }
                DialogItem("Equalizer") { onEqualizer(); dismiss() }
                DialogItem("Song info and credits") { info = true }
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SpeedDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = P.colors
    val now by vm.now.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text("Playback speed", style = T.h2, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf(0.5f, 0.75f, 0.9f, 1f), listOf(1.1f, 1.25f, 1.5f, 2f)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { v ->
                            Pill(speedLabel(v), { vm.setSpeed(v) }, selected = kotlin.math.abs(now.speed - v) < 0.01f, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text("Pitch changes with speed, like a record. 1× is normal.", style = T.small, color = c.muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = c.text) } },
    )
}

fun speedLabel(v: Float): String = (if (v == v.toInt().toFloat()) "${v.toInt()}" else "$v".trimEnd('0')) + "×"

/** Spotify's "Show credits", from the file's tags and what Android knows about it. */
@Composable
fun SongInfoDialog(vm: MainViewModel, song: SongEntity, onDismiss: () -> Unit) {
    val c = P.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text(song.title, style = T.h2, color = c.text, maxLines = 2) },
        text = { SongInfoRows(vm, song) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.text) } },
    )
}

@Composable
private fun SongInfoRows(vm: MainViewModel, song: SongEntity) {
    val c = P.colors
    val now by vm.now.collectAsStateWithLifecycle()
    val counts by vm.playCounts.collectAsStateWithLifecycle()
    val album = vm.albumGroup(song.albumId)
    val rows = listOfNotNull(
        "Artist" to song.artist,
        album?.artist?.takeIf { it != song.artist }?.let { "Album artist" to it },
        "Album" to song.album,
        song.track.takeIf { it > 0 }?.let { t -> "Track" to (if (t >= 1000) "Disc ${t / 1000}, track ${t % 1000}" else "$t") },
        song.year.takeIf { it > 0 }?.let { "Year" to "$it" },
        song.genre.takeIf { it.isNotBlank() }?.let { "Genre" to it },
        "Length" to formatTime(song.durationMs),
        "Format" to song.formatLabel() + if (song.bitrate > 0) ", ${song.bitrate / 1000} kbps" else "",
        now.output?.takeIf { now.songId == song.id }?.let { "Output now" to it },
        "Size" to String.format("%.1f MB", song.sizeBytes / 1_000_000.0),
        "Plays" to "${counts[song.id] ?: 0}",
        "Added" to java.time.Instant.ofEpochSecond(song.dateAddedSec).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
        "File" to song.folder + song.fileName,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (k, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k, Modifier.width(96.dp), style = T.small, color = c.muted)
                Text(v, Modifier.weight(1f), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            }
        }
    }
}

/** Elapsed on the left, time left on the right, under the seek bar. */
@Composable
fun TimeRow(vm: MainViewModel, durationMs: Long) {
    val c = P.colors
    val pos by vm.position.collectAsStateWithLifecycle()
    val p = pos.coerceIn(0, durationMs.coerceAtLeast(0))
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(formatTime(p), Modifier.weight(1f), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
        Text("-" + formatTime((durationMs - p).coerceAtLeast(0)), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
    }
}

@Composable
private fun InfoCard(title: String, action: String? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = P.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = T.title.copy(fontSize = 18.sp), color = c.text)
            if (action != null) Text(action, style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
        }
        content()
    }
}

/** The cards under the player controls. */
@Composable
fun NowPlayingCards(
    vm: MainViewModel, song: SongEntity, onQueue: () -> Unit, onLyrics: () -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val c = P.colors
    val now by vm.now.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val offset by vm.lyricsOffset.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val counts by vm.playCounts.collectAsStateWithLifecycle()
    val autoplay = vm.settings.collectAsStateWithLifecycle().value.autoplay

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Lyrics preview: the current line and the two after it, live.
        val l = (lyrics as? LyricsState.Ready)?.lyrics
        if (l != null && !l.isEmpty()) {
            val pos by vm.position.collectAsStateWithLifecycle()
            val at = if (l.synced) l.indexAt(pos - offset).coerceAtLeast(0) else 0
            InfoCard("Lyrics", "Show all", onLyrics) {
                l.lines.drop(at).take(if (l.synced) 3 else 4).forEachIndexed { i, line ->
                    Text(
                        line.text.ifBlank { "♪" }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = T.title.copy(fontSize = if (i == 0 && l.synced) 20.sp else 17.sp),
                        color = if (i == 0 || !l.synced) c.text else c.muted,
                    )
                }
            }
        }

        // Up next: the next three songs in the queue.
        val next = now.queue.drop(now.queuePosition + 1).take(3).mapNotNull { songMap[it.songId] }
        InfoCard("Up next", "Open queue", onQueue) {
            if (next.isEmpty()) {
                Text(
                    if (autoplay) "End of the queue. Autoplay will add similar songs." else "End of the queue.",
                    style = T.small, color = c.muted,
                )
            }
            next.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SongArt(vm, s, 44.dp, corner = 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = T.small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(s.durationMs), style = T.small, color = c.muted)
                }
            }
        }

        // About the artist, with your own numbers, and more of their songs.
        val main = splitArtists(song.artist).first()
        val artist = remember(artists, main) { artists.firstOrNull { normalize(it.name) == normalize(main) } }
        if (artist != null) {
            val plays = artist.songs.sumOf { counts[it.id] ?: 0 }
            val albums = artist.songs.distinctBy { vm.albumGroup(it.albumId)?.id ?: it.albumId }.size
            InfoCard("About the artist", "Open", { onOpenCollection("artist:${artist.name}") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SongArt(vm, artist.songs.maxByOrNull { counts[it.id] ?: 0 } ?: song, 64.dp, corner = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = T.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${artist.songs.size} songs, $albums albums on this phone. You've played them $plays times.",
                            style = T.small, color = c.muted,
                        )
                    }
                }
                val more = artist.songs.filter { it.id != song.id }.sortedByDescending { counts[it.id] ?: 0 }.take(12)
                if (more.isNotEmpty()) {
                    Text("More by ${artist.name}", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(0.dp)) {
                        items(more, key = { "mb" + it.id }) { s ->
                            Column(
                                Modifier.width(110.dp).clip(RoundedCornerShape(16.dp))
                                    .clickable(role = Role.Button, onClickLabel = "Play ${s.title}") { vm.play(more, more.indexOf(s), artist.name) },
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SongArt(vm, s, 110.dp, corner = 18.dp)
                                Text(s.title, style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Credits.
        InfoCard("Song info") { SongInfoRows(vm, song) }
        Box(Modifier.padding(bottom = 24.dp))
    }
}
