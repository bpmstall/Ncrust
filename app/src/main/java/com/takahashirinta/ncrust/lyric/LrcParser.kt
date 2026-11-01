package com.takahashirinta.ncrust.lyric

import androidx.compose.runtime.Immutable

@Immutable
data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    // 毫秒段可选：官方歌词既有 [00:12.34] / [00:12.345]，也有不带小数的 [00:12]。
    // 以前正则强制要求小数，导致不带毫秒的整行被 continue 丢掉。
    private val TAG_REGEX = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\]""")

    fun parse(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()

        for (rawLine in lrcText.lines()) {
            val line = rawLine.trim()
            // 一行可能挂多个时间标签（[00:01.00][00:05.00]同一句歌词）。每个标签都要产出一行：
            // 以前只取第一个 match，后面的标签会当成正文残留在字幕里，面板上会直接显示 "[00:05.00]"。
            val tags = TAG_REGEX.findAll(line).toList()
            if (tags.isEmpty()) continue
            // 行尾正文：多标签共用一句正文（[00:01][00:05]词）时，标签之间是空的，回退到这里。
            val trailingText = line.substring(tags.last().range.last + 1).trim()

            for ((index, tag) in tags.withIndex()) {
                // 同行双语（[00:03.00]原文[00:03.00]译文）则每个标签各取自己那一截正文。
                val segmentEnd = tags.getOrNull(index + 1)?.range?.first ?: line.length
                val text = line.substring(tag.range.last + 1, segmentEnd).trim().ifEmpty { trailingText }
                if (text.isEmpty()) continue

                val min = tag.groupValues[1].toLong()
                val sec = tag.groupValues[2].toLong()
                val ms = when (tag.groupValues[3].length) {
                    0 -> 0L          // 无毫秒段
                    2 -> tag.groupValues[3].toLong() * 10  // 两位小数是百分秒
                    else -> tag.groupValues[3].toLong()
                }
                lines.add(LrcLine(min * 60000 + sec * 1000 + ms, text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}