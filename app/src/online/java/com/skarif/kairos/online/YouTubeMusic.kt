package com.skarif.kairos.online

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.skarif.kairos.data.StreamQuality
import com.skarif.kairos.data.db.ONLINE_VOLUME
import com.skarif.kairos.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube Music through NewPipeExtractor (YouTube's internal API, no key).
 *
 * Unofficial: it breaks when YouTube changes things, and the fix is usually bumping the
 * NewPipeExtractor version in libs.versions.toml.
 */
@OptIn(UnstableApi::class)
internal object YouTubeMusic : OnlineMusic {
    private const val SCHEME = "kairos-yt"

    /** Stream URLs expire after about 6 hours; refresh well before that. */
    private const val URL_TTL_MS = 3 * 3600_000L

    override val name = "YouTube Music"

    private class Resolved(val url: String, val at: Long)
    private val streamUrls = ConcurrentHashMap<String, Resolved>()

    /**
     * The quality each song is played at, fixed the first time it is opened in this process.
     * Seeks reopen the stream at a byte offset, so switching quality mid-song would corrupt it.
     */
    private val pinned = ConcurrentHashMap<String, StreamQuality>()

    private val init by lazy { NewPipe.init(NewPipeDownloader) }

    /** YouTube sometimes fails a request that works a moment later. */
    private inline fun <T> retry(times: Int = 2, block: () -> T): T {
        var last: Exception? = null
        repeat(times) { attempt ->
            try { return block() } catch (e: Exception) {
                last = e
                if (attempt < times - 1) Thread.sleep(600)
            }
        }
        throw last!!
    }

    override suspend fun search(query: String, kind: OnlineKind): List<SongEntity> = withContext(Dispatchers.IO) {
        init
        val yt = ServiceList.YouTube
        val filter = when (kind) {
            OnlineKind.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            OnlineKind.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
        }
        retry {
            val extractor = yt.getSearchExtractor(query, listOf(filter), "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                .filter { kind == OnlineKind.SONGS || !it.isShortFormContent } // Shorts aren't music
                .mapNotNull { it.toSong(kind) }
        }
    }

    override suspend fun lyrics(song: SongEntity): String? = withContext(Dispatchers.IO) { LrcLib.find(song) }

    override fun mediaUri(song: SongEntity): Uri = Uri.parse("$SCHEME:${song.fileName}")

    private fun cacheKey(videoId: String, q: StreamQuality) = "yt:$videoId:$q"

    override fun isCached(context: Context, song: SongEntity): Boolean =
        StreamQuality.entries.any { StreamCache.isComplete(context, cacheKey(song.fileName, it)) }

    override fun cacheBytes(context: Context): Long = StreamCache.get(context).cacheSpace

    override fun clearCache(context: Context) {
        StreamCache.clear(context)
        pinned.clear()
    }

    /** Which quality to use for [videoId]: already pinned, else one fully cached (plays offline), else the setting. */
    private fun qualityFor(context: Context, videoId: String, setting: () -> StreamQuality): StreamQuality =
        pinned.getOrPut(videoId) {
            StreamQuality.entries.reversed().firstOrNull { StreamCache.isComplete(context, cacheKey(videoId, it)) } ?: setting()
        }

    override fun dataSourceFactory(context: Context, quality: () -> StreamQuality): DataSource.Factory {
        val app = context.applicationContext
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        // Our Uris become the real stream URL only when bytes are missing from the cache,
        // so URLs are always fresh (they expire) and cached songs need no network at all.
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            val id = spec.uri.schemeSpecificPart
            spec.withUri(Uri.parse(streamUrl(id, qualityFor(app, id, quality))))
        }
        val cached = CacheDataSource.Factory()
            .setCache(StreamCache.get(app))
            .setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory { spec -> spec.uri.schemeSpecificPart.let { cacheKey(it, qualityFor(app, it, quality)) } }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val local = DefaultDataSource.Factory(app)
        return DataSource.Factory { RoutingDataSource(SCHEME, local.createDataSource(), cached.createDataSource()) }
    }

    /** Called on the player's loading thread, so blocking here is fine. */
    private fun streamUrl(videoId: String, quality: StreamQuality): String {
        val key = "$videoId|$quality"
        streamUrls[key]?.let { if (System.currentTimeMillis() - it.at < URL_TTL_MS) return it.url }
        try {
            init
            val info = retry { StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId") }
            // Audio-only streams; video streams are never fetched, so videos cost the same data as songs.
            val streams = info.audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
                .sortedBy(::kbps)
            if (streams.isEmpty()) throw IOException("No playable audio for $videoId")
            val pick = when (quality) {
                StreamQuality.LOW -> streams.first()
                StreamQuality.HIGH -> streams.last()
                StreamQuality.NORMAL -> streams.lastOrNull { kbps(it) in 1..140 } ?: streams.first()
            }
            return pick.content.also { streamUrls[key] = Resolved(it, System.currentTimeMillis()) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Couldn't load $videoId", e) // the player only reports IOExceptions as load errors
        }
    }

    /** Average bitrate in kbps; some streams only report the peak, in bits per second. */
    private fun kbps(s: AudioStream): Int = if (s.averageBitrate > 0) s.averageBitrate else s.bitrate / 1000

    private fun StreamInfoItem.toSong(kind: OnlineKind): SongEntity? {
        val videoId = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(url) }.getOrNull() ?: return null
        val art = thumbnails.maxByOrNull { it.height }?.url?.let(::squareArt)
        return SongEntity(
            id = idFor(videoId),
            title = name,
            artist = uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            album = if (kind == OnlineKind.SONGS) this@YouTubeMusic.name else "YouTube",
            albumId = 0,
            durationMs = duration.coerceAtLeast(0) * 1000,
            track = 0,
            year = 0,
            folder = "",
            fileName = videoId,
            volume = ONLINE_VOLUME,
            mimeType = "audio/opus",
            sizeBytes = 0,
            dateAddedSec = System.currentTimeMillis() / 1000,
            bitrate = 0,
            artUrl = art,
        )
    }

    /** YouTube Music art URLs end in a size like "=w60-h60-l90-rj"; ask for a large square instead. */
    private fun squareArt(url: String): String =
        if ("googleusercontent.com" in url) url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj") else url

    /**
     * A stable negative id from the video id (MediaStore ids are positive, so they never clash).
     * 64-bit FNV-1a, with the sign bit forced on.
     */
    internal fun idFor(videoId: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        for (ch in videoId) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L // FNV prime
        }
        return h or Long.MIN_VALUE
    }
}
