package com.skarif.kairos.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.skarif.kairos.R
import com.skarif.kairos.data.AlbumColors
import com.skarif.kairos.data.ArtworkRepository
import com.skarif.kairos.data.Settings
import com.skarif.kairos.data.ThemeMode

/** The design's colour roles. [accent]/[onAccent] come from the album art (or a fixed colour). */
@Immutable
data class PlayerColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val ink: Color,
    val inkFg: Color,
    val veil: Color,
    val line: Color,
    val sheet: Color,
    val accent: Color,
    val onAccent: Color,
    val dark: Boolean,
) {
    /** A soft wash of the accent, used for backgrounds and icon circles. */
    val tint: Color get() = lerp(card, accent, 0.2f)
}

val LocalPlayerColors = staticCompositionLocalOf<PlayerColors> { error("PlayerTheme missing") }

object P {
    val colors: PlayerColors
        @Composable @ReadOnlyComposable get() = LocalPlayerColors.current
}

/** Urbanist, the design's typeface: one variable font file, one instance per weight. */
@OptIn(ExperimentalTextApi::class)
val Urbanist = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(R.font.urbanist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Type scale from the design. */
object T {
    private val base = TextStyle(fontFamily = Urbanist)
    val h1 = base.copy(fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.Normal)
    val h2 = base.copy(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.Bold)
    val big = base.copy(fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-1.2).sp, fontWeight = FontWeight.Bold)
    val title = base.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val body = base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val small = base.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    val label = base.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    /** Small caps-style labels above titles ("PLAYING FROM", tile kickers). */
    val kicker = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    /** Times and counts: tabular digits, so "1:11" and "8:58" are the same width and don't jitter. */
    val num = small.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
}

/** Corner radii, so cards, tiles and rows match across screens. */
object Radius {
    /** Big cards and panels. */
    val card = 30.dp
    /** Rows, list items and small cards. */
    val item = 24.dp
    /** Cover thumbnails in rows. */
    val art = 16.dp
}

/** "Make it **yours**": the design's light/bold headline. Pass pairs of (text, bold). */
fun mixedHeadline(vararg parts: Pair<String, Boolean>): AnnotatedString = buildAnnotatedString {
    parts.forEach { (s, bold) ->
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(s) } else append(s)
    }
}

@Composable
fun PlayerTheme(settings: Settings, album: AlbumColors?, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val useArt = settings.colorFromArt && album != null
    // Album colours are tuned per theme so the accent never disappears into the background.
    val tuned = remember(useArt, album, settings.fixedColor, dark) {
        ArtworkRepository.tuneAccent(if (useArt) album.accent else settings.fixedColor, dark)
    }
    val accent by animateColorAsState(Color(tuned), tween(700), label = "accent")
    val onAccent by animateColorAsState(Color(ArtworkRepository.onColorFor(tuned)), tween(700), label = "onAccent")

    val c = if (dark) {
        PlayerColors(
            bg = if (settings.pureBlack) Color.Black else Color(0xFF121212),
            card = Color(0xFF2A2A2A), text = Color(0xFFF4F4F0), muted = Color(0xFFA9A9A3),
            ink = Color(0xFFF4F4F0), inkFg = Color(0xFF151515),
            veil = if (settings.pureBlack) Color(0xD9000000) else Color(0xB30E0E0E),
            line = Color(0x29FFFFFF), sheet = Color(0xFF1C1C1C),
            accent = accent, onAccent = onAccent, dark = true,
        )
    } else {
        PlayerColors(
            bg = Color(0xFFF7F7F4), card = Color.White, text = Color(0xFF151515), muted = Color(0xFF5C5C58),
            ink = Color(0xFF151515), inkFg = Color.White, veil = Color(0x9EFAFAF7),
            line = Color(0x24151515), sheet = Color(0xFFF7F7F4),
            accent = accent, onAccent = onAccent, dark = false,
        )
    }
    val scheme = if (dark) {
        darkColorScheme(primary = accent, onPrimary = onAccent, background = c.bg, surface = c.card, onBackground = c.text, onSurface = c.text)
    } else {
        lightColorScheme(primary = accent, onPrimary = onAccent, background = c.bg, surface = c.card, onBackground = c.text, onSurface = c.text)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPlayerColors provides c,
            LocalContentColor provides c.text,
            LocalTextStyle provides T.body,
            content = content,
        )
    }
}
