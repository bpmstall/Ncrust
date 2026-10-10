package com.takahashirinta.ncrust.player

import androidx.media3.common.Player
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerUiStateTest {
    @Before
    fun reset() = PlayerUiStore.resetForTests()

    @After
    fun clear() = PlayerUiStore.resetForTests()

    @Test
    fun `seek and item change publish position immediately`() {
        assertTrue(PlaybackService.shouldPublishPositionNow(Player.DISCONTINUITY_REASON_SEEK))
        assertTrue(PlaybackService.shouldPublishPositionNow(Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertFalse(PlaybackService.shouldPublishPositionNow(Player.DISCONTINUITY_REASON_INTERNAL))
    }

    @Test
    fun `events reduce into a consistent immutable snapshot`() {
        PlayerUiStore.onSongChanged(NowPlaying(42L, "Title", "Artist", "cover", "lossless"))
        PlayerUiStore.onPositionChanged(12_000L, 180_000L)
        PlayerUiStore.onPlayingChanged(true)
        PlayerUiStore.onBufferingChanged(true)
        PlayerUiStore.onError("decode")

        assertEquals(12_000L, PlayerUiStore.position.value.positionMs)
        assertEquals(180_000L, PlayerUiStore.position.value.durationMs)
        assertEquals(
            PlayerUiState(
                isPlaying = true,
                isBuffering = true,
                songId = 42L,
                title = "Title",
                artist = "Artist",
                artwork = "cover",
                error = "decode"
            ),
            PlayerUiStore.state.value
        )

        PlayerUiStore.onSongChanged(NowPlaying(7L, "Next", "Other", "", "standard"))
        assertEquals(0L, PlayerUiStore.position.value.positionMs)
        assertEquals(0L, PlayerUiStore.position.value.durationMs)
        assertEquals(
            PlayerUiState(
                isPlaying = true,
                songId = 7L,
                title = "Next",
                artist = "Other"
            ),
            PlayerUiStore.state.value
        )
    }

    @Test
    fun `position is clamped to valid media bounds`() {
        PlayerUiStore.onPositionChanged(-5L, -10L)
        assertEquals(0L, PlayerUiStore.position.value.positionMs)
        assertEquals(0L, PlayerUiStore.position.value.durationMs)
    }
}
