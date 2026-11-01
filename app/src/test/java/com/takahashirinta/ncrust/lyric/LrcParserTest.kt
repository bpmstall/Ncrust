package com.takahashirinta.ncrust.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    private fun texts(lrc: String): List<String> = LrcParser.parse(lrc).map { it.text }

    private fun times(lrc: String): List<Long> = LrcParser.parse(lrc).map { it.timeMs }

    @Test
    fun `parses single tag with centisecond precision`() {
        val lines = LrcParser.parse("[00:12.34]Hello")
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].timeMs)
        assertEquals("Hello", lines[0].text)
    }

    @Test
    fun `parses three digit millisecond fraction`() {
        val lines = LrcParser.parse("[01:05.678]World")
        assertEquals(1, lines.size)
        assertEquals(65_678L, lines[0].timeMs)
        assertEquals("World", lines[0].text)
    }

    /**
     * 老实现的经典缺陷：一行挂多个时间标签时只取第一个 match，
     * 后面的标签会当成正文残留（面板上直接显示 "[00:05.00]"），且 5 秒处不再出现这句词。
     */
    @Test
    fun `expands multiple tags on one line into separate lines`() {
        val lines = LrcParser.parse("[00:01.00][00:05.00]同一句词")
        assertEquals(2, lines.size)
        assertEquals(listOf(1_000L, 5_000L), lines.map { it.timeMs })
        // 两个时间点共用同一句正文，标签不能残留在文本里
        assertEquals(listOf("同一句词", "同一句词"), lines.map { it.text })
    }

    /**
     * 另一个老缺陷：正则强制要求小数部分，`[mm:ss]` 形式的整行会被直接丢弃。
     */
    @Test
    fun `keeps lines whose tag has no fraction`() {
        val lines = LrcParser.parse("[00:07]无毫秒")
        assertEquals(1, lines.size)
        assertEquals(7_000L, lines[0].timeMs)
        assertEquals("无毫秒", lines[0].text)
    }

    /**
     * 同行双语：同一句歌词两侧各挂一个标签、正文各不相同，取各自标签之后的那一截。
     */
    @Test
    fun `splits inline bilingual text by its own tag`() {
        val lines = LrcParser.parse("[00:10.00]原文[00:12.00]译文")
        assertEquals(2, lines.size)
        assertEquals(listOf(10_000L, 12_000L), lines.map { it.timeMs })
        assertEquals(listOf("原文", "译文"), lines.map { it.text })
    }

    @Test
    fun `sorts out of order time tags ascending`() {
        val lrc = """
            [00:20.00]第三句
            [00:05.00]第一句
            [00:12.00]第二句
        """.trimIndent()
        assertEquals(listOf("第一句", "第二句", "第三句"), texts(lrc))
        assertEquals(listOf(5_000L, 12_000L, 20_000L), times(lrc))
    }

    @Test
    fun `drops lines with empty text and lines without any tag`() {
        val lrc = """
            [ar:歌手]
            [00:03.00]
            [00:09.00]有词
            [00:15.00]   
            不是歌词的一行
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(9_000L, lines[0].timeMs)
        assertEquals("有词", lines[0].text)
    }

    /** 现有契约：返回结果始终按 timeMs 升序（歌词面板依赖这个顺序做滚动定位）。 */
    @Test
    fun `result is always sorted by time`() {
        val parsed = LrcParser.parse(
            """
            [00:30.00]c
            [00:10.00]b
            [00:02.00]a
            """.trimIndent()
        )
        assertTrue(parsed.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs })
    }
}
