package com.skarif.kairos.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
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

/** Type scale from the design. Swap FontFamily for Urbanist once its font files are in res/font. */
object T {
    val h1 = TextStyle(fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.Normal)
    val h2 = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.Bold)
    val big = TextStyle(fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-1.2).sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
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
    val targetAccent = Color(if (useArt) album!!.accent else settings.fixedColor)
    val targetOn = Color(if (useArt) album!!.onAccent else ArtworkRepository.onColorFor(settings.fixedColor))
    val accent by animateColorAsState(targetAccent, tween(600), label = "accent")
    val onAccent by animateColorAsState(targetOn, tween(600), label = "onAccent")

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
        CompositionLocalProvider(LocalPlayerColors provides c, LocalContentColor provides c.text, content = content)
    }
}
