# Privacy policy

_Last updated: 26 November 2026_

Kairos is an offline music player made by SK Arif Hossain. This policy explains what the app does with your information.

## What Kairos collects

Nothing. Kairos does not collect, sell or share any personal data, and it contains no analytics, advertising or crash-reporting services.

## Optional: fetching lyrics online

Kairos has the internet permission for one feature, **Fetch lyrics online** (Settings > Lyrics), which is **off** until you turn it on. When it is on and a song has no lyrics of its own, Kairos asks [LRCLIB](https://lrclib.net), a free public lyrics database, for that song. The request contains only the song's title, artist, album name and length, plus the app's name and version. No account, device ID or listening history is sent. LRCLIB's own policies apply to the requests it receives. The lyrics that come back are saved in the app's private storage so each song is fetched once; *Clear downloaded lyrics* deletes them. Turn the setting off and Kairos makes no network requests at all.

## What stays on your phone

To work, Kairos keeps the following in its private storage on your device:

- the list of songs found on your phone (title, artist, album, duration, file location)
- your playlists, favourites and settings
- your listening history (which song, when, and for how long), used for statistics and auto playlists
- the text of the last crash, if the app crashes, so you can choose to copy it into a bug report

This data never leaves your phone. Uninstalling Kairos, or clearing its storage in Android settings, deletes all of it. If Android's own backup is turned on, Android may include this data in your device backup, under Google's backup terms.

## Permissions

| Permission | Why |
|---|---|
| Music and audio (`READ_MEDIA_AUDIO`, or storage on Android 12 and older) | To find and play the songs on your phone |
| Notifications | To show playback controls in the notification |
| Foreground service (media playback) | To keep playing when the app is in the background |
| Change audio settings | For bit-perfect USB output on Android 14+ |
| Internet | Only for the optional *Fetch lyrics online* setting (off by default) |

Folder access for `.lrc` lyrics files is only requested when you choose a folder, and is only used to read lyrics.

## Children

Kairos does not knowingly collect data from anyone, including children.

## Changes

If this policy changes, the new version will be published in this file with a new date.

## Contact

Questions: open an issue at https://github.com/Arif1221/kairos/issues.
