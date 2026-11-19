# Kairos Online (`online` branch)

> **Two branches, two apps.**
> - `main`: **Kairos**, the offline player (Play Store). All offline features are built there.
> - `online`: **Kairos Online**, `main` plus YouTube Music streaming. Online-only work happens here, and `main` is merged in regularly. Nothing goes from `online` back into `main`.

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

## How the online build is set up
The `online` branch keeps two Gradle flavors so `main`'s code merges in cleanly:
- `online` (`assembleOnlineRelease`): what this branch builds and releases.
- `play`: the offline app, kept only so shared code compiles both ways. It isn't built or released here.

Online build features:
- **Discover** tab (replaces Stats in the dock; Stats is in the menu): "More from" shelves for your most played artists, trending, new releases and mood chips (Bollywood, Lo-fi, Punjabi...).
- **Search** shows YouTube results under your own songs, with a **Songs / Videos** switch. Videos play as **audio only**; video streams are never downloaded. Shorts are skipped.
- **Settings > Streaming**: Data saver (~50 kbps), Normal (~128 kbps) or High (~160 kbps Opus), plus "Data saver on mobile data" (default on). Applies from the next song.
- **Works offline after streaming**: played songs are kept in a 1 GB stream cache (oldest go first) and replay with no connection. Discover shows an **Available offline** shelf when you're offline. Settings > Streaming shows the space used and can clear it.
- **Synced lyrics** for online songs from [LRCLIB](https://lrclib.net) (free, no key).
- **Reliability**: YouTube requests are retried once; a song that fails to load is skipped (up to 3 in a row) instead of stopping playback. Search and Discover show an offline state and reload when the connection returns.
- **Welcome screen** explains streaming and has **Skip, just stream**: no music permission needed, and the app opens on Discover.
- Discover also has **Recently streamed**, **Music videos** and a long-press menu (play next, favourite, add to playlist).
- Online songs can be favourited, added to playlists, queued, and count in Stats. No ReplayGain for them (no file to read).

How it is wired:
- `main/.../online/OnlineMusic.kt` is the only thing shared code knows about. `Flavor.online` is `null` in `src/play` and `YouTubeMusic` in `src/online`.
- Online songs are normal `songs` rows with a **negative id** and `volume = "online"`, so favourites, playlists, stats and the queue need no special cases. They are saved only once played, favourited or added to a playlist, and never appear in the Library.
- Online state that isn't playback (Discover, network, cache) lives in `ui/OnlineFeatures.kt`, not `MainViewModel`, so merges from main stay small.
- Playback goes through `RoutingDataSource`: local Uris go straight to the file, and `kairos-yt:` Uris go through `CacheDataSource` and then `ResolvingDataSource` (the stream URL is only fetched for bytes not in the cache). Each song's quality is pinned the first time it's opened, because seeking reopens the stream at a byte offset and a quality change mid-song would corrupt it.
- Quality is read each time a stream opens (`dataSourceFactory(context, quality)`), so the setting and Wi-Fi/mobile changes apply from the next song. Resolved URLs are cached per song and quality for 3 hours.
- Stream URLs expire, so the player gets `kairos-yt:<videoId>` and a `ResolvingDataSource` swaps in a fresh URL when the song is opened.
- YouTube access goes through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (unofficial). When streaming breaks, bump `newpipeExtractor` in `gradle/libs.versions.toml` first.

> NewPipeExtractor is GPL-3.0, so the online APK must be distributed under the GPL-3.0.

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
**Releases:** download `app-online-release.apk` from the latest `v*-online` release. Pushing to `online` with a new `versionName` publishes one automatically.

**Every push:** open **Actions**, pick the latest run on `online`, and download `kairos-online-apk` (a zip).

## Keeping up with main
```
git checkout online
git merge origin/main
```
Conflicts to expect: `versionCode`/`versionName` (keep online's numbering, ahead of main), `.github/workflows/build.yml` (keep this branch's), and `MainViewModel.mixes` (the favourites mix must read `byId`, which includes saved online songs).
