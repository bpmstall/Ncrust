package com.takahashirinta.ncrust.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * UI-facing transport boundary. PlaybackService remains the only player owner;
 * this object only forwards standard Media3 commands and projects controller state.
 * ponytail: process singleton is migration debt; replace with an injected connection when all callers use it.
 */
object PlaybackConnection {
    private const val TAG = "PlaybackConnection"
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = PlayerUiStore.onPlayingChanged(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) {
            PlayerUiStore.onBufferingChanged(playbackState == Player.STATE_BUFFERING)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull() ?: return
            val metadata = mediaItem.mediaMetadata
            PlayerUiStore.onSongChanged(
                NowPlaying(
                    songId = id,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    artwork = metadata.artworkUri?.toString().orEmpty(),
                    actualLevel = ""
                )
            )
        }
    }

    @Synchronized
    fun connect(context: Context) {
        if (controller != null || future != null) return
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val pending = MediaController.Builder(appContext, token).buildAsync()
        future = pending
        pending.addListener({
            runCatching { pending.get() }
                .onSuccess {
                    controller = it
                    it.addListener(listener)
                    PlayerUiStore.onPlayingChanged(it.isPlaying)
                    PlayerUiStore.onBufferingChanged(it.playbackState == Player.STATE_BUFFERING)
                    if (it.currentMediaItem != null) {
                        listener.onMediaItemTransition(it.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                        PlayerUiStore.onPositionChanged(it.currentPosition, it.duration)
                    }
                }
                .onFailure {
                    future = null
                    Log.w(TAG, "MediaController connection failed", it)
                }
        }, MoreExecutors.directExecutor())
    }

    fun play(): Boolean = controller?.let { it.play(); true } ?: false
    fun pause(): Boolean = controller?.let { it.pause(); true } ?: false
    fun seekTo(positionMs: Long): Boolean = controller?.let { it.seekTo(positionMs); true } ?: false
    fun skipToNext(): Boolean = controller?.let { it.seekToNext(); true } ?: false
    fun skipToPrevious(): Boolean = controller?.let { it.seekToPrevious(); true } ?: false
}
