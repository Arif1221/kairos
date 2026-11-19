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
        val key = "${song.id}@$sizePx"
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
        val p = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch
        val accent = swatch?.rgb ?: DEFAULT_ACCENT
        AlbumColors(accent, onColorFor(accent)).also { colors.put(songId, it) }
    }

    companion object {
        const val DEFAULT_ACCENT = 0xFFBD3657.toInt()
        private const val INK = 0xFF151515.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** The Palette accent of any bitmap (used to tint cards by their cover). */
        suspend fun paletteAccent(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
            val p = Palette.from(bitmap).maximumColorCount(12).generate()
            (p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb ?: DEFAULT_ACCENT
        }

        /** Black or white text, whichever reads better on [accent]. */
        fun onColorFor(accent: Int): Int {
            val solid = ColorUtils.setAlphaComponent(accent, 255)
            return if (ColorUtils.calculateContrast(INK, solid) >= ColorUtils.calculateContrast(WHITE, solid)) INK else WHITE
        }
    }
}
