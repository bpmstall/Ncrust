package com.takahashirinta.ncrust.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRequestOwnershipTest {
    @Test
    fun `old generation cannot publish after song switch`() {
        assertFalse(ownsLyricsRequest(4L, 5L, 101L, 202L))
    }

    @Test
    fun `current generation and song owns response`() {
        assertTrue(ownsLyricsRequest(5L, 5L, 202L, 202L))
    }
}
