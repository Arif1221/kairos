# Changelog

Offline builds. Each version is on the [Releases](https://github.com/Arif1221/kairos/releases) page.

## Unreleased
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
