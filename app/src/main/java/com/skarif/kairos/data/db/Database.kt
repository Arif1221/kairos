package com.skarif.kairos.data.db

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One audio file. The id is MediaStore's id, so it stays stable across rescans. */
@Entity(tableName = "songs", indices = [Index("albumId"), Index("folder")])
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    /** Folder relative to the storage root, e.g. "Music/Coastal Hours/". */
    val folder: String,
    val fileName: String,
    /** MediaStore volume, e.g. "external_primary" or an SD card id. */
    val volume: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val bitrate: Int,
)

fun SongEntity.contentUri(): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/** "FLAC", "MP3", "M4A"... for the format chip. */
fun SongEntity.formatLabel(): String =
    fileName.substringAfterLast('.', "").uppercase().ifEmpty { mimeType.substringAfter('/').uppercase() }

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long, val addedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("songId")],
)
data class PlaylistSongEntity(val playlistId: Long, val position: Int, val songId: Long)

/** One listening session of one song. Powers Stats and the "Most played" mixes. */
@Entity(tableName = "play_events", indices = [Index("songId"), Index("startedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val completed: Boolean,
)

/** Per-song lyrics timing fix set from the Lyrics screen. */
@Entity(tableName = "lyrics_offsets")
data class LyricsOffsetEntity(@PrimaryKey val songId: Long, val offsetMs: Long)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observe(id: Long): Flow<SongEntity?>

    @Query("SELECT id FROM songs")
    suspend fun allIds(): List<Long>

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)
}

@Dao
abstract class PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert
    abstract suspend fun insert(p: PlaylistEntity): Long

    @Insert
    abstract suspend fun insertSongs(items: List<PlaylistSongEntity>)

    @Transaction
    open suspend fun create(name: String, songIds: List<Long>): Long {
        val id = insert(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
        insertSongs(songIds.mapIndexed { i, s -> PlaylistSongEntity(id, i, s) })
        return id
    }
}

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(e: PlayEventEntity)
}

@Dao
interface LyricsOffsetDao {
    @Query("SELECT offsetMs FROM lyrics_offsets WHERE songId = :songId")
    fun observe(songId: Long): Flow<Long?>

    @Upsert
    suspend fun upsert(o: LyricsOffsetEntity)
}

@Database(
    entities = [
        SongEntity::class, FavoriteEntity::class, PlaylistEntity::class,
        PlaylistSongEntity::class, PlayEventEntity::class, LyricsOffsetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PlayerDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun playEvents(): PlayEventDao
    abstract fun lyricsOffsets(): LyricsOffsetDao

    companion object {
        fun create(context: Context): PlayerDatabase =
            Room.databaseBuilder(context, PlayerDatabase::class.java, "player.db").build()
    }
}
