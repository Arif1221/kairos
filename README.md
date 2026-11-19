# Kairos: offline Android music player (Phase 1)

Jetpack Compose + Media3. Design baseline: the "Music Player — v1 Screens" canvas.

## Open and run
1. Android Studio Ladybug (2024.2) or newer → **Open** this folder.
2. Let Gradle sync (it downloads AGP 8.7, Kotlin 2.1, Media3 1.5.1, Room 2.6.1).
3. Run on a phone or emulator with Android 9 (API 28) or newer, with some music on it.

> This project was written in a sandbox with no access to Google's Maven repository, so it has
> **not been compiled yet**. The Kotlin was syntax-checked and the lyrics engine was run against
> real tagged files, but expect a few small compile fixes on the first sync. Send me any errors.

## What works in Phase 1
| Area | What it does |
|---|---|
| First launch | Asks for audio access (and notifications on Android 13+), then scans |
| Library | MediaStore → Room, songs list, dark header with counts, rescan |
| Folder selection | Include/exclude folders, skip files under 30 s, scan again |
| Playback | Media3 ExoPlayer in a `MediaSessionService`: background play, notification, lock screen, headset buttons, pause when headphones unplug, gapless |
| Now Playing | Album-art hero, favourite, format/duration/queue chips, waveform seek bar, controls |
| Lyrics | Embedded (MP3 USLT/SYLT, FLAC Vorbis, M4A ©lyr) and `.lrc` files; time-synced highlight with auto-scroll; tap a line to jump; per-song timing fix (±0.1 s) |
| Theme | Light / Dark / System, colour from album art (Palette API) or a fixed colour, blurred album background, pure black |
| Audio quality | Hi-res (32-bit float output), bit-perfect USB on Android 14+, Standard; ReplayGain (track/album) |
| EQ engine | 10-band `DynamicsProcessing`, bass boost, virtualizer (settings stored; screen in Phase 2) |
| Stats data | Every listen is saved (`play_events`) for the Stats and Mixes screens |

## Phase 2 (UI on top of tables that already exist)
Home mixes, Stats, Playlists + New playlist, Queue, Equalizer screen, Sleep timer, Album detail, Search, Sort sheet, Menu.

## Architecture
```
ui/            Compose screens, theme (colours from album art), components
  MainViewModel  one ViewModel exposing StateFlows to every screen
playback/      PlaybackService (ExoPlayer + MediaSession), PlayerConnection (MediaController),
               AudioEffects (EQ), BitPerfect (Android 14 USB mixer)
data/          Room database, MediaScanner, SettingsStore (DataStore),
               LyricsRepository, ArtworkRepository (thumbnails + Palette)
lyrics/        Pure Java: TagReader, LrcParser. No Android imports, unit-testable.
PlayerApp      AppContainer: hand-written dependency wiring
```

### Decisions worth knowing
- **Media3 instead of VLC.** Smaller app, native notification and gapless playback, no LGPL rules.
  Add the FFmpeg decoder later only if you hit a format MediaCodec can't play (e.g. APE).
- **Lyrics parser written by hand, in Java.** No tag library handles all three containers on
  Android without trouble. Java so it can be tested without an Android device.
- **`.lrc` needs folder permission.** On Android 13+, audio permission does not cover `.lrc`
  text files. The user allows their music folder once (Settings → Lyrics → Allow a folder).
- **Changing output mode needs an app restart.** Float output is fixed when ExoPlayer is built.
- **Waveform bars are a stable pattern per song**, not the real waveform. Real peaks need decoding the file (Phase 2+).
- **Font:** the design uses Urbanist. Add it via Resource Manager → Downloadable font, then set
  `fontFamily` in `ui/theme/Theme.kt` (`T`).

## Tests
- `app/src/test/.../LrcParserTest.java`: JUnit, runs with `./gradlew test`.
- `tools/lyrics-check/`: builds real MP3/FLAC/M4A files with embedded lyrics (needs ffmpeg and
  `pip install mutagen`) and checks the TagReader against them, plus 2,000 random-garbage files:
  ```
  python3 tools/lyrics-check/make_fixtures.py /tmp/fx
  javac -d /tmp/out app/src/main/java/com/skarif/kairos/lyrics/*.java tools/lyrics-check/LyricsCheck.java
  java -cp /tmp/out LyricsCheck /tmp/fx
  ```

## Getting the APK from GitHub
Every push runs `.github/workflows/build.yml`. Open the repo's **Actions** tab, select the latest
run, and download **kairos-debug-apk** under Artifacts. It is a zip containing `app-debug.apk`.
