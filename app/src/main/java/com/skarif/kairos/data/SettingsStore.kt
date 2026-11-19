package com.skarif.kairos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * STANDARD: Android mixer, 16-bit. Works everywhere.
 * HI_RES: 32-bit float output, so 24-bit files are not cut to 16 bits.
 * BIT_PERFECT: HI_RES plus Android 14's bit-perfect USB mixer. Volume and EQ are bypassed.
 */
enum class OutputMode { STANDARD, HI_RES, BIT_PERFECT }
enum class LyricsPreference { EMBEDDED, LRC_FILE }
enum class ReplayGainMode { OFF, TRACK, ALBUM }
enum class SongSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Date added"), LENGTH("Length"), MOST_PLAYED("Most played")
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorFromArt: Boolean = true,
    val fixedColor: Int = 0xFFBD3657.toInt(),
    val blurBackground: Boolean = true,
    val pureBlack: Boolean = false,
    val readEmbeddedLyrics: Boolean = true,
    val readLrcFiles: Boolean = true,
    val lyricsPreference: LyricsPreference = LyricsPreference.EMBEDDED,
    /** Folders the user granted through the system picker, used to find .lrc files. */
    val lrcFolderUris: Set<String> = emptySet(),
    val outputMode: OutputMode = OutputMode.HI_RES,
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    val excludedFolders: Set<String> = emptySet(),
    val skipShortFiles: Boolean = true,
    val eqEnabled: Boolean = false,
    /** Ten gains in dB, 31 Hz to 16 kHz. */
    val eqGains: List<Float> = List(10) { 0f },
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val onboarded: Boolean = false,
    val songSort: SongSort = SongSort.TITLE,
    val sortDescending: Boolean = false,
) {
    val minDurationMs: Long get() = if (skipShortFiles) 30_000L else 0L
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context, scope: CoroutineScope) {
    private val ds = context.applicationContext.dataStore

    private object K {
        val theme = stringPreferencesKey("theme")
        val colorFromArt = booleanPreferencesKey("color_from_art")
        val fixedColor = intPreferencesKey("fixed_color")
        val blur = booleanPreferencesKey("blur")
        val pureBlack = booleanPreferencesKey("pure_black")
        val embedded = booleanPreferencesKey("lyrics_embedded")
        val lrc = booleanPreferencesKey("lyrics_lrc")
        val lyricsPref = stringPreferencesKey("lyrics_pref")
        val lrcFolders = stringSetPreferencesKey("lrc_folders")
        val output = stringPreferencesKey("output_mode")
        val replayGain = stringPreferencesKey("replay_gain")
        val excluded = stringSetPreferencesKey("excluded_folders")
        val skipShort = booleanPreferencesKey("skip_short")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqGains = stringPreferencesKey("eq_gains")
        val bass = intPreferencesKey("bass_boost")
        val virt = intPreferencesKey("virtualizer")
        val onboarded = booleanPreferencesKey("onboarded")
        val sort = stringPreferencesKey("song_sort")
        val sortDesc = booleanPreferencesKey("sort_desc")
    }

    val settings: StateFlow<Settings> = ds.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun update(change: (Settings) -> Settings) {
        ds.edit { p -> write(p, change(read(p))) }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(p[K.theme], d.themeMode),
            colorFromArt = p[K.colorFromArt] ?: d.colorFromArt,
            fixedColor = p[K.fixedColor] ?: d.fixedColor,
            blurBackground = p[K.blur] ?: d.blurBackground,
            pureBlack = p[K.pureBlack] ?: d.pureBlack,
            readEmbeddedLyrics = p[K.embedded] ?: d.readEmbeddedLyrics,
            readLrcFiles = p[K.lrc] ?: d.readLrcFiles,
            lyricsPreference = enumOr(p[K.lyricsPref], d.lyricsPreference),
            lrcFolderUris = p[K.lrcFolders] ?: d.lrcFolderUris,
            outputMode = enumOr(p[K.output], d.outputMode),
            replayGain = enumOr(p[K.replayGain], d.replayGain),
            excludedFolders = p[K.excluded] ?: d.excludedFolders,
            skipShortFiles = p[K.skipShort] ?: d.skipShortFiles,
            eqEnabled = p[K.eqEnabled] ?: d.eqEnabled,
            eqGains = p[K.eqGains]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 10 } ?: d.eqGains,
            bassBoost = p[K.bass] ?: d.bassBoost,
            virtualizer = p[K.virt] ?: d.virtualizer,
            onboarded = p[K.onboarded] ?: d.onboarded,
            songSort = enumOr(p[K.sort], d.songSort),
            sortDescending = p[K.sortDesc] ?: d.sortDescending,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[K.theme] = s.themeMode.name
        p[K.colorFromArt] = s.colorFromArt
        p[K.fixedColor] = s.fixedColor
        p[K.blur] = s.blurBackground
        p[K.pureBlack] = s.pureBlack
        p[K.embedded] = s.readEmbeddedLyrics
        p[K.lrc] = s.readLrcFiles
        p[K.lyricsPref] = s.lyricsPreference.name
        p[K.lrcFolders] = s.lrcFolderUris
        p[K.output] = s.outputMode.name
        p[K.replayGain] = s.replayGain.name
        p[K.excluded] = s.excludedFolders
        p[K.skipShort] = s.skipShortFiles
        p[K.eqEnabled] = s.eqEnabled
        p[K.eqGains] = s.eqGains.joinToString(",")
        p[K.bass] = s.bassBoost
        p[K.virt] = s.virtualizer
        p[K.onboarded] = s.onboarded
        p[K.sort] = s.songSort.name
        p[K.sortDesc] = s.sortDescending
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
