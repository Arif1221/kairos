package com.skarif.kairos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.data.db.contentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The two colours every screen takes from the album art. Stored as ARGB ints. */
data class AlbumColors(val accent: Int, val onAccent: Int)

class ArtworkRepository(private val context: Context) {
    /** Sized in bytes (32 MB), keyed by song and size, so list thumbnails and full art coexist. */
    private val bitmaps = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val colors = LruCache<Long, AlbumColors>(200)

    suspend fun load(song: SongEntity, sizePx: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        // Songs on one album share art, so cache per album: far fewer decodes in long lists.
        val key = "${if (song.albumId > 0) "a${song.albumId}" else "s${song.id}"}@$sizePx"
        bitmaps.get(key)?.let { return@withContext it }
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(song.contentUri(), Size(sizePx, sizePx), null)
            } else {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, song.contentUri())
                        embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } finally {
                        release()
                    }
                }
            }
        }.getOrNull()
        bmp?.also { bitmaps.put(key, it) }
    }

    /** Palette-API colour extraction, the same idea as the design's dynamic colour. */
    suspend fun colorsFor(songId: Long, bitmap: Bitmap): AlbumColors = withContext(Dispatchers.Default) {
        colors.get(songId)?.let { return@withContext it }
        val accent = pickAccent(Palette.from(bitmap).maximumColorCount(24).generate())
        AlbumColors(accent, onColorFor(accent)).also { colors.put(songId, it) }
    }

    companion object {
        const val DEFAULT_ACCENT = 0xFFBD3657.toInt()
        private const val INK = 0xFF151515.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** The Palette accent of any bitmap (used to tint cards by their cover). */
        suspend fun paletteAccent(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
            pickAccent(Palette.from(bitmap).maximumColorCount(16).generate())
        }

        /**
         * Picks the colour people perceive as "the cover's colour": the most common swatch that is
         * actually colourful. Grey covers fall back to their dominant colour.
         */
        fun pickAccent(p: Palette): Int {
            val hsl = FloatArray(3)
            val colourful = p.swatches.filter { ColorUtils.colorToHSL(it.rgb, hsl); hsl[1] >= 0.22f && hsl[2] in 0.12f..0.88f }
            val best = colourful.maxByOrNull { sw ->
                ColorUtils.colorToHSL(sw.rgb, hsl)
                sw.population * (0.35f + hsl[1]) * (1f - kotlin.math.abs(hsl[2] - 0.5f))
            }
            return (best ?: p.vibrantSwatch ?: p.dominantSwatch)?.rgb ?: DEFAULT_ACCENT
        }

        /**
         * Keeps an accent readable: lightness 0.36-0.56 on light backgrounds, 0.46-0.68 on dark ones,
         * and a little saturation so grey covers still give a usable tint.
         */
        fun tuneAccent(argb: Int, dark: Boolean): Int {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(argb, hsl)
            hsl[2] = if (dark) hsl[2].coerceIn(0.46f, 0.68f) else hsl[2].coerceIn(0.36f, 0.56f)
            if (hsl[1] > 0.08f) hsl[1] = hsl[1].coerceIn(0.35f, 0.9f)
            return ColorUtils.HSLToColor(hsl)
        }

        /** Black or white text, whichever reads better on [accent]. */
        fun onColorFor(accent: Int): Int {
            val solid = ColorUtils.setAlphaComponent(accent, 255)
            return if (ColorUtils.calculateContrast(INK, solid) >= ColorUtils.calculateContrast(WHITE, solid)) INK else WHITE
        }
    }
}
