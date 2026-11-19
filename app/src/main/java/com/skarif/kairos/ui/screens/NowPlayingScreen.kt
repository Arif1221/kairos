package com.skarif.kairos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.formatLabel
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.WaveformSeek
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/** Design reference: the hotel screen (photo hero) plus the finance chart card as the seek bar. */
@Composable
fun NowPlayingScreen(vm: MainViewModel, onBack: () -> Unit, onLyrics: () -> Unit) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = song

    ScreenBackground(art, settings.blurBackground) {
        if (s == null) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircleButton(Ic.Back, "Back", onBack)
                Text("Nothing is playing. Pick a song in your library.", style = T.body, color = c.muted)
            }
            return@ScreenBackground
        }
        val liked = s.id in favs
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(458.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp))
                        .background(c.accent),
                ) {
                    val bmp = art
                    if (bmp != null) {
                        val img = remember(bmp) { bmp.asImageBitmap() }
                        Image(img, "${s.album} cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x9E000000)))),
                    )
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircleButton(Ic.Down, "Close now playing", onBack, container = Color(0xE0FFFFFF), content = Color(0xFF151515))
                        Box(Modifier.weight(1f))
                        CircleButton(Ic.Lyrics, "Lyrics", onLyrics, container = Color(0x38FFFFFF), content = Color.White)
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 34.dp, end = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Ic.Music, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(if (s.year > 0) "${s.year}" else "Album", color = Color.White, style = T.small.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Text(s.album, color = Color.White, style = T.big.copy(fontSize = 38.sp, lineHeight = 40.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                CircleButton(
                    if (liked) Ic.HeartFilled else Ic.Heart,
                    if (liked) "Remove from favourites" else "Add to favourites",
                    { vm.toggleFavorite(s.id) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp),
                    size = 76.dp, container = Color(0xFF151515), content = Color.White, iconSize = 28.dp,
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.artist.uppercase(), color = c.text, textAlign = TextAlign.Center,
                        style = T.small.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                    )
                    Text(s.title, color = c.text, style = T.h2.copy(fontSize = 30.sp, lineHeight = 34.sp), textAlign = TextAlign.Center)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(Ic.Wave, now.output?.let { "${s.formatLabel()} $it" } ?: s.formatLabel())
                    InfoChip(Ic.Clock, formatTime(s.durationMs))
                    if (now.queueSize > 0) InfoChip(Ic.Queue, "${now.queueIndex + 1} of ${now.queueSize}")
                }
                WaveformSeek(now.positionMs, now.durationMs.takeIf { it > 0 } ?: s.durationMs, s.id, vm::seekTo)
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleButton(Ic.Prev, "Previous song", vm::previous, size = 64.dp, iconSize = 22.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(c.ink)
                            .clickable(role = Role.Button, onClick = vm::toggle),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(if (now.isPlaying) Ic.Pause else Ic.Play, null, tint = c.inkFg, modifier = Modifier.size(20.dp))
                        Text(if (now.isPlaying) "Pause" else "Play", color = c.inkFg, style = T.title.copy(fontSize = 17.sp))
                    }
                    CircleButton(Ic.Next, "Next song", vm::next, size = 64.dp, iconSize = 22.dp)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = P.colors
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(16.dp))
        Text(text, style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
    }
}
