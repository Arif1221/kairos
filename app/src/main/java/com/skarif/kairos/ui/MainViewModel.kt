package com.skarif.kairos.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skarif.kairos.PlayerApp
import com.skarif.kairos.data.AlbumColors
import com.skarif.kairos.data.FolderInfo
import com.skarif.kairos.data.Settings
import com.skarif.kairos.data.db.FavoriteEntity
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.lyrics.Lyrics
import com.skarif.kairos.playback.NowPlaying
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val songs: Int) : ScanState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ct = (app as PlayerApp).container
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<Settings> = ct.settings.settings
    val now: StateFlow<NowPlaying> = ct.player.state
    val songs: StateFlow<List<SongEntity>> = ct.db.songs().observeAll().state(emptyList())

    val currentSong: StateFlow<SongEntity?> = now.map { it.songId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else ct.db.songs().observe(id) }
        .state(null)

    val artwork: StateFlow<Bitmap?> = currentSong.mapLatest { s -> s?.let { ct.artwork.load(it) } }.state(null)

    val albumColors: StateFlow<AlbumColors?> = combine(currentSong, artwork) { s, bmp ->
        if (s != null && bmp != null) ct.artwork.colorsFor(s.id, bmp) else null
    }.state(null)

    val favorites: StateFlow<Set<Long>> = ct.db.favorites().observeIds().map { it.toSet() }.state(emptySet())

    private val lyricsInputs = settings.map {
        listOf(it.readEmbeddedLyrics, it.readLrcFiles, it.lyricsPreference, it.lrcFolderUris)
    }.distinctUntilChanged()

    val lyrics: StateFlow<LyricsState> = combine(currentSong, lyricsInputs) { s, _ -> s }
        .transformLatest { s ->
            if (s == null) {
                emit(LyricsState.None)
            } else {
                emit(LyricsState.Loading)
                val l = ct.lyrics.load(s, settings.value)
                emit(if (l == null) LyricsState.None else LyricsState.Ready(l))
            }
        }.state(LyricsState.Loading)

    val lyricsOffset: StateFlow<Long> = currentSong
        .flatMapLatest { s -> if (s == null) flowOf(0L) else ct.lyrics.observeOffset(s.id) }
        .state(0L)

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    init {
        ct.player.connect()
    }

    fun scanNow() {
        if (_scan.value == ScanState.Scanning) return
        viewModelScope.launch {
            _scan.value = ScanState.Scanning
            val s = settings.value
            val n = runCatching { ct.scanner.scan(s.excludedFolders, s.minDurationMs) }.getOrDefault(0)
            _scan.value = ScanState.Done(n)
        }
    }

    fun loadFolders() {
        viewModelScope.launch { _folders.value = ct.scanner.listFolders(settings.value.minDurationMs) }
    }

    fun play(list: List<SongEntity>, index: Int) = ct.player.play(list, index)
    fun toggle() { ct.player.toggle() }
    fun next() { ct.player.next() }
    fun previous() { ct.player.previous() }
    fun seekTo(ms: Long) { ct.player.seekTo(ms) }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            if (songId in favorites.value) ct.db.favorites().remove(songId)
            else ct.db.favorites().add(FavoriteEntity(songId, System.currentTimeMillis()))
        }
    }

    /** Positive = lyrics appear later. */
    fun nudgeLyrics(deltaMs: Long) {
        val s = currentSong.value ?: return
        viewModelScope.launch { ct.lyrics.setOffset(s.id, lyricsOffset.value + deltaMs) }
    }

    fun updateSettings(change: (Settings) -> Settings) {
        viewModelScope.launch { ct.settings.update(change) }
    }

    fun addLrcFolder(uri: Uri) {
        getApplication<Application>().contentResolver
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        updateSettings { it.copy(lrcFolderUris = it.lrcFolderUris + uri.toString()) }
    }

    fun removeLrcFolder(uri: String) {
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        updateSettings { it.copy(lrcFolderUris = it.lrcFolderUris - uri) }
    }

    suspend fun thumbnail(song: SongEntity): Bitmap? = ct.artwork.load(song, 160)
}
