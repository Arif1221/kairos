package com.skarif.kairos.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The design's line icons, built from the same SVG paths, so no icon library is needed.
 * Icon() tints them, so the black here is only a placeholder.
 */
object Ic {
    private fun stroke(name: String, d: String, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun solid(name: String, d: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()

    val Back by lazy { stroke("back", "M15 18l-6-6 6-6") }
    val Down by lazy { stroke("down", "M6 9l6 6 6-6") }
    val Chevron by lazy { stroke("chev", "M9 18l6-6-6-6") }
    val Play by lazy { solid("play", "M8 5v14l11-7z") }
    val Pause by lazy { solid("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z") }
    val Next by lazy { solid("next", "M6 5l9 7-9 7zM16.5 5h2v14h-2z") }
    val Prev by lazy { solid("prev", "M18 5l-9 7 9 7zM5.5 5h2v14h-2z") }
    val Heart by lazy { stroke("heart", "M12 20.5s-7.5-4.6-9.6-9.2A5.3 5.3 0 0 1 12 6.4a5.3 5.3 0 0 1 9.6 4.9c-2.1 4.6-9.6 9.2-9.6 9.2z") }
    val HeartFilled by lazy { solid("heartF", "M12 20.5s-7.5-4.6-9.6-9.2A5.3 5.3 0 0 1 12 6.4a5.3 5.3 0 0 1 9.6 4.9c-2.1 4.6-9.6 9.2-9.6 9.2z") }
    val Lyrics by lazy { stroke("lyrics", "M4 5h16v11H9l-5 4zM8 9h8M8 12h5") }
    val Home by lazy { stroke("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z") }
    val Settings by lazy { stroke("settings", "M12 2.8 20 7.4v9.2l-8 4.6-8-4.6V7.4zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z") }
    val Refresh by lazy { stroke("refresh", "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7") }
    val Folder by lazy { stroke("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val Plus by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val Close by lazy { stroke("close", "M6 6l12 12M18 6 6 18") }
    val Wave by lazy { stroke("wave", "M3 12h2M7 8v8M11 5v14M15 9v6M19 11v2") }
    val Clock by lazy { stroke("clock", "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z") }
    val Queue by lazy { stroke("queue", "M3 6h13M3 12h13M3 18h8M17 15l5 3-5 3z") }
    val Check by lazy { stroke("check", "M20 6 9 17l-5-5") }
    val Music by lazy { stroke("music", "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z") }
    val Palette by lazy { stroke("palette", "M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.6 0-1-.8-1.4-.8-2.4 0-1 .8-1.5 1.8-1.5H17a4 4 0 0 0 4-4c0-4.6-4-8.5-9-8.5z") }
}
