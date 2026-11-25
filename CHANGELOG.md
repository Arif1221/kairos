# Changelog

Offline builds. Each version is on the [Releases](https://github.com/Arif1221/kairos/releases) page.

## Unreleased
- The blurred album cover is back behind every page, lighter so the cover's colours show.
- Lyrics follow the light or dark theme and sit on the same cover background as the player.
- Home no longer rebuilds its mixes each time you come back to it, or every few seconds while a song plays. Covers on Home appear at once instead of popping in.
- Fixed: tapping play made the cover jump, with a corner sticking out of its frame.
- Fixed: scrolling sometimes stopped dead until you scrolled slowly again. The iOS rubber band is rebuilt on Compose's own overscroll support and now applies to every list.
- Rebuilt on current Android tools: Compose 1.13, Media3 1.11, Kotlin 2.4, AGP 9.5, Android 17 SDK.
- New motion system, iOS style and lighter:
  - pages are opaque and push like iOS: the new page slides over and the old one slides back and dims;
  - tabs switch with a quick fade;
  - opening the player sinks the app back under a dark scrim, and stops drawing it once it's covered.
- Background blur is now done once per song instead of live, so it costs about the same as drawing one picture, on every phone.
- Nothing runs while the app is idle: the seek bar's motion and long-title scrolling only run while the player is on screen (before, the hidden player kept the app drawing nonstop during playback).
- Removed the staggered "appear" animations and list animations in Library and Search.
- Lyrics blur no longer rebuilds every line on each frame.
- Library builds rows ahead of scrolling.
- Fixed a crash on launch when a saved queue was restored.
- The restored queue now loads 2.5 s after launch, in small pieces, so app start doesn't stutter.
- Fixed: tapping a song that wasn't playing still lagged while the player opened. Each queue change makes Android's media controls rebuild the whole queue on the main thread; the rest of a long queue was added in 21 batches right after opening (21 rebuilds). It is now added in two steps, only once the player has finished animating. Restoring the queue at app start loads the same way instead of all at once
- The app's new colours and the cards under the player wait for the opening animation to finish, not a fixed delay
- App start does less at once: the search index, Library sort keys and artist lookup are built when a screen needs them
- Fixed (measured with the performance log): freezes of 100-540 ms at app start and after tapping a song. Media3 mirrored the whole queue (2,000+ songs) to Android's system media controls on the main thread at every queue change. The lock screen and notification still have play/pause, next/previous, seeking and song details; they just no longer get a copy of the queue list
- Fixed: a 362 ms freeze just after the app opened while it switched from default to album colours; the last colours are remembered, so it opens in them
- New: Settings, Performance log. It records every dropped frame with what the app was doing, to share when something lags
- Fixed: the first time you played a song after opening the app, the player still skipped from closed to almost open. It is now built while still closed, then opens on a fresh spring
- Fixed: lyrics jumped up, back down, then glided whenever the line changed (the scrolled list showed one frame before the wave offset)
- Fixed: opening a song still lagged. Every song change saved a listen, and that re-sorted the whole Library and recalculated Statistics on the main thread right as the player opened (and again every 15 s while playing). Both now run in the background, and the Library only re-sorts on plays when sorted by Most played
- Fixed: the player showed the new song about 200 ms after the tap while it looked it up in the database; it now comes straight from the loaded library
- Fixed: the first lyric lines jumped up and slid back when singing started (the list can't scroll above the top; the wave now uses the distance it really moved)
- Fixed: Library letter headers out of order ("W" above "#"); sorting and headers now share one rule that ignores leading punctuation and accents, with numbers and other scripts under "#" first

## 0.8.2
- Fixed: the player still froze half-open, then jumped open, when you tapped a song. The full player is now built in the background as soon as a song is loaded, and the new album colours are applied just after it opens instead of on its first frames
- Fixed: the queue count showed "1 of 1", then "1 of 1601", while a long queue was being added; it now shows the real position and total from the start
- Fixed: the next and previous covers appeared beside the cover while it moved between the player, lyrics and queue
- Fixed: switching tabs briefly showed both pages on top of each other
- Fixed: Home flashed "Play a few songs…" on launch before the mixes were ready
- Lyrics are white on a dark, album-tinted background in light theme too, like Apple Music (grey on pale pink was hard to read)
- Fixed: lyrics jumped when the singing started or a break ended (the dots' space collapsed), and snapped when lines changed quickly
- Song rows, queue rows, the big play button and the info chips press and bounce like every other button
- Search and Statistics use the same header as Home and Library; buttons inside headers no longer blend into the panel

## 0.8.1
- New motion system on iOS-style springs: every movement keeps your finger's speed and can be interrupted
- Pages push in from the right edge over the old one; back is a predictive-back swipe from either edge that scrubs the animation
- Opening the player sinks the app behind it back with rounded corners, like an iOS sheet; a back swipe pulls the player down under your thumb
- The cover settles back while paused and springs forward on play
- Buttons and cards press down instantly, dim slightly, and spring back with a small bounce
- Tabs crossfade with a slight zoom; Library and Search rows glide when filtering or sorting
- Fixed: the queue's now-playing card didn't show the song's cover
- iOS scrolling: lists glide longer and slow down evenly; pulling past either end stretches like a rubber band and springs back, and a fling that hits the end bounces
- Large titles grow slightly as you pull down, and a small title bar slides in once they scroll away (Home, Search, Stats; Library already had one)
- Album, artist and playlist headers scroll with parallax and stretch to fill the space when pulled down
- Tap the current tab again to spring back to the top
- Fixed: tapping a song in a big library made the player jump open in a couple of frames. The tapped song now starts on its own and the rest of the queue is added after the player has opened
- Fixed: the very first play opened the player before it had a song, so it appeared halfway through
- Lyrics like Apple Music: lines move in a staggered wave, lines away from the current one are dimmed and blurred, instrumental breaks show breathing dots, scrolling by hand pauses following and brings every line into focus, and album colours drift slowly behind the words
- Dialogs pop in like iOS alerts; player, lyrics and queue switch with depth; sheets push the app back; pages build in from top to bottom when they open

## 0.8.0
- Fixed: the queue sometimes showed every song as "Unknown" with no covers
- Home redesigned to match Library: same header, plain cards, no empty "0 songs" tiles
- Library filter box on every tab, letter headers in the song list, accent-insensitive search
- Albums split by Android are merged; compilations stay one album; "Pop; Rock" genres split, "Hip-Hop"/"hip hop" merge
- Artist pages with Popular songs and Albums; genre pages with albums and artists
- Shuffle spreads artists out, and the queue shows the real order
- Smart Shuffle: adds a similar song from your library after every three
- Autoplay: when the queue ends, similar songs keep playing
- Hide a song, snooze it for 30 days, or keep it out of recommendations
- Mixes weighted by what you play, favourite and skip; Artist radio for your top artists; a daylist that changes through the day
- Your year in music in Stats
- Playlists: rename, remove and reorder songs, custom cover, import and export .m3u
- Filter Favourites and playlists by genre and save the result as a playlist
- Search finds artists, albums and genres, not just songs; Genres browse tab
- Player: "Playing from", ⋮ menu (song radio, share, share file, playback speed, sleep timer, equalizer, credits, hide, go to artist), tappable artist, elapsed and remaining time, Smart Shuffle and radio buttons
- Under the player: live lyrics preview, up next, about the artist with more of their songs, and song info
- Fixed: typing in the Library filter or Search lagged; search now uses an index built once per library change
- One swipe rule everywhere (72 dp or a quick flick): closing the player, swiping the cover or mini player, queue and library rows. Pull down anywhere on the player to close it
- Backup and restore: favourites, playlists, history, lyrics timing, hidden songs and settings in one file, restorable on a new phone
- Undo bar: removing from the queue or a playlist, clearing up next, hiding, snoozing, unfavouriting and deleting a playlist can be undone
- Swipe actions show their name as you pull; times use even-width digits; card corners match across screens
- Faster first launch and scrolling: baseline profile, list item content types, favourites collected once per list
- Settings: resume where you left off, keep screen on with lyrics, ignore "The"/"A" when sorting, scan again, clear history, about

## 0.7.6
- New app icon, with a themed icon on Android 13+
- Releases signed with a private key; a Play Store bundle (.aab) with every release
- Privacy policy, contributing guide and security policy

## 0.7.5
- Bigger cards on Home and a richer Continue card

## 0.7.4
- The fade behind the mini player follows it and fades as the player opens

## 0.7.3
- Fixed a crash when leaving the queue
- One shared background behind all pages, for smoother transitions
- If the app crashes, the next launch offers a report you can copy

## 0.7.2
- New Home: greeting, quick picks, Jump back in, your mixes, fresh albums
- New seek bar animation; lyrics fill moves smoothly; bigger current lyric line
- Favourites pinned in Playlists; favourite button in the accent colour

## 0.7.0
- One connected player: swipe the mini player up into the full player and back
- Lyrics and queue open inside the player, with the cover moving between them
- Sleep timer, sort and equalizer are bottom sheets

## 0.6.x
- Welcome screen, queue options and swipes, live stats, genres, multi-artist songs, auto playlists, line-by-line karaoke lyrics
