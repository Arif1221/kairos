package com.skarif.kairos.online

import com.skarif.kairos.data.db.SongEntity
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Lyrics for online songs from LRCLIB (lrclib.net): free, no key, mostly synced. */
internal object LrcLib {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private const val UA = "Kairos Online (https://github.com/Arif1221/kairos)"

    /** Video titles carry extras like "(Official Video)" or "ft. X" that LRCLIB titles don't. */
    private val noise = Regex("""\s*[(\[][^)\]]*(official|video|audio|lyric|visualizer|hd|4k|remaster|from|feat|ft\.)[^)\]]*[)\]]|\s+(ft\.|feat\.).*$""", RegexOption.IGNORE_CASE)

    fun find(song: SongEntity): String? {
        var title = song.title.replace(noise, "").trim()
        var artist = song.artist
        // Videos are often titled "Artist - Song".
        if (" - " in title) {
            val (a, t) = title.split(" - ", limit = 2)
            artist = a.trim(); title = t.trim()
        }
        val seconds = song.durationMs / 1000
        exact(artist, title, seconds)?.let { return it }
        return search("$artist $title", seconds)
    }

    private fun exact(artist: String, title: String, seconds: Long): String? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .apply { if (seconds > 0) addQueryParameter("duration", seconds.toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        return pick(JSONObject(body))
    }

    private fun search(q: String, seconds: Long): String? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
        val arr = JSONArray(get(url.toString()) ?: return null)
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { seconds <= 0 || abs(it.optDouble("duration", 0.0) - seconds) <= 4 }
        // Synced first, then plain.
        return items.firstNotNullOfOrNull { it.optString("syncedLyrics").takeIf { s -> s.isNotBlank() && s != "null" } }
            ?: items.firstNotNullOfOrNull { pick(it) }
    }

    private fun pick(o: JSONObject): String? =
        listOf("syncedLyrics", "plainLyrics").firstNotNullOfOrNull { k ->
            o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        }

    /** Body on 200, null on 404; throws on network errors. */
    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
}
