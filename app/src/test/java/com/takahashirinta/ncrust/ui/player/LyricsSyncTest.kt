package com.takahashirinta.ncrust.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSyncTest {
    @Test
    fun `song change snaps a high leftover position back to zero`() {
        assertTrue(shouldSnapLyricPosition(displayMs = 180_000L, sampleMs = 0L))
    }

    @Test
    fun `small forward drift stays extrapolated`() {
        assertFalse(shouldSnapLyricPosition(displayMs = 12_000L, sampleMs = 12_400L))
    }
}
