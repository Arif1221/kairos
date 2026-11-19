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
import com.skarif.kairos.data.SongSort
import com.skarif.kairos.data.db.FavoriteEntity
import com.skarif.kairos.data.db.PlayEventEntity
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.lyrics.Lyrics
import com.skarif.kairos.playback.NowPlaying
import com.skarif.kairos.playback.SleepState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

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

/** A list of songs shown with the album-style screen: albums, playlists and mixes. */
data class Collection(val key: String, val title: String, val subtitle: String, val songs: List<SongEntity>, val playlistId: Long? = null)

data class PlaylistUi(val id: Long, val name: String, val songs: List<SongEntity>)

enum class StatsRange(val label: String) { WEEK("W"), MONTH("M"), YEAR("Y"), ALL("All") }
data class StatBar(val label: String, val listenedMs: Long)
data class TopSong(val song: SongEntity, val plays: Int, val listenedMs: Long)
data class StatsUi(
    val totalMs: Long = 0,
    val plays: Int = 0,
    val change: String = "",
    val bars: List<StatBar> = emptyList(),
    val top: List<TopSong> = emptyList(),
    val streakDays: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ct = (app as PlayerApp).container
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<Settings> = ct.settings.settings
    val now: StateFlow<NowPlaying> = ct.player.state
    val sleep: StateFlow<SleepState> = ct.sleep.state
    val songs: StateFlow<List<SongEntity>> = ct.db.songs().observeAll().state(emptyList())
    private val events: StateFlow<List<PlayEventEntity>> = ct.db.playEvents().observeAll().state(emptyList())
    val favorites: StateFlow<Set<Long>> = ct.db.favorites().observeIds().map { it.toSet() }.state(emptySet())

    private val songById: StateFlow<Map<Long, SongEntity>> = songs.map { l -> l.associateBy { it.id } }.state(emptyMap())
    private val playCounts: StateFlow<Map<Long, Int>> = events.map { e -> e.groupingBy { it.songId }.eachCount() }.state(emptyMap())

    val sortedSongs: StateFlow<List<SongEntity>> = combine(songs, settings, playCounts) { list, s, counts ->
        val sorted = when (s.songSort) {
            SongSort.TITLE -> list.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SongSort.ALBUM -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
            SongSort.DATE_ADDED -> list.sortedBy { it.dateAddedSec }
            SongSort.LENGTH -> list.sortedBy { it.durationMs }
            SongSort.MOST_PLAYED -> list.sortedBy { counts[it.id] ?: 0 }
        }
        val naturallyDesc = s.songSort == SongSort.DATE_ADDED || s.songSort == SongSort.MOST_PLAYED
        if (s.sortDescending != naturallyDesc) sorted.asReversed() else sorted
    }.state(emptyList())

    val currentSong: StateFlow<SongEntity?> = now.map { it.songId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else ct.db.songs().observe(id) }
        .state(null)

    val artwork: StateFlow<Bitmap?> = currentSong.mapLatest { s -> s?.let { ct.artwork.load(it) } }.state(null)

    val albumColors: StateFlow<AlbumColors?> = combine(currentSong, artwork) { s, bmp ->
        if (s != null && bmp != null) ct.artwork.colorsFor(s.id, bmp) else null
    }.state(null)

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

    val playlists: StateFlow<List<PlaylistUi>> = combine(
        ct.db.playlists().observeAll(), ct.db.playlists().observeAllItems(), songById,
    ) { lists, items, byId ->
        val grouped = items.groupBy { it.playlistId }
        lists.map { p -> PlaylistUi(p.id, p.name, grouped[p.id].orEmpty().sortedBy { it.position }.mapNotNull { byId[it.songId] }) }
    }.state(emptyList())

    /** Home screen mixes, built from listening history. */
    val mixes: StateFlow<List<Collection>> = combine(songs, events, favorites, songById) { all, ev, favs, byId ->
        val counts = ev.groupingBy { it.songId }.eachCount()
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        val recentIds = ev.filter { it.startedAt >= monthAgo }.mapTo(HashSet()) { it.songId }
        val mostPlayed = counts.entries.sortedByDescending { it.value }.take(50).mapNotNull { byId[it.key] }
        val recent = all.sortedByDescending { it.dateAddedSec }.take(50)
        val favourites = all.filter { it.id in favs }
        val forgotten = counts.filter { it.value >= 2 && it.key !in recentIds }.keys.mapNotNull { byId[it] }
            .ifEmpty { all.filter { it.id !in counts }.shuffled(java.util.Random(LocalDate.now().toEpochDay())).take(40) }
        listOf(
            Collection("most", "Most played", "${mostPlayed.size} songs", mostPlayed),
            Collection("recent", "Recently added", "${recent.size} songs", recent),
            Collection("favourites", "Favourites", "${favourites.size} songs", favourites),
            Collection("forgotten", "Forgotten gems", "Not played lately", forgotten),
        )
    }.state(emptyList())

    /** Songs from the album you were last listening to, for the "Continue" card. */
    val continueAlbum: StateFlow<Collection?> = combine(currentSong, songs) { cur, all ->
        cur?.let { c -> albumOf(c.albumId, all) }
    }.state(null)

    private val _statsRange = MutableStateFlow(StatsRange.WEEK)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()
    private val _selectedBar = MutableStateFlow(-1)
    val selectedBar: StateFlow<Int> = _selectedBar.asStateFlow()

    val stats: StateFlow<StatsUi> = combine(events, songById, _statsRange) { ev, byId, range -> computeStats(ev, byId, range) }
        .state(StatsUi())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val searchResults: StateFlow<List<SongEntity>> = combine(songs, _query) { all, q ->
        val t = q.trim().lowercase()
        if (t.isEmpty()) emptyList()
        else all.filter { it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) || it.album.lowercase().contains(t) }.take(200)
    }.state(emptyList())

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    init {
        ct.player.connect()
    }

    // ---------------------------------------------------------------- collections

    fun albumOf(albumId: Long, all: List<SongEntity> = songs.value): Collection? {
        val list = all.filter { it.albumId == albumId }.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
        val first = list.firstOrNull() ?: return null
        val mins = list.sumOf { it.durationMs } / 60_000
        val year = list.firstOrNull { it.year > 0 }?.year
        return Collection("album:$albumId", first.album, listOfNotNull(year?.toString(), "${list.size} songs", "$mins min").joinToString(", "), list)
    }

    fun collection(key: String): Collection? = when {
        key.startsWith("album:") -> key.removePrefix("album:").toLongOrNull()?.let { albumOf(it) }
        key.startsWith("playlist:") -> key.removePrefix("playlist:").toLongOrNull()?.let { id ->
            playlists.value.firstOrNull { it.id == id }?.let { p -> Collection(key, p.name, "${p.songs.size} songs", p.songs, p.id) }
        }
        key.startsWith("artist:") -> key.removePrefix("artist:").let { name ->
            val l = songs.value.filter { it.artist == name }.sortedBy { it.title.lowercase() }
            Collection(key, name, "${l.size} songs", l)
        }
        else -> mixes.value.firstOrNull { it.key == key }
    }

    // ---------------------------------------------------------------- stats

    private fun computeStats(ev: List<PlayEventEntity>, byId: Map<Long, SongEntity>, range: StatsRange): StatsUi {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun dayOf(e: PlayEventEntity) = Instant.ofEpochMilli(e.startedAt).atZone(zone).toLocalDate()
        val (from, prevFrom) = when (range) {
            StatsRange.WEEK -> today.minusDays(6) to today.minusDays(13)
            StatsRange.MONTH -> today.minusDays(29) to today.minusDays(59)
            StatsRange.YEAR -> today.minusMonths(11).withDayOfMonth(1) to today.minusMonths(23).withDayOfMonth(1)
            StatsRange.ALL -> LocalDate.MIN to LocalDate.MIN
        }
        val inRange = ev.filter { !dayOf(it).isBefore(from) }
        val prev = ev.filter { val d = dayOf(it); !d.isBefore(prevFrom) && d.isBefore(from) }
        val total = inRange.sumOf { it.listenedMs }
        val prevTotal = prev.sumOf { it.listenedMs }
        val change = when {
            range == StatsRange.ALL -> "${inRange.size} plays in total"
            prevTotal == 0L -> "${inRange.size} plays"
            else -> {
                val pct = ((total - prevTotal) * 100 / prevTotal).toInt()
                if (pct >= 0) "$pct% more than the period before" else "${-pct}% less than the period before"
            }
        }
        val loc = Locale.getDefault()
        val bars = when (range) {
            StatsRange.WEEK -> (6 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                StatBar(d.dayOfWeek.getDisplayName(TextStyle.SHORT, loc), inRange.filter { dayOf(it) == d }.sumOf { it.listenedMs })
            }
            StatsRange.MONTH -> (3 downTo 0).map { back ->
                val end = today.minusDays(back * 7L)
                val start = end.minusDays(6)
                StatBar("${start.dayOfMonth}/${start.monthValue}", inRange.filter { val d = dayOf(it); !d.isBefore(start) && !d.isAfter(end) }.sumOf { it.listenedMs })
            }
            StatsRange.YEAR -> (5 downTo 0).map { back ->
                val m = today.minusMonths(back.toLong())
                StatBar(m.month.getDisplayName(TextStyle.SHORT, loc), inRange.filter { val d = dayOf(it); d.year == m.year && d.month == m.month }.sumOf { it.listenedMs })
            }
            StatsRange.ALL -> {
                val years = ev.map { dayOf(it).year }.distinct().sorted().takeLast(6).ifEmpty { listOf(today.year) }
                years.map { y -> StatBar("$y", ev.filter { dayOf(it).year == y }.sumOf { it.listenedMs }) }
            }
        }
        val top = inRange.groupBy { it.songId }
            .mapNotNull { (id, l) -> byId[id]?.let { TopSong(it, l.size, l.sumOf { e -> e.listenedMs }) } }
            .sortedByDescending { it.listenedMs }.take(5)
        val days = ev.mapTo(HashSet()) { dayOf(it) }
        var streak = 0
        var d = if (today in days) today else today.minusDays(1)
        while (d in days) { streak++; d = d.minusDays(1) }
        return StatsUi(total, inRange.size, change, bars, top, streak)
    }

    fun setStatsRange(r: StatsRange) { _statsRange.value = r; _selectedBar.value = -1 }
    fun selectBar(i: Int) { _selectedBar.value = i }

    // ---------------------------------------------------------------- actions

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

    fun setQuery(q: String) { _query.value = q }

    fun play(list: List<SongEntity>, index: Int) = ct.player.play(list, index)
    fun shuffle(list: List<SongEntity>) { if (list.isNotEmpty()) ct.player.play(list, 0, shuffle = true) }
    fun playNext(list: List<SongEntity>) { ct.player.playNext(list) }
    fun toggle() { ct.player.toggle() }
    fun next() { ct.player.next() }
    fun previous() { ct.player.previous() }
    fun seekTo(ms: Long) { ct.player.seekTo(ms) }
    fun playQueueItem(i: Int) { ct.player.playQueueItem(i) }
    fun moveQueueItem(from: Int, to: Int) { ct.player.moveQueueItem(from, to) }
    fun removeQueueItem(i: Int) { ct.player.removeQueueItem(i) }
    fun toggleShuffle() { ct.player.toggleShuffle() }
    fun cycleRepeat() { ct.player.cycleRepeat() }
    fun song(id: Long): SongEntity? = songById.value[id]

    fun startSleep(minutes: Int, fade: Boolean) = ct.sleep.start(minutes, fade)
    fun startSleepEndOfSong(fade: Boolean) = ct.sleep.startEndOfSong(fade)
    fun cancelSleep() = ct.sleep.cancel()

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            if (songId in favorites.value) ct.db.favorites().remove(songId)
            else ct.db.favorites().add(FavoriteEntity(songId, System.currentTimeMillis()))
        }
    }

    fun createPlaylist(name: String, songIds: List<Long>) {
        viewModelScope.launch { ct.db.playlists().create(name.ifBlank { "New playlist" }, songIds) }
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch { ct.db.playlists().append(playlistId, songIds) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { ct.db.playlists().delete(playlistId) }
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
    suspend fun cover(song: SongEntity): Bitmap? = ct.artwork.load(song, 720)
}

/** Days between two instants; used by screens that show "x days ago". */
fun daysAgo(epochMs: Long): Long = ChronoUnit.DAYS.between(Instant.ofEpochMilli(epochMs), Instant.now())
