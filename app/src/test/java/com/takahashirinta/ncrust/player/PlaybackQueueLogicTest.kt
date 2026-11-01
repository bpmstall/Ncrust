package com.takahashirinta.ncrust.player

import com.takahashirinta.ncrust.network.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueLogicTest {

    private fun song(id: Long) = SongItem(
        id = id,
        name = "song$id",
        artists = null,
        album = null,
        duration = null
    )

    private var queue = listOf(song(1), song(2), song(3), song(4))

    @Test
    fun `resolve maps by actual mediaId even when fallback disagrees`() {
        // 播放模式猜的下一个索引是 1，但播放器实际切到了 id=3（索引 2）——以媒体项为准。
        val idx = PlaybackQueueLogic.resolveCurrentIndex(queue, actualSongId = 3L, fallbackIndex = 1)
        assertEquals(2, idx)
    }

    @Test
    fun `resolve falls back when actual id absent from queue`() {
        val idx = PlaybackQueueLogic.resolveCurrentIndex(queue, actualSongId = 999L, fallbackIndex = 2)
        assertEquals(2, idx)
    }

    @Test
    fun `resolve falls back when actual id missing or invalid`() {
        assertEquals(0, PlaybackQueueLogic.resolveCurrentIndex(queue, null, 0))
        assertEquals(3, PlaybackQueueLogic.resolveCurrentIndex(queue, -1L, 3))
    }

    @Test
    fun `resolve clamps fallback into range`() {
        assertEquals(3, PlaybackQueueLogic.resolveCurrentIndex(queue, actualSongId = null, fallbackIndex = 99))
        assertEquals(0, PlaybackQueueLogic.resolveCurrentIndex(queue, actualSongId = null, fallbackIndex = -5))
    }

    @Test
    fun `resolve returns minus one for empty queue`() {
        assertEquals(-1, PlaybackQueueLogic.resolveCurrentIndex(emptyList(), 5L, 0))
    }

    @Test
    fun `first duplicate id wins`() {
        val dup = listOf(song(1), song(2), song(1))
        assertEquals(0, PlaybackQueueLogic.resolveCurrentIndex(dup, actualSongId = 1L, fallbackIndex = 2))
    }

    @Test
    fun `realign finds shuffled position of mapped index`() {
        val shuffled = listOf(2, 0, 3, 1)
        assertEquals(2, PlaybackQueueLogic.realignShuffledPosition(shuffled, mappedIndex = 3, fallbackPosition = 0))
    }

    @Test
    fun `realign keeps fallback when mapped index not in shuffled list`() {
        val shuffled = listOf(2, 0, 3, 1)
        assertEquals(1, PlaybackQueueLogic.realignShuffledPosition(shuffled, mappedIndex = 99, fallbackPosition = 1))
    }

    @Test
    fun `realign empty shuffled list returns zero`() {
        assertEquals(0, PlaybackQueueLogic.realignShuffledPosition(emptyList(), mappedIndex = 2, fallbackPosition = 3))
    }

    // ---------- indexAfterRemoval：删除队列项后的游标修正 ----------
    // 队列 [A,B,C] 播 B（index=1）时删掉 A：B 左移到 0，游标必须变 0，
    // 否则队列指向 C 而音频还在 B（违反核心不变量）。

    @Test
    fun `removal before current shifts index left by one`() {
        assertEquals(0, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 1, removedIndex = 0, newSize = 2))
        assertEquals(1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 2, removedIndex = 0, newSize = 2))
    }

    @Test
    fun `removal after current keeps index unchanged`() {
        assertEquals(0, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 0, removedIndex = 1, newSize = 3))
        assertEquals(1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 1, removedIndex = 2, newSize = 3))
    }

    @Test
    fun `removal of current item lands on the next one`() {
        // 删掉正在播的那首：音频仍持有它，游标原地落到"下一首"。
        assertEquals(1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 1, removedIndex = 1, newSize = 3))
        // 删的是最后一首且它就是当前项：夹到新的末位。
        assertEquals(2, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 3, removedIndex = 3, newSize = 3))
    }

    @Test
    fun `removal leaving empty queue returns minus one`() {
        assertEquals(-1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 0, removedIndex = 0, newSize = 0))
        assertEquals(-1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = -1, removedIndex = 0, newSize = 0))
    }

    @Test
    fun `removal with single item queue returns minus one`() {
        assertEquals(-1, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 0, removedIndex = 0, newSize = 0))
    }

    @Test
    fun `removal result is always clamped into new range`() {
        // 游标原本越界（如外部替换过队列）：结果必须落在合法区间而不是抛错或越界。
        assertEquals(0, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 9, removedIndex = 0, newSize = 1))
        assertEquals(2, PlaybackQueueLogic.indexAfterRemoval(currentIndex = 9, removedIndex = 5, newSize = 3))
    }
}
