package com.skarif.kairos.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.skarif.kairos.data.db.SongDao
import com.skarif.kairos.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A folder shown on the Music folders screen. [key] is what gets stored in the excluded set. */
data class FolderInfo(val key: String, val label: String, val count: Int)

/**
 * Reads the device's audio files from MediaStore into Room.
 * MediaStore already indexes every audio file, so no filesystem walk is needed.
 */
class MediaScanner(private val context: Context, private val songDao: SongDao) {

    private val projection = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DATE_ADDED)
        if (Build.VERSION.SDK_INT >= 29) {
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.VOLUME_NAME)
        } else {
            @Suppress("DEPRECATION") add(MediaStore.Audio.Media.DATA)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            add(MediaStore.Audio.Media.BITRATE)
            add(MediaStore.Audio.Media.GENRE)
        }
    }.toTypedArray()

    /** Every audio file MediaStore knows about, before any of our filters. */
    private fun queryAll(minDurationMs: Long): List<SongEntity> {
        val out = ArrayList<SongEntity>()
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf(minDurationMs.toString()), null,
        )?.use { c ->
            fun col(name: String) = c.getColumnIndex(name)
            val iId = col(MediaStore.Audio.Media._ID)
            val iTitle = col(MediaStore.Audio.Media.TITLE)
            val iArtist = col(MediaStore.Audio.Media.ARTIST)
            val iAlbum = col(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = col(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = col(MediaStore.Audio.Media.DURATION)
            val iTrack = col(MediaStore.Audio.Media.TRACK)
            val iYear = col(MediaStore.Audio.Media.YEAR)
            val iName = col(MediaStore.Audio.Media.DISPLAY_NAME)
            val iMime = col(MediaStore.Audio.Media.MIME_TYPE)
            val iSize = col(MediaStore.Audio.Media.SIZE)
            val iAdded = col(MediaStore.Audio.Media.DATE_ADDED)
            val iRel = col("relative_path")
            val iVol = col("volume_name")
            val iData = col("_data")
            val iBitrate = col("bitrate")
            val iGenre = col("genre")
            // Android 9-10 has no genre column; read the Genres table once instead.
            val legacyGenres = if (iGenre < 0) legacyGenreMap() else emptyMap()

            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                val folder = when {
                    iRel >= 0 -> c.getString(iRel) ?: ""
                    iData >= 0 -> legacyRelativeFolder(c.getString(iData) ?: "")
                    else -> ""
                }
                out += SongEntity(
                    id = c.getLong(iId),
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                    artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    track = c.getInt(iTrack) % 1000, // MediaStore stores disc * 1000 + track
                    year = c.getInt(iYear),
                    folder = folder,
                    fileName = name,
                    volume = if (iVol >= 0) c.getString(iVol) ?: "external_primary" else "external_primary",
                    mimeType = c.getString(iMime) ?: "",
                    sizeBytes = c.getLong(iSize),
                    dateAddedSec = c.getLong(iAdded),
                    bitrate = if (iBitrate >= 0) c.getInt(iBitrate) else 0,
                    genre = cleanGenre(if (iGenre >= 0) c.getString(iGenre) else legacyGenres[c.getLong(iId)]),
                )
            }
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun legacyGenreMap(): Map<Long, String> {
        val out = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
            )?.use { g ->
                while (g.moveToNext()) {
                    val gid = g.getLong(0)
                    val name = g.getString(1) ?: continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", gid),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null,
                    )?.use { m -> while (m.moveToNext()) out.putIfAbsent(m.getLong(0), name) }
                }
            }
        }
        return out
    }

    /** Folders with song counts, for the Music folders screen. */
    suspend fun listFolders(minDurationMs: Long): List<FolderInfo> = withContext(Dispatchers.IO) {
        queryAll(minDurationMs)
            .groupingBy { folderKey(it.folder) }
            .eachCount()
            .map { (key, n) -> FolderInfo(key, "/" + key.trimEnd('/'), n) }
            .sortedByDescending { it.count }
    }

    /** Syncs Room with MediaStore. Returns the number of songs kept. */
    suspend fun scan(excludedFolders: Set<String>, minDurationMs: Long): Int = withContext(Dispatchers.IO) {
        val songs = queryAll(minDurationMs).filter { folderKey(it.folder) !in excludedFolders }
        songDao.upsertAll(songs)
        val keep = songs.mapTo(HashSet()) { it.id }
        val gone = songDao.allIds().filter { it !in keep }
        gone.chunked(500).forEach { songDao.deleteIds(it) } // SQLite caps bound parameters
        songs.size
    }

    companion object {
        /** ID3v1 numeric genres, the ones that actually show up in libraries. */
        private val ID3 = mapOf(
            0 to "Blues", 1 to "Classic Rock", 2 to "Country", 3 to "Dance", 4 to "Disco", 5 to "Funk", 6 to "Grunge",
            7 to "Hip-Hop", 8 to "Jazz", 9 to "Metal", 10 to "New Age", 11 to "Oldies", 12 to "Other", 13 to "Pop",
            14 to "R&B", 15 to "Rap", 16 to "Reggae", 17 to "Rock", 18 to "Techno", 19 to "Industrial", 20 to "Alternative",
            24 to "Soundtrack", 26 to "Ambient", 32 to "Classical", 35 to "House", 52 to "Electronic", 98 to "Easy Listening",
        )

        /** "(13)" or "13" becomes "Pop"; blanks and "<unknown>" become "". */
        fun cleanGenre(raw: String?): String {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty() || t.equals("<unknown>", true) || t.equals("unknown", true)) return ""
            Regex("""^\(?(\d{1,3})\)?$""").find(t)?.let { return ID3[it.groupValues[1].toInt()] ?: "" }
            return t.replaceFirstChar { it.uppercaseChar() }
        }

        /**
         * Groups files by top-level folder ("Music/"), except under "Android/", where apps such as
         * WhatsApp keep audio deep down. There the last two folders are used instead.
         */
        fun folderKey(relative: String): String {
            val parts = relative.trim('/').split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return ""
            return if (parts[0].equals("Android", ignoreCase = true) && parts.size >= 2) {
                parts.takeLast(2).joinToString("/") + "/"
            } else {
                parts[0] + "/"
            }
        }

        private fun legacyRelativeFolder(path: String): String {
            val dir = path.substringBeforeLast('/', "") + "/"
            val root = "/storage/emulated/0/"
            return if (dir.startsWith(root)) dir.removePrefix(root) else dir.substringAfter("/storage/").substringAfter('/')
        }
    }
}
