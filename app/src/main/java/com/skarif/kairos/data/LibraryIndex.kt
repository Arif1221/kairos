package com.skarif.kairos.data

import com.skarif.kairos.data.db.SongEntity
import java.text.Normalizer
import kotlin.math.ln
import kotlin.random.Random

/*
 * Pure library logic: grouping, search, genre cleanup, mixes and shuffle.
 * No Android types, so all of it is unit-tested on the JVM.
 */

/** Accents on Latin letters only: Bengali and Hindi vowel signs are marks too, and must stay. */
private val LATIN_MARKS = Regex("(?<=\\p{IsLatin})\\p{Mn}+")
private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}&]+")

/** "Beyoncé", "BEYONCE " and "beyonce" all become "beyonce". Keeps non-Latin scripts intact. */
fun normalize(s: String): String {
    val stripped = Normalizer.normalize(s, Normalizer.Form.NFD).replace(LATIN_MARKS, "")
    return Normalizer.normalize(stripped, Normalizer.Form.NFC).lowercase().replace(NON_WORD, " ").trim()
}

/** Lowercase, with a leading "The ", "A " or "An " dropped: "The Weeknd" sorts under W. */
fun sortTitle(s: String): String {
    val l = s.trim().lowercase()
    for (a in listOf("the ", "a ", "an ")) if (l.startsWith(a) && l.length > a.length) return l.substring(a.length)
    return l
}

/** Same, with a leading "the " dropped, for sorting and grouping. */
fun sortKey(s: String): String = normalize(s).removePrefix("the ")

// ---------------------------------------------------------------- search

/**
 * Every word you type must appear somewhere in the title, artist or album, in any order.
 * Results are ranked: title starts with the query, a title word starts with it, title contains it,
 * then artist, then album.
 */
fun searchSongs(all: List<SongEntity>, query: String, limit: Int = 300): List<SongEntity> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    val words = q.split(' ')
    return all.asSequence()
        .mapNotNull { s ->
            val title = normalize(s.title)
            val artist = normalize(s.artist)
            val album = normalize(s.album)
            val hay = "$title $artist $album"
            if (!words.all { it in hay }) return@mapNotNull null
            val rank = when {
                title.startsWith(q) -> 0
                title.split(' ').any { it.startsWith(q) } -> 1
                q in title -> 2
                artist.startsWith(q) || q in artist -> 3
                q in album -> 4
                else -> 5
            }
            Triple(s, rank, title)
        }
        .sortedWith(compareBy({ it.second }, { it.third.length }, { it.third }))
        .take(limit).map { it.first }.toList()
}

/** True when every typed word appears in [text]. For filtering albums, artists and genres. */
fun matchesQuery(text: String, query: String): Boolean {
    val q = normalize(query)
    if (q.isEmpty()) return true
    val hay = normalize(text)
    return q.split(' ').all { it in hay }
}

// ---------------------------------------------------------------- albums

data class AlbumGroup(
    /** The MediaStore album id used in "album:<id>" keys; any member id resolves to this group. */
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<SongEntity>,
    val albumIds: Set<Long>,
)

/**
 * MediaStore splits one album across several ids when its songs sit in different folders or were
 * tagged slightly differently ("Night Visions" vs "Night Visions (Deluxe)" stays split, but
 * "night visions" and "Night Visions " merge). Songs are grouped by album name plus album artist;
 * an album whose songs have many different artists is a compilation and groups under its name
 * alone. Songs with no album tag stay grouped by folder, so loose singles don't become one giant
 * "Unknown album".
 */
fun groupAlbums(all: List<SongEntity>): List<AlbumGroup> {
    val buckets = LinkedHashMap<String, MutableList<List<SongEntity>>>()
    // First by album name, then decide per name whether the parts are one album.
    val byName = all.groupBy { it.albumId }.values.groupBy { part ->
        val first = part.first()
        val name = sortKey(first.album)
        if (name.isEmpty() || first.album == UNKNOWN_ALBUM) "?|${first.folder}" else name
    }
    for ((name, parts) in byName) {
        if (name.startsWith("?|")) { buckets.getOrPut(name) { ArrayList() } += parts; continue }
        // A compilation whose parts are each mixed-artist is one album, whatever MediaStore says.
        // Parts that each belong to one artist ("Greatest Hits" by A and by B) stay apart.
        val whole = parts.flatten()
        val oneCompilation = isCompilation(whole) && parts.all { it.size == 1 || primaryArtists(it).size > 1 }
        for (part in parts) {
            val key = if (oneCompilation) "$name|various" else "$name|${albumArtistKey(part)}"
            buckets.getOrPut(key) { ArrayList() } += part
        }
    }
    return buckets.values.map { parts ->
        val songs = parts.flatten().sortedWith(compareBy({ it.track / 1000 }, { it.track % 1000 }, { sortKey(it.title) }))
        val rep = parts.maxBy { it.size }.first()
        AlbumGroup(
            id = rep.albumId,
            title = rep.album,
            artist = albumArtistName(songs),
            year = songs.firstOrNull { it.year > 0 }?.year ?: 0,
            songs = songs,
            albumIds = parts.mapTo(HashSet()) { it.first().albumId },
        )
    }.sortedBy { sortKey(it.title) }
}

const val UNKNOWN_ALBUM = "Unknown album"
const val UNKNOWN_ARTIST = "Unknown artist"

private fun primaryArtists(songs: List<SongEntity>): Map<String, Int> =
    songs.groupingBy { normalize(splitArtists(it.artist).first()) }.eachCount()

/** No single artist on at least half of 3+ songs. */
private fun isCompilation(songs: List<SongEntity>): Boolean {
    val top = primaryArtists(songs).maxByOrNull { it.value } ?: return false
    return songs.size >= 3 && top.value * 2 < songs.size
}

/** "various" for compilations, else the main artist. */
private fun albumArtistKey(songs: List<SongEntity>): String =
    if (isCompilation(songs)) "various" else primaryArtists(songs).maxByOrNull { it.value }?.key ?: ""

private fun albumArtistName(songs: List<SongEntity>): String {
    val counts = primaryArtists(songs)
    val top = counts.maxByOrNull { it.value } ?: return UNKNOWN_ARTIST
    if (isCompilation(songs)) return "Various artists"
    return songs.first { normalize(splitArtists(it.artist).first()) == top.key }.let { splitArtists(it.artist).first() }
}

// ---------------------------------------------------------------- genres

private val GENRE_SPLIT = Regex("\\s*[;/,|]\\s*")

/** "Pop; Rock", "Pop/Rock" and "Pop, Rock" are two genres each. "R&B" stays one. */
fun splitGenres(raw: String): List<String> =
    raw.split(GENRE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

/** "Hip-Hop", "hip hop" and "HipHop" group together. */
fun genreKey(g: String): String = normalize(g).replace(" ", "")

data class GenreGroup(val name: String, val songs: List<SongEntity>)

fun groupGenres(all: List<SongEntity>): List<GenreGroup> {
    val acc = LinkedHashMap<String, MutableList<SongEntity>>()
    val spellings = HashMap<String, MutableMap<String, Int>>()
    for (s in all) for (g in splitGenres(s.genre)) {
        val k = genreKey(g)
        if (k.isEmpty()) continue
        acc.getOrPut(k) { ArrayList() } += s
        spellings.getOrPut(k) { HashMap() }.merge(g, 1, Int::plus)
    }
    return acc.map { (k, songs) ->
        // Show the spelling most of your files use.
        GenreGroup(spellings[k]!!.maxBy { it.value }.key, songs.distinctBy { it.id }.sortedBy { sortKey(it.title) })
    }.sortedByDescending { it.songs.size }
}

// ---------------------------------------------------------------- taste and mixes

/** What your listening says about each song. Built once per history change. */
class Taste(
    val plays: Map<Long, Int>,
    val skips: Map<Long, Int>,
    val favourites: Set<Long>,
    /** Listening time per artist (normalized name), for "you like this artist" boosts. */
    val artistMs: Map<String, Long>,
) {
    private val maxArtistMs = (artistMs.values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    /** Higher = more likely to be picked. Never zero, so everything can still turn up. */
    fun score(s: SongEntity): Double {
        val p = plays[s.id] ?: 0
        val k = skips[s.id] ?: 0
        var w = 1.0 + 1.5 * ln(1.0 + p)
        if (s.id in favourites) w += 3.0
        val artistShare = splitArtists(s.artist).maxOf { (artistMs[normalize(it)] ?: 0L).toDouble() } / maxArtistMs
        w += 2.0 * artistShare
        // Songs you keep skipping sink, but don't vanish.
        if (k >= 2 && k > p) w *= 0.25
        return w
    }

    /** A new song by an artist you already like scores higher than a random one. */
    fun discoveryScore(s: SongEntity): Double {
        val artistShare = splitArtists(s.artist).maxOf { (artistMs[normalize(it)] ?: 0L).toDouble() } / maxArtistMs
        return 1.0 + 4.0 * artistShare + if (s.id in favourites) 1.0 else 0.0
    }
}

/**
 * Picks [k] items, each with probability proportional to its weight (Efraimidis–Spirakis).
 * Favourites show up more, but a mix is still different every day.
 */
fun <T> weightedSample(items: List<T>, k: Int, rnd: Random, weight: (T) -> Double): List<T> =
    items.map { it to Math.pow(rnd.nextDouble().coerceAtLeast(1e-12), 1.0 / weight(it).coerceAtLeast(1e-6)) }
        .sortedByDescending { it.second }.take(k).map { it.first }

/**
 * Shuffle that spreads each artist evenly through the list, so you don't get three songs by the
 * same artist in a row (what a plain random shuffle does surprisingly often). Each artist's songs
 * are placed at evenly spaced points with a random start and a little jitter, then everything is
 * sorted by position.
 */
fun spreadShuffle(songs: List<SongEntity>, rnd: Random): List<SongEntity> {
    if (songs.size < 3) return songs.shuffled(rnd)
    val groups = songs.groupBy { normalize(splitArtists(it.artist).first()) }.values
    val placed = ArrayList<Pair<Double, SongEntity>>(songs.size)
    for (g in groups) {
        val n = g.size
        val start = rnd.nextDouble() / n
        g.shuffled(rnd).forEachIndexed { i, s ->
            val jitter = (rnd.nextDouble() - 0.5) * 0.3 / n
            placed += (start + i.toDouble() / n + jitter) to s
        }
    }
    return placed.sortedBy { it.first }.map { it.second }
}

// ---------------------------------------------------------------- similar songs (autoplay, smart shuffle)

/**
 * Songs that fit after [seeds]: sharing an artist counts most, sharing a genre next, and within
 * that your taste decides. Later seeds count more, so autoplay follows where the queue went.
 * If the library has too few tags to find enough, songs you like fill the rest.
 */
fun similarSongs(
    seeds: List<SongEntity>, all: List<SongEntity>, taste: Taste, n: Int, rnd: Random, exclude: Set<Long> = emptySet(),
): List<SongEntity> {
    if (seeds.isEmpty() || n <= 0) return emptyList()
    val artistW = HashMap<String, Double>()
    val genreW = HashMap<String, Double>()
    seeds.forEachIndexed { i, s ->
        val w = 1.0 + i.toDouble() / seeds.size
        splitArtists(s.artist).forEach { artistW.merge(normalize(it), w, Double::plus) }
        splitGenres(s.genre).forEach { genreW.merge(genreKey(it), w, Double::plus) }
    }
    artistW.remove(normalize(UNKNOWN_ARTIST))
    val maxA = artistW.values.maxOrNull() ?: 1.0
    val maxG = genreW.values.maxOrNull() ?: 1.0
    val skip = exclude + seeds.map { it.id }
    val open = all.filter { it.id !in skip }
    val scored = open.mapNotNull { s ->
        val a = splitArtists(s.artist).maxOf { artistW[normalize(it)] ?: 0.0 } / maxA
        val g = (splitGenres(s.genre).maxOfOrNull { genreW[genreKey(it)] ?: 0.0 } ?: 0.0) / maxG
        val sim = 2.0 * a + 1.5 * g
        if (sim > 0) s to sim else null
    }
    val pool = if (scored.size >= n) scored else {
        val have = scored.mapTo(HashSet()) { it.first.id }
        scored + open.filter { it.id !in have }.map { it to 0.15 }
    }
    return spreadShuffle(weightedSample(pool, n, rnd) { (s, sim) -> sim * taste.score(s) }.map { it.first }, rnd)
}

/**
 * Smart Shuffle: the list shuffled, with one suggestion from your library after every
 * [every] songs. Suggestions never repeat a song already in the list.
 */
fun smartShuffle(list: List<SongEntity>, all: List<SongEntity>, taste: Taste, rnd: Random, exclude: Set<Long>, every: Int = 3): List<SongEntity> {
    val base = spreadShuffle(list, rnd)
    val extra = similarSongs(list, all, taste, (list.size / every).coerceIn(1, 60), rnd, exclude + list.map { it.id })
    if (extra.isEmpty()) return base
    val out = ArrayList<SongEntity>(base.size + extra.size)
    var e = 0
    base.forEachIndexed { i, s ->
        out += s
        if ((i + 1) % every == 0 && e < extra.size) out += extra[e++]
    }
    return out
}

// ---------------------------------------------------------------- daylist

enum class DayPart(val label: String) { MORNING("morning"), AFTERNOON("afternoon"), EVENING("evening"), NIGHT("late night") }

fun dayPart(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

// ---------------------------------------------------------------- yearly recap

data class Recap(
    val year: Int,
    val minutes: Long,
    val plays: Int,
    val topSongs: List<Pair<SongEntity, Long>>,
    val topArtists: List<Pair<String, Long>>,
    val topGenre: String?,
    val songs: Int,
    val artists: Int,
    /** Artists you played for the first time this year. */
    val newArtists: Int,
    /** 1..12, the month you listened most. */
    val topMonth: Int,
)

/** Your year in music, from play events (each with the local date it started on). */
fun yearRecap(
    year: Int,
    events: List<com.skarif.kairos.data.db.PlayEventEntity>,
    byId: Map<Long, SongEntity>,
    zone: java.time.ZoneId,
): Recap? {
    fun date(e: com.skarif.kairos.data.db.PlayEventEntity) = java.time.Instant.ofEpochMilli(e.startedAt).atZone(zone).toLocalDate()
    val (thisYear, before) = events.filter { date(it).year <= year }.partition { date(it).year == year }
    val inYear = thisYear.filter { it.songId in byId }
    if (inYear.isEmpty()) return null
    val songMs = inYear.groupBy { it.songId }.mapValues { (_, l) -> l.sumOf { it.listenedMs } }
    val artistMs = HashMap<String, Pair<String, Long>>()
    val genreMs = HashMap<String, Pair<String, Long>>()
    for (e in inYear) {
        val s = byId.getValue(e.songId)
        splitArtists(s.artist).forEach { a -> val k = normalize(a); artistMs[k] = a to ((artistMs[k]?.second ?: 0L) + e.listenedMs) }
        splitGenres(s.genre).forEach { g -> val k = genreKey(g); genreMs[k] = g to ((genreMs[k]?.second ?: 0L) + e.listenedMs) }
    }
    val earlierArtists = before.mapNotNull { byId[it.songId] }.flatMap { splitArtists(it.artist) }.mapTo(HashSet()) { normalize(it) }
    return Recap(
        year = year,
        minutes = inYear.sumOf { it.listenedMs } / 60_000,
        plays = inYear.size,
        topSongs = songMs.entries.sortedByDescending { it.value }.take(5).map { byId.getValue(it.key) to it.value },
        topArtists = artistMs.values.sortedByDescending { it.second }.take(5),
        topGenre = genreMs.values.maxByOrNull { it.second }?.first,
        songs = songMs.size,
        artists = artistMs.size,
        newArtists = artistMs.keys.count { it !in earlierArtists },
        topMonth = inYear.groupBy { date(it).monthValue }.maxBy { (_, l) -> l.sumOf { it.listenedMs } }.key,
    )
}

// ---------------------------------------------------------------- M3U playlists

/** An .m3u8 playlist with relative paths, readable by most players and by Kairos. */
fun toM3u(songs: List<SongEntity>): String = buildString {
    append("#EXTM3U\n")
    for (s in songs) {
        append("#EXTINF:").append(s.durationMs / 1000).append(',').append(s.artist).append(" - ").append(s.title).append('\n')
        append(s.folder).append(s.fileName).append('\n')
    }
}

/**
 * Matches the entries of an .m3u/.m3u8 file to songs on this phone: by path, then file name,
 * then "artist - title" from the #EXTINF line. Returns the matched songs in order, and how many
 * entries could not be found.
 */
fun parseM3u(text: String, all: List<SongEntity>): Pair<List<SongEntity>, Int> {
    val byPath = all.associateBy { (it.folder + it.fileName).lowercase() }
    val byFile = all.groupBy { it.fileName.lowercase() }
    val byTag = all.groupBy { normalize("${it.artist} ${it.title}") }
    val out = ArrayList<SongEntity>()
    var missing = 0
    var info: String? = null
    for (raw in text.lineSequence()) {
        val line = raw.trim().removePrefix("﻿")
        if (line.isEmpty()) continue
        if (line.startsWith("#EXTINF", true)) { info = line.substringAfter(',', ""); continue }
        if (line.startsWith("#")) continue
        val path = line.replace('\\', '/').removePrefix("file://")
        val rel = path.lowercase().let { p ->
            // "/storage/emulated/0/Music/a.mp3" and "../Music/a.mp3" both end with a known relative path.
            byPath.keys.firstOrNull { p.endsWith(it) }
        }
        val song = rel?.let { byPath[it] }
            ?: byFile[path.substringAfterLast('/').lowercase()]?.firstOrNull()
            ?: info?.let { byTag[normalize(it.replace(" - ", " "))]?.firstOrNull() }
        if (song != null) out += song else missing++
        info = null
    }
    return out to missing
}

// ---------------------------------------------------------------- precomputed search index

/**
 * Everything search needs, normalized once when the library changes. Normalizing 2,000 songs on
 * every keystroke (Unicode decomposition + regex, three fields each) is what made typing lag.
 */
class SearchIndex(
    songs: List<SongEntity>,
    albums: List<AlbumGroup> = emptyList(),
    artistNames: List<String> = emptyList(),
    genreNames: List<String> = emptyList(),
) {
    private class Entry(val song: SongEntity, val title: String, val artist: String, val album: String) {
        val hay = "$title $artist $album"
        val titleWords = title.split(' ')
    }
    private val entries = songs.map { Entry(it, normalize(it.title), normalize(it.artist), normalize(it.album)) }
    private val albumKeys = albums.map { it to normalize("${it.title} ${it.artist}") }
    private val artistKeys = artistNames.map { it to normalize(it) }
    private val genreKeys = genreNames.map { it to normalize(it) }

    /** Ranked like [searchSongs]. */
    fun songs(query: String, limit: Int = 300): List<SongEntity> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ')
        return entries.asSequence().filter { e -> words.all { it in e.hay } }
            .map { e ->
                val rank = when {
                    e.title.startsWith(q) -> 0
                    e.titleWords.any { it.startsWith(q) } -> 1
                    q in e.title -> 2
                    q in e.artist -> 3
                    q in e.album -> 4
                    else -> 5
                }
                Triple(e.song, rank, e.title)
            }
            .sortedWith(compareBy({ it.second }, { it.third.length }, { it.third }))
            .take(limit).map { it.first }.toList()
    }

    /** Ids of every song matching all the words, in no particular order (for filtering a list). */
    fun songIds(query: String): Set<Long> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries.mapTo(HashSet()) { it.song.id }
        return entries.filter { e -> words.all { it in e.hay } }.mapTo(HashSet()) { it.song.id }
    }

    private fun <T> match(keys: List<Pair<T, String>>, query: String): List<T> {
        val q = normalize(query)
        val words = q.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return keys.map { it.first }
        return keys.filter { (_, k) -> words.all { it in k } }.sortedBy { (_, k) -> if (k.startsWith(q)) 0 else 1 }.map { it.first }
    }

    fun albums(query: String): List<AlbumGroup> = match(albumKeys, query)
    fun artists(query: String): List<String> = match(artistKeys, query)
    fun genres(query: String): List<String> = match(genreKeys, query)
}

// ---------------------------------------------------------------- matching songs across phones (backup restore)

/**
 * Finds a song from a backup on this phone. MediaStore ids change after a reinstall or on a new
 * phone, so a backup stores each song's path, title, artist and length instead.
 * Path first; then same artist and title with a length within 3 seconds; then title alone if
 * only one song has it.
 */
class SongMatcher(all: List<SongEntity>) {
    private val byPath = all.associateBy { (it.folder + it.fileName).lowercase() }
    private val byTag = all.groupBy { normalize("${it.artist} ${it.title}") }
    private val byTitle = all.groupBy { normalize(it.title) }

    fun find(path: String, title: String, artist: String, durationMs: Long): SongEntity? {
        byPath[path.lowercase()]?.let { return it }
        val close = { s: SongEntity -> durationMs <= 0 || kotlin.math.abs(s.durationMs - durationMs) <= 3_000 }
        byTag[normalize("$artist $title")]?.firstOrNull(close)?.let { return it }
        return byTitle[normalize(title)]?.filter(close)?.singleOrNull()
    }
}

// ---------------------------------------------------------------- library A-Z order

/**
 * The one rule for sorting the library and for its letter headers, so they always agree:
 * punctuation and accents at the start don't count ("(When You…" sorts under W, "Élan" under E),
 * "The"/"A"/"An" are skipped when [ignoreArticles], and anything that doesn't start with A–Z
 * (numbers, Bengali, Hindi…) goes under "#", before A.
 */
fun libraryKey(text: String, ignoreArticles: Boolean = false): String {
    val k = normalize(text)
    return if (ignoreArticles) sortTitle(k) else k
}

fun libraryLetter(text: String, ignoreArticles: Boolean = false): Char {
    val c = libraryKey(text, ignoreArticles).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Sort key: "#" group first, then A–Z, each by [libraryKey]. */
fun librarySortKey(text: String, ignoreArticles: Boolean = false): String =
    (if (libraryLetter(text, ignoreArticles) == '#') "0" else "1") + libraryKey(text, ignoreArticles)
