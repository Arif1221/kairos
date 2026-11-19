package com.skarif.kairos.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import com.skarif.kairos.data.db.LyricsOffsetDao
import com.skarif.kairos.data.db.LyricsOffsetEntity
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.data.db.contentUri
import com.skarif.kairos.lyrics.ByteSource
import com.skarif.kairos.lyrics.LrcParser
import com.skarif.kairos.lyrics.Lyrics
import com.skarif.kairos.lyrics.TagReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/**
 * Finds lyrics for a song: embedded in the file's tags, and/or a .lrc file next to it.
 *
 * Why .lrc needs folder access: on Android 13+, READ_MEDIA_AUDIO only covers audio files, so a
 * plain .lrc file is invisible. The user grants the folder once through the system picker
 * (Settings > Lyrics), and we read the .lrc through that grant.
 */
class LyricsRepository(
    private val context: Context,
    private val offsets: LyricsOffsetDao,
) {
    private val cache = LruCache<String, LyricsResult>(30)

    data class LyricsResult(val lyrics: Lyrics?)

    suspend fun load(song: SongEntity, s: Settings): Lyrics? = withContext(Dispatchers.IO) {
        val key = "${song.id}|${s.readEmbeddedLyrics}|${s.readLrcFiles}|${s.lyricsPreference}|${s.lrcFolderUris.hashCode()}"
        cache.get(key)?.let { return@withContext it.lyrics }

        val embedded = if (s.readEmbeddedLyrics) readEmbedded(song) else null
        val lrc = if (s.readLrcFiles) readLrcFile(song, s.lrcFolderUris) else null
        val pick = when {
            embedded == null || embedded.isEmpty -> lrc
            lrc == null || lrc.isEmpty -> embedded
            s.lyricsPreference == LyricsPreference.EMBEDDED -> embedded
            else -> lrc
        }?.takeUnless { it.isEmpty }
        cache.put(key, LyricsResult(pick))
        pick
    }

    fun observeOffset(songId: Long): Flow<Long> = offsets.observe(songId).map { it ?: 0L }

    suspend fun setOffset(songId: Long, offsetMs: Long) = offsets.upsert(LyricsOffsetEntity(songId, offsetMs))

    /** Reads tags once; also used for ReplayGain by the playback service. */
    fun readTags(song: SongEntity): TagReader.Result? = readTags(context, song.contentUri())

    private fun readEmbedded(song: SongEntity): Lyrics? {
        val r = readTags(song) ?: return null
        return when {
            r.syncedLines.isNotEmpty() -> LrcParser.fromTimed(r.syncedLines, Lyrics.SOURCE_EMBEDDED)
            r.lyricsText != null -> LrcParser.parse(r.lyricsText, Lyrics.SOURCE_EMBEDDED)
            else -> null
        }
    }

    private fun readLrcFile(song: SongEntity, trees: Set<String>): Lyrics? {
        if (trees.isEmpty()) return null
        val base = song.fileName.substringBeforeLast('.')
        // Documents provider ids: "primary:Music/Album/song.lrc" or "1A2B-3C4D:Music/...".
        val volume = if (song.volume == "external_primary" || song.volume == "external") "primary" else song.volume.uppercase()
        val docId = "$volume:${song.folder}$base.lrc"
        for (t in trees) {
            val tree = Uri.parse(t)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            if (!docId.startsWith(treeId)) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull() ?: continue
            return LrcParser.parse(text, Lyrics.SOURCE_LRC_FILE)
        }
        return null
    }

    companion object {
        fun readTags(context: Context, uri: Uri): TagReader.Result? = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { ch -> TagReader.read(ByteSource.OfChannel(ch)) }
            }
        } catch (e: Exception) {
            Log.w("Lyrics", "Could not read tags of $uri", e)
            null
        }
    }
}
