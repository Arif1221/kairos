package com.skarif.kairos.online

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import com.skarif.kairos.data.StreamQuality
import com.skarif.kairos.data.db.SongEntity

/** What to search: YouTube Music songs, or any YouTube video (played audio-only). */
enum class OnlineKind(val label: String) { SONGS("Songs"), VIDEOS("Videos") }

/**
 * A streaming source, present only in the "online" build flavor.
 * Shared code reaches it through [Flavor.online], which is null in the Play Store build.
 *
 * Online songs are ordinary [SongEntity] rows with a negative id and volume [ONLINE_VOLUME],
 * so favourites, playlists, stats and the queue work for them unchanged.
 */
interface OnlineMusic {
    /** Shown in the UI, e.g. "YouTube Music". */
    val name: String

    /** Songs matching [query]. Throws on network errors. */
    suspend fun search(query: String, kind: OnlineKind = OnlineKind.SONGS): List<SongEntity>

    /** The Uri handed to the player; [dataSourceFactory] turns it into a real stream. */
    fun mediaUri(song: SongEntity): Uri

    /**
     * Plays both local files and this source's Uris. [quality] is asked each time a stream is
     * opened, so a settings or network change applies from the next song.
     */
    fun dataSourceFactory(context: Context, quality: () -> StreamQuality): DataSource.Factory
}
