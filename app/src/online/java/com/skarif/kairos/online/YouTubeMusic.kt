package com.skarif.kairos.online

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
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

    private val streamUrls = ConcurrentHashMap<String, Pair<String, Long>>()

    private val init by lazy { NewPipe.init(NewPipeDownloader) }

    override suspend fun search(query: String, kind: OnlineKind): List<SongEntity> = withContext(Dispatchers.IO) {
        init
        val yt = ServiceList.YouTube
        val filter = when (kind) {
            OnlineKind.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            OnlineKind.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
        }
        val extractor = yt.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            .filter { kind == OnlineKind.SONGS || !it.isShortFormContent } // Shorts aren't music
            .mapNotNull { it.toSong(kind) }
    }

    override fun mediaUri(song: SongEntity): Uri = Uri.parse("$SCHEME:${song.fileName}")

    override fun dataSourceFactory(context: Context, quality: () -> StreamQuality): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        // Local files and content:// Uris pass straight through; our Uris become the real stream URL
        // when the player opens them, so URLs are always fresh (they expire).
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http)) { spec ->
            if (spec.uri.scheme != SCHEME) spec
            else spec.withUri(Uri.parse(streamUrl(spec.uri.schemeSpecificPart, quality())))
        }
    }

    /** Called on the player's loading thread, so blocking here is fine. */
    private fun streamUrl(videoId: String, quality: StreamQuality): String {
        val key = "$videoId|$quality"
        streamUrls[key]?.let { (url, at) -> if (System.currentTimeMillis() - at < URL_TTL_MS) return url }
        try {
            init
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
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
            return pick.content.also { streamUrls[key] = it to System.currentTimeMillis() }
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
