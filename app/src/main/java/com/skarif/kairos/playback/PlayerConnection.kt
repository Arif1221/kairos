package com.skarif.kairos.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.data.db.isOnline
import com.skarif.kairos.online.Flavor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(
    val songId: Long? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    /** The queue in the order it will actually play (shuffle included). */
    val queue: List<QueueEntry> = emptyList(),
    /** Where the current song sits in [queue]. */
    val queuePosition: Int = 0,
    val shuffle: Boolean = false,
    /** Player.REPEAT_MODE_OFF / ONE / ALL. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val output: String? = null,
    val usbConnected: Boolean = false,
    val outputNeedsRestart: Boolean = false,
)

/** [index] is the player's media item index; [songId] the song. */
data class QueueEntry(val index: Int, val songId: Long)

/** The UI's handle on the playback service, through a MediaController. */
class PlayerConnection(private val context: Context, private val appScope: CoroutineScope) {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    /**
     * Position is its own flow: it changes 5 times a second while playing, and only the screens
     * that show it (seek bar, lyrics) should redraw that often.
     */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var extras: Bundle = Bundle.EMPTY
    private val transitionListeners = mutableListOf<() -> Unit>()

    fun connect() {
        if (controller != null) return
        appScope.launch(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token)
                .setListener(object : MediaController.Listener {
                    override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                        this@PlayerConnection.extras = extras
                        publish()
                    }
                })
                .buildAsync()
                .await()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) transitionListeners.toList().forEach { it() }
                }
            })
            publish()
        }
    }

    /** Called when a song ends and the next one starts on its own (used by the sleep timer). */
    fun onAutoTransition(listener: () -> Unit) { transitionListeners += listener }
    fun removeAutoTransition(listener: () -> Unit) { transitionListeners -= listener }

    fun play(songs: List<SongEntity>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map { it.toMediaItem() }, if (shuffle) (songs.indices).random() else startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() = controller?.run { if (isPlaying) pause() else { if (playbackState == Player.STATE_IDLE) prepare(); play() } }
    fun pause() = controller?.pause()
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms.coerceAtLeast(0))
    fun playQueueItem(index: Int) = controller?.run { seekToDefaultPosition(index); play() }
    fun moveQueueItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun removeQueueItem(index: Int) = controller?.removeMediaItem(index)

    /** Moves a queue entry to play right after the current song. */
    fun moveToNext(index: Int) = controller?.run {
        val cur = currentMediaItemIndex
        if (index == cur) return@run
        moveMediaItem(index, if (index > cur) cur + 1 else cur)
    }

    fun moveToEnd(index: Int) = controller?.run { if (index != currentMediaItemIndex) moveMediaItem(index, mediaItemCount - 1) }

    /** Removes everything after the current song. */
    fun clearUpcoming() = controller?.run { if (currentMediaItemIndex + 1 < mediaItemCount) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount) }

    /** Removes the songs already played. */
    fun clearPlayed() = controller?.run { if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex) }
    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setVolume(v: Float) = controller?.run { volume = v.coerceIn(0f, 1f) }
    val volume: Float get() = controller?.volume ?: 1f

    /** Adds songs right after the current one. */
    fun playNext(songs: List<SongEntity>) = controller?.run {
        addMediaItems((currentMediaItemIndex + 1).coerceAtMost(mediaItemCount), songs.map { it.toMediaItem() })
    }

    private fun publish() {
        val c = controller ?: return
        _position.value = c.currentPosition
        // Walk the timeline in play order, so the queue matches what you hear when shuffle is on.
        val tl = c.currentTimeline
        val order = ArrayList<Int>(tl.windowCount)
        if (!tl.isEmpty) {
            var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && order.size < tl.windowCount) {
                order += i
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        if (order.size != c.mediaItemCount) { order.clear(); order.addAll(0 until c.mediaItemCount) }
        val next = NowPlaying(
            songId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            queueIndex = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            queue = order.map { i -> QueueEntry(i, c.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L) },
            queuePosition = order.indexOf(c.currentMediaItemIndex).coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            output = extras.getString(PlaybackService.EXTRA_OUTPUT),
            usbConnected = extras.getBoolean(PlaybackService.EXTRA_USB),
            outputNeedsRestart = extras.getBoolean(PlaybackService.EXTRA_NEEDS_RESTART),
        )
        if (next != _state.value) _state.value = next
        // Position only changes on its own while playing, so only tick then.
        if (c.isPlaying && ticker?.isActive != true) {
            ticker = appScope.launch(Dispatchers.Main) {
                while (isActive && controller?.isPlaying == true) {
                    _position.value = controller?.currentPosition ?: 0
                    delay(200)
                }
            }
        }
    }

    private fun SongEntity.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        // Online songs carry their stream Uri across to the service; local ones are resolved there from the id.
        .apply {
            val online = Flavor.online
            if (isOnline && online != null) {
                setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(online.mediaUri(this@toMediaItem)).build())
            }
        }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                // Legacy album-art Uri; still served by MediaStore and used for the notification.
                .setArtworkUri(artUrl?.let(Uri::parse) ?: Uri.parse("content://media/external/audio/albumart/$albumId"))
                .setIsPlayable(true)
                .build(),
        )
        .build()
}
