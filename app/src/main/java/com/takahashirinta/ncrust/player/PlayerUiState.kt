package com.takahashirinta.ncrust.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The immutable playback snapshot consumed by player surfaces. */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val songId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val artwork: String? = null,
    val error: String? = null
)

data class PlayerPosition(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Temporary process-local boundary while PlaybackService is migrated to a controller.
 * ponytail: singleton is deliberate migration debt; inject per playback session once callers are migrated.
 */
object PlayerUiStore {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _position = MutableStateFlow(PlayerPosition())
    val position: StateFlow<PlayerPosition> = _position.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    fun onPlayingChanged(value: Boolean) = update { it.copy(isPlaying = value) }

    fun onBufferingChanged(value: Boolean) = update { it.copy(isBuffering = value) }

    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        val validPosition = positionMs.coerceAtLeast(0L)
        _position.value = PlayerPosition(
            positionMs = validPosition,
            durationMs = durationMs.coerceAtLeast(0L)
        )
        _positionMs.value = validPosition
    }

    fun onSongChanged(now: NowPlaying) = update {
        it.copy(
            songId = now.songId.takeIf { id -> id > 0L },
            title = now.title,
            artist = now.artist,
            artwork = now.artwork.ifEmpty { null },
            isBuffering = false,
            error = null
        )
    }.also {
        _position.value = PlayerPosition()
        _positionMs.value = 0L
    }

    fun onError(message: String?) = update { it.copy(error = message) }

    internal fun resetForTests() {
        _state.value = PlayerUiState()
        _position.value = PlayerPosition()
        _positionMs.value = 0L
    }

    private inline fun update(transform: (PlayerUiState) -> PlayerUiState) {
        _state.value = transform(_state.value)
    }
}
