package com.skarif.kairos.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import com.skarif.kairos.data.splitArtists
import com.skarif.kairos.data.AlbumGroup
import com.skarif.kairos.data.Taste
import com.skarif.kairos.data.genreKey
import com.skarif.kairos.data.groupAlbums
import com.skarif.kairos.data.groupGenres
import com.skarif.kairos.data.normalize
import com.skarif.kairos.data.searchSongs
import com.skarif.kairos.data.matchesQuery
import com.skarif.kairos.data.sortKey
import com.skarif.kairos.data.splitGenres
import com.skarif.kairos.data.spreadShuffle
import com.skarif.kairos.data.weightedSample
import com.skarif.kairos.data.Recap
import com.skarif.kairos.data.dayPart
import com.skarif.kairos.data.parseM3u
import com.skarif.kairos.data.similarSongs
import com.skarif.kairos.data.smartShuffle
import com.skarif.kairos.data.toM3u
import com.skarif.kairos.data.yearRecap
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

/** One artist, after splitting "A feat. B" tags, with every song they appear on. */
data class ArtistUi(val name: String, val songs: List<SongEntity>)

data class GenreUi(val name: String, val songs: List<SongEntity>)

data class SearchHits(
    val artists: List<ArtistUi> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val genres: List<GenreUi> = emptyList(),
)

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
    val uniqueSongs: Int = 0,
    val avgPerDayMs: Long = 0,
    val completedPct: Int = 0,
    /** e.g. "9 PM", the hour you most often start listening. */
    val peakHour: String = "",
    val topArtists: List<Pair<String, Long>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ct = (app as PlayerApp).container
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    /** For indexes that are read with `.value` (queue rows, album lookups): always kept up to date. */
    private fun <T> Flow<T>.always(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val settings: StateFlow<Settings> = ct.settings.settings
    val now: StateFlow<NowPlaying> = ct.player.state
    val position: StateFlow<Long> = ct.player.position
    val sleep: StateFlow<SleepState> = ct.sleep.state
    val songs: StateFlow<List<SongEntity>> = ct.db.songs().observeAll().always(emptyList())
    private val events: StateFlow<List<PlayEventEntity>> = ct.db.playEvents().observeAll().state(emptyList())
    val favorites: StateFlow<Set<Long>> = ct.db.favorites().observeIds().map { it.toSet() }.always(emptySet())

    /**
     * Song lookup by id. Shared eagerly: the queue reads it for every row, and while it was only
     * started by other screens it could still be empty when the queue opened, so every row showed
     * "Unknown" with no cover. Screens should collect [songMap] so they redraw when it fills.
     */
    private val songById: StateFlow<Map<Long, SongEntity>> = songs.map { l -> l.associateBy { it.id } }
        .flowOn(Dispatchers.Default).always(emptyMap())
    val songMap: StateFlow<Map<Long, SongEntity>> get() = songById
    val playCounts: StateFlow<Map<Long, Int>> = events.map { e -> e.groupingBy { it.songId }.eachCount() }.state(emptyMap())

    /** Albums merged across MediaStore ids; see [groupAlbums]. */
    val albums: StateFlow<List<AlbumGroup>> = songs.map { groupAlbums(it) }.flowOn(Dispatchers.Default).always(emptyList())
    private val albumIndex: StateFlow<Map<Long, AlbumGroup>> = albums.map { l ->
        HashMap<Long, AlbumGroup>().apply { l.forEach { g -> g.albumIds.forEach { put(it, g) } } }
    }.always(emptyMap())
    fun albumGroup(albumId: Long): AlbumGroup? = albumIndex.value[albumId]

    private val sortKey = settings.map { it.songSort to it.sortDescending }.distinctUntilChanged()
    val sortedSongs: StateFlow<List<SongEntity>> = combine(songs, sortKey, playCounts) { list, (sort, desc), counts ->
        val sorted = when (sort) {
            SongSort.TITLE -> list.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SongSort.ALBUM -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
            SongSort.DATE_ADDED -> list.sortedBy { it.dateAddedSec }
            SongSort.LENGTH -> list.sortedBy { it.durationMs }
            SongSort.MOST_PLAYED -> list.sortedBy { counts[it.id] ?: 0 }
        }
        val naturallyDesc = sort == SongSort.DATE_ADDED || sort == SongSort.MOST_PLAYED
        if (desc != naturallyDesc) sorted.asReversed() else sorted
    }.state(emptyList())

    val currentSong: StateFlow<SongEntity?> = now.map { it.songId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else ct.db.songs().observe(id) }
        .state(null)

    /**
     * The song and its cover, loaded together. Colours must be computed from this pair:
     * combining the current song with "the latest cover" separately gave the new song the
     * previous song's cover for a moment, and that wrong colour was then cached.
     */
    private val songArt: StateFlow<Pair<SongEntity, Bitmap?>?> = currentSong
        .mapLatest { s -> s?.let { it to ct.artwork.load(it) } }
        .state(null)

    val artwork: StateFlow<Bitmap?> = songArt.map { it?.second }.state(null)

    val albumColors: StateFlow<AlbumColors?> = songArt.mapLatest { pair ->
        val (song, bmp) = pair ?: return@mapLatest null
        bmp?.let { ct.artwork.colorsFor(song, it) }
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

    /** Artists from split tags, most songs first. Computed off the main thread. */
    val artists: StateFlow<List<ArtistUi>> = songs.map { all ->
        val acc = LinkedHashMap<String, Pair<String, MutableList<SongEntity>>>()
        for (song in all) for (a in splitArtists(song.artist)) {
            acc.getOrPut(a.lowercase()) { a to ArrayList() }.second += song
        }
        acc.values.map { (name, l) -> ArtistUi(name, l.sortedBy { it.title.lowercase() }) }.sortedBy { sortKey(it.name) }
    }.flowOn(Dispatchers.Default).always(emptyList())

    /** "Pop; Rock" counts for both, and "Hip-Hop" / "hip hop" are one genre. */
    val genres: StateFlow<List<GenreUi>> = songs.map { all -> groupGenres(all).map { GenreUi(it.name, it.songs) } }
        .flowOn(Dispatchers.Default).always(emptyList())

    /** Hidden songs plus songs snoozed until a time still ahead. Re-checked every few minutes. */
    private val clock: Flow<Long> = kotlinx.coroutines.flow.flow {
        while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(5 * 60_000L) }
    }
    val blocked: StateFlow<Set<Long>> = combine(settings, clock) { s, t ->
        s.hiddenSongs.mapNotNullTo(HashSet()) { it.toLongOrNull() } +
            s.snoozedSongs.mapNotNull { e -> e.split(':').takeIf { it.size == 2 && (it[1].toLongOrNull() ?: 0L) > t }?.get(0)?.toLongOrNull() }
    }.distinctUntilChanged().always(emptySet())
    val hiddenIds: StateFlow<Set<Long>> = settings.map { s -> s.hiddenSongs.mapNotNullTo(HashSet()) { it.toLongOrNull() } }.always(emptySet())
    val snoozedIds: StateFlow<Set<Long>> = combine(blocked, hiddenIds) { b, h -> b - h }.always(emptySet())
    val tasteExcluded: StateFlow<Set<Long>> = settings.map { s -> s.tasteExcluded.mapNotNullTo(HashSet()) { it.toLongOrNull() } }
        .distinctUntilChanged().always(emptySet())
    /** The hour, so the daylist changes through the day. */
    private val hour: Flow<Int> = clock.map { java.time.LocalTime.now().hour }.distinctUntilChanged()

    /** Real listens (30 s, or half a short song) and skips, minus songs kept out of your taste. */
    private class History(val plays: List<PlayEventEntity>, val skips: List<PlayEventEntity>, val taste: Taste)
    private data class MixIn(
        val songs: List<SongEntity>, val events: List<PlayEventEntity>, val history: History, val byId: Map<Long, SongEntity>, val genres: List<GenreUi>,
    )

    private fun history(ev: List<PlayEventEntity>, byId: Map<Long, SongEntity>, favs: Set<Long>, excluded: Set<Long>): History {
        val (plays, skips) = ev.filter { it.songId !in excluded }.partition { e ->
            val d = byId[e.songId]?.durationMs ?: 0L
            e.listenedMs >= minOf(30_000L, if (d > 0) d / 2 else 30_000L)
        }
        val artistMs = HashMap<String, Long>()
        plays.forEach { e -> byId[e.songId]?.let { s -> splitArtists(s.artist).forEach { a -> artistMs.merge(normalize(a), e.listenedMs, Long::plus) } } }
        return History(plays, skips, Taste(plays.groupingBy { it.songId }.eachCount(), skips.groupingBy { it.songId }.eachCount(), favs, artistMs))
    }

    private val historyFlow: StateFlow<History> = combine(events, songById, favorites, tasteExcluded) { ev, byId, favs, ex -> history(ev, byId, favs, ex) }
        .flowOn(Dispatchers.Default).always(History(emptyList(), emptyList(), Taste(emptyMap(), emptyMap(), emptySet(), emptyMap())))

    /**
     * Auto playlists, rebuilt whenever the library or listening history changes. The first four
     * show on Home; all of them are under Playlists, Smart.
     */
    val mixes: StateFlow<List<Collection>> = combine(
        combine(songs, events, historyFlow, songById, genres) { a, b, c, d, e -> MixIn(a, b, c, d, e) },
        blocked, hour,
    ) { (allSongs, ev, hist, byId, gens), block, hr ->
        val all = allSongs
        // Generated mixes leave out hidden and snoozed songs; your own lists (favourites, most played) don't.
        val open = all.filter { it.id !in block }
        val plays = hist.plays
        val taste = hist.taste
        val counts = taste.plays
        val anyPlay = ev.mapTo(HashSet()) { it.songId }
        val lastAt = plays.groupBy { it.songId }.mapValues { (_, l) -> l.maxOf { it.startedAt } }
        val now = System.currentTimeMillis()
        val monthAgo = now - 30L * 24 * 3600 * 1000
        val artistMs = taste.artistMs
        // New every day, and stable within the day: each mix has its own seed, so a play being
        // saved (every 15 s) only nudges the weights instead of reshuffling every mix.
        val day = LocalDate.now().toEpochDay()
        fun rng(key: String) = kotlin.random.Random(day * 31 + key.hashCode())
        fun pick(key: String, pool: List<SongEntity>, n: Int, w: (SongEntity) -> Double = taste::score): List<SongEntity> {
            val r = rng(key)
            return spreadShuffle(weightedSample(pool, n, r, w), r)
        }

        // Recent plays count more: a play loses half its weight every 30 days.
        val decayed = HashMap<Long, Double>()
        plays.forEach { e -> decayed.merge(e.songId, Math.pow(0.5, (now - e.startedAt) / (30.0 * 24 * 3600 * 1000)), Double::plus) }
        val mostPlayed = counts.keys
            .sortedWith(compareByDescending<Long> { counts[it] ?: 0 }.thenByDescending { decayed[it] ?: 0.0 })
            .take(50).mapNotNull { byId[it] }
        val recent = all.sortedByDescending { it.dateAddedSec }.take(50)
        val favourites = all.filter { it.id in favorites.value }
        // Loved once (3+ plays) but not played for a month. Empty until that is true.
        val forgotten = counts.filter { (id, n) -> n >= 3 && (lastAt[id] ?: 0L) < monthAgo }
            .keys.sortedByDescending { counts[it] }.take(40).mapNotNull { byId[it] }
        val onRepeat = plays.filter { it.startedAt >= monthAgo }.groupingBy { it.songId }.eachCount()
            .filter { it.value >= 3 }.entries.sortedByDescending { it.value }.take(40).mapNotNull { byId[it.key] }
        val lastPlayed = plays.sortedByDescending { it.startedAt }.map { it.songId }.distinct().take(50).mapNotNull { byId[it] }
        // Never started at all; artists you already like come up first.
        val neverPlayed = pick("never", open.filter { it.id !in anyPlay }, 60, taste::discoveryScore)
        val quick = pick("quick", open.filter { it.durationMs in 60_000..180_000 }, 40)
        val longOnes = open.filter { it.durationMs >= 6 * 60_000 }.sortedByDescending { it.durationMs }.take(40)
        // Your top artists, plus songs that share their genres: like a radio station for each.
        val topArtists = artistMs.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val artistMixes = topArtists.mapNotNull { key ->
            val own = open.filter { s -> splitArtists(s.artist).any { normalize(it) == key } }
            if (own.size < 3) return@mapNotNull null
            val name = splitArtists(own.first().artist).first { normalize(it) == key }
            val genreKeys = own.flatMap { splitGenres(it.genre) }.map(::genreKey).toSet()
            val related = if (genreKeys.isEmpty()) emptyList()
            else open.filter { s -> s !in own && splitGenres(s.genre).any { genreKey(it) in genreKeys } }
            val r = rng("artist:$key")
            val songs = spreadShuffle(weightedSample(own, 20, r, taste::score) + weightedSample(related, 30, r, taste::score), r)
            Collection("mix-artist:$name", "$name radio", "$name and similar songs, new every day", songs)
        }
        val genreMixes = gens.take(6).map { g ->
            val songs = pick("genre:${g.name}", g.songs.filter { it.id !in block }, 50)
            Collection("mix-genre:${g.name}", "${g.name} mix", "${songs.size} songs, new every day", songs)
        }
        // daylist: what you play at this time of day, plus songs like it. Changes through the day.
        val part = dayPart(hr)
        val zone = ZoneId.systemDefault()
        val partCounts = plays.filter { dayPart(Instant.ofEpochMilli(it.startedAt).atZone(zone).hour) == part }
            .groupingBy { it.songId }.eachCount().filterKeys { it !in block }
        val daylist = if (partCounts.size < 3) emptyList() else {
            val r = rng("day:${part.name}")
            val usual = weightedSample(partCounts.keys.mapNotNull { byId[it] }, 25, r) { s -> (partCounts[s.id] ?: 1) * taste.score(s) }
            spreadShuffle(usual + similarSongs(usual.take(15), open, taste, 20, r, usual.mapTo(HashSet()) { it.id }), r)
        }
        listOfNotNull(
            Collection("daylist", "Your ${part.label} mix", "What you play around this time, changes through the day", daylist).takeIf { daylist.isNotEmpty() },
        ) + listOf(
            Collection("most", "Most played", "Your most listened songs", mostPlayed),
            Collection("recent", "Recently added", "${recent.size} songs", recent),
            Collection("favourites", "Favourites", "${favourites.size} songs", favourites),
            Collection("forgotten", "Forgotten gems", "Loved once, not played this month", forgotten),
            Collection("onrepeat", "On repeat", "Played 3+ times this month", onRepeat),
            Collection("lastplayed", "Recently played", "${lastPlayed.size} songs", lastPlayed),
            Collection("never", "Never played", "Something new from your library", neverPlayed),
            Collection("quick", "Quick hits", "Under 3 minutes", quick),
            Collection("long", "Long listens", "6 minutes or more", longOnes),
        ) + artistMixes + genreMixes
    }.flowOn(Dispatchers.Default).state(emptyList())

    /** Songs from the album you were last listening to, for the "Continue" card. */
    val continueAlbum: StateFlow<Collection?> = combine(currentSong, albumIndex) { cur, idx ->
        cur?.let { c -> idx[c.albumId]?.toCollection() }
    }.state(null)

    /** This year's recap; in January, last year's until this year has some listening. */
    val recap: StateFlow<Recap?> = combine(events, songById) { ev, byId ->
        val y = LocalDate.now().year
        yearRecap(y, ev, byId, ZoneId.systemDefault())?.takeIf { it.minutes >= 30 || LocalDate.now().monthValue > 1 }
            ?: yearRecap(y - 1, ev, byId, ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default).state(null)

    /** Custom playlist covers, kept as files: playlist id -> image file. */
    private val coverDir = java.io.File(app.filesDir, "playlist-covers").apply { mkdirs() }
    private val _covers = MutableStateFlow(readCovers())
    val playlistCovers: StateFlow<Map<Long, java.io.File>> = _covers.asStateFlow()
    private fun readCovers(): Map<Long, java.io.File> =
        coverDir.listFiles().orEmpty().mapNotNull { f -> f.nameWithoutExtension.substringBefore('-').toLongOrNull()?.let { it to f } }.toMap()

    private val _statsRange = MutableStateFlow(StatsRange.WEEK)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()
    private val _selectedBar = MutableStateFlow(-1)
    val selectedBar: StateFlow<Int> = _selectedBar.asStateFlow()

    val stats: StateFlow<StatsUi> = combine(events, songById, _statsRange) { ev, byId, range -> computeStats(ev, byId, range) }
        .state(StatsUi())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val searchResults: StateFlow<List<SongEntity>> = combine(songs, _query) { all, q -> searchSongs(all, q, 200) }
        .flowOn(Dispatchers.Default).state(emptyList())

    /** Artists, albums and genres matching the search, names starting with it first. */
    val searchHits: StateFlow<SearchHits> = combine(_query, artists, albums, genres) { q, ar, al, ge ->
        if (q.isBlank()) SearchHits() else {
            val n = normalize(q)
            fun rank(name: String) = if (normalize(name).startsWith(n)) 0 else 1
            SearchHits(
                artists = ar.filter { matchesQuery(it.name, q) }.sortedWith(compareBy({ rank(it.name) }, { -it.songs.size })).take(20),
                albums = al.filter { matchesQuery("${it.title} ${it.artist}", q) }.sortedWith(compareBy({ rank(it.title) }, { -it.songs.size })).take(20),
                genres = ge.filter { matchesQuery(it.name, q) }.sortedWith(compareBy({ rank(it.name) }, { -it.songs.size })).take(12),
            )
        }
    }.flowOn(Dispatchers.Default).state(SearchHits())

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** The song autoplay last added songs for, so it adds only once per "end of queue". */
    private var autoplayedAfter: Long? = null

    init {
        ct.player.connect()
        // Autoplay: when the last song in the queue starts, add similar songs from your library.
        viewModelScope.launch {
            now.map { Triple(it.songId, it.queuePosition == it.queue.lastIndex, it.repeatMode) }.distinctUntilChanged().collect { (id, last, repeat) ->
                if (id == null || !last || repeat != androidx.media3.common.Player.REPEAT_MODE_OFF || !settings.value.autoplay) return@collect
                if (autoplayedAfter == id) return@collect
                autoplayedAfter = id
                val queued = now.value.queue.mapNotNull { songById.value[it.songId] }
                val more = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    similarSongs(queued.takeLast(10), songs.value, historyFlow.value.taste, 10, kotlin.random.Random.Default, blocked.value + queued.map { it.id })
                }
                ct.player.append(more)
            }
        }
    }

    // ---------------------------------------------------------------- collections

    fun albumOf(albumId: Long): Collection? = albumIndex.value[albumId]?.toCollection()

    private fun AlbumGroup.toCollection(): Collection {
        val mins = songs.sumOf { it.durationMs } / 60_000
        return Collection("album:$id", title, listOfNotNull(year.takeIf { it > 0 }?.toString(), "${songs.size} songs", "$mins min").joinToString(", "), songs)
    }

    fun collection(key: String): Collection? = when {
        key.startsWith("album:") -> key.removePrefix("album:").toLongOrNull()?.let { albumOf(it) }
        key.startsWith("playlist:") -> key.removePrefix("playlist:").toLongOrNull()?.let { id ->
            playlists.value.firstOrNull { it.id == id }?.let { p -> Collection(key, p.name, "${p.songs.size} songs", p.songs, p.id) }
        }
        key.startsWith("artist:") -> key.removePrefix("artist:").let { name ->
            val l = artists.value.firstOrNull { it.name.equals(name, true) }?.songs
                ?: songs.value.filter { s -> splitArtists(s.artist).any { it.equals(name, true) } }.sortedBy { it.title.lowercase() }
            Collection(key, name, "${l.size} songs, ${l.distinctBy { it.albumId }.size} albums", l)
        }
        key.startsWith("genre:") -> key.removePrefix("genre:").let { name ->
            val l = genres.value.firstOrNull { genreKey(it.name) == genreKey(name) }?.songs.orEmpty()
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
        val spanDays = when (range) {
            StatsRange.ALL -> (days.minOrNull()?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) + 1 } ?: 1L)
            else -> java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1
        }.coerceAtLeast(1L)
        val peak = inRange.groupingBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }.eachCount().maxByOrNull { it.value }?.key
        val peakHour = peak?.let { h -> java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a", loc)) } ?: ""
        val artistTime = HashMap<String, Pair<String, Long>>()
        inRange.forEach { e ->
            byId[e.songId]?.let { s ->
                splitArtists(s.artist).forEach { a ->
                    val k = a.lowercase()
                    artistTime[k] = a to ((artistTime[k]?.second ?: 0L) + e.listenedMs)
                }
            }
        }
        return StatsUi(
            total, inRange.size, change, bars, top, streak,
            uniqueSongs = inRange.mapTo(HashSet()) { it.songId }.size,
            avgPerDayMs = total / spanDays,
            completedPct = if (inRange.isEmpty()) 0 else inRange.count { it.completed } * 100 / inRange.size,
            peakHour = peakHour,
            topArtists = artistTime.values.sortedByDescending { it.second }.take(5),
        )
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
    /**
     * Shuffles with artists spread out, then plays that order with the player's own shuffle off.
     * The queue shows exactly what will play, and play next / reorder keep working.
     */
    fun shuffle(list: List<SongEntity>, smart: Boolean = settings.value.smartShuffle) {
        if (list.isEmpty()) return
        val r = kotlin.random.Random.Default
        val order = if (smart) smartShuffle(list, songs.value, historyFlow.value.taste, r, blocked.value) else spreadShuffle(list, r)
        ct.player.play(order, 0)
    }

    /** Shuffle the whole library (or a big list), leaving out hidden and snoozed songs. */
    fun shuffleAll(list: List<SongEntity>) = shuffle(list.filter { it.id !in blocked.value }.ifEmpty { list }, smart = false)

    // ---------------------------------------------------------------- hide, snooze, taste

    fun setHidden(songId: Long, hidden: Boolean) = updateSettings { s ->
        s.copy(hiddenSongs = if (hidden) s.hiddenSongs + "$songId" else s.hiddenSongs - "$songId")
    }

    /** Keeps a song out of mixes and autoplay for [days] days. */
    fun snooze(songId: Long, days: Int = 30) = updateSettings { s ->
        val until = System.currentTimeMillis() + days * 24L * 3600 * 1000
        s.copy(snoozedSongs = s.snoozedSongs.filterNot { it.startsWith("$songId:") }.toSet() + "$songId:$until")
    }

    fun unsnooze(songId: Long) = updateSettings { s -> s.copy(snoozedSongs = s.snoozedSongs.filterNot { it.startsWith("$songId:") }.toSet()) }

    fun setTasteExcluded(songId: Long, excluded: Boolean) = updateSettings { s ->
        s.copy(tasteExcluded = if (excluded) s.tasteExcluded + "$songId" else s.tasteExcluded - "$songId")
    }

    fun clearHiddenAndSnoozed() = updateSettings { it.copy(hiddenSongs = emptySet(), snoozedSongs = emptySet()) }
    fun clearTasteExclusions() = updateSettings { it.copy(tasteExcluded = emptySet()) }

    // ---------------------------------------------------------------- playlist tools

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { ct.db.playlists().rename(id, name.trim()) }
    }

    fun removeFromPlaylist(id: Long, index: Int) {
        val p = playlists.value.firstOrNull { it.id == id } ?: return
        if (index !in p.songs.indices) return
        viewModelScope.launch { ct.db.playlists().replaceSongs(id, p.songs.map { it.id }.toMutableList().apply { removeAt(index) }) }
    }

    fun moveInPlaylist(id: Long, from: Int, to: Int) {
        val p = playlists.value.firstOrNull { it.id == id } ?: return
        if (from !in p.songs.indices || to !in p.songs.indices || from == to) return
        val ids = p.songs.map { it.id }.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { ct.db.playlists().replaceSongs(id, ids) }
    }

    /** Copies the picked image into app storage, scaled down, as the playlist's cover. */
    fun setPlaylistCover(id: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            runCatching {
                val src = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return@runCatching
                val side = minOf(src.width, src.height)
                val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                val scaled = Bitmap.createScaledBitmap(square, 720.coerceAtMost(side), 720.coerceAtMost(side), true)
                coverDir.listFiles().orEmpty().filter { it.name.startsWith("$id-") }.forEach { it.delete() }
                // A new file name each time, so screens that cached the old image reload it.
                java.io.File(coverDir, "$id-${System.currentTimeMillis()}.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }.onFailure { toast("Couldn't use that image") }
            _covers.value = readCovers()
        }
    }

    fun removePlaylistCover(id: Long) {
        coverDir.listFiles().orEmpty().filter { it.name.startsWith("$id-") }.forEach { it.delete() }
        _covers.value = readCovers()
    }

    fun exportPlaylist(id: Long, uri: Uri) {
        val p = playlists.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(toM3u(p.songs).toByteArray()) }
            }.onSuccess { toast("Saved ${p.name} (${p.songs.size} songs)") }.onFailure { toast("Couldn't save the playlist") }
        }
    }

    /** Reads an .m3u/.m3u8 file and makes a playlist from the songs found on this phone. */
    fun importPlaylist(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }?.substringBeforeLast('.') ?: "Imported playlist"
                val (found, missing) = parseM3u(text, songs.value)
                if (found.isEmpty()) { toast("None of those songs are on this phone"); return@runCatching }
                ct.db.playlists().create(name, found.map { it.id })
                toast("Imported $name: ${found.size} songs" + if (missing > 0) ", $missing not found" else "")
            }.onFailure { toast("Couldn't read that file") }
        }
    }

    private suspend fun toast(text: String) = kotlinx.coroutines.withContext(Dispatchers.Main) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun playNext(list: List<SongEntity>) { ct.player.playNext(list) }
    fun toggle() { ct.player.toggle() }
    fun next() { ct.player.next() }
    fun previous() { ct.player.previous() }
    /** Always the previous song, never a restart of this one. */
    fun previousTrack() { ct.player.previousTrack() }
    fun seekTo(ms: Long) { ct.player.seekTo(ms) }
    fun playQueueItem(i: Int) { ct.player.playQueueItem(i) }
    fun moveQueueItem(from: Int, to: Int) { ct.player.moveQueueItem(from, to) }
    fun removeQueueItem(i: Int) { ct.player.removeQueueItem(i) }
    fun moveToNext(i: Int) { ct.player.moveToNext(i) }
    fun moveToEnd(i: Int) { ct.player.moveToEnd(i) }
    fun clearUpcoming() { ct.player.clearUpcoming() }
    fun clearPlayed() { ct.player.clearPlayed() }
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
    fun cachedCover(song: SongEntity): Bitmap? = ct.artwork.peek(song, 720)
}

/** Days between two instants; used by screens that show "x days ago". */
fun daysAgo(epochMs: Long): Long = ChronoUnit.DAYS.between(Instant.ofEpochMilli(epochMs), Instant.now())
