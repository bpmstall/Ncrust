package com.takahashirinta.ncrust.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takahashirinta.ncrust.lyric.LrcLine
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import io.github.takahashirinta.kanesumi.controls.MetroLyricLine
import io.github.takahashirinta.kanesumi.controls.MetroLyricsPanel
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * 歌词面板 —— 渲染交给 Kanesumi 的 MetroLyricsPanel(左对齐大字,每行独立
 * 弹簧单位,静态效果 = 原版 LazyColumn 实现)。这里只做播放器侧对接:
 *
 *  - 位置外推:Service 侧刻意保持 2Hz 广播(见 PlaybackService.startProgressUpdates
 *    注释),若直接喂给面板,跨行检测会滞后最多 500ms。这里从最近的真实采样
 *    锚点做线性外推(播放时 position ≈ 墙钟),但不再逐帧轮询:只在"下一行
 *    时间戳"的时刻唤醒一次并写 displayPosition,跨行判定精确落在时间戳上,
 *    同时避免 withFrameNanos 每帧把 GPU/CPU 拉起来。暂停/隐藏时停掉循环,不空转。
 *    锚点在每次 2Hz 采样到达时重置,外推永远从最近真实值出发。
 *  - tap-to-seek:点击行 → 本地立即跳 position + 回调解绑回调,瞬时反馈。
 */
@Composable
fun LyricsView(
    songId: Long = -1L,
    lyrics: List<LrcLine>,
    translatedLyrics: List<LrcLine> = emptyList(),
    showTranslation: Boolean = true,
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isVisible: Boolean,
    forcedLocateTrigger: Int = 0,
    onSeekToMs: (Long) -> Unit,
    enabled: Boolean = true,
    onUserScrolled: () -> Unit = {},
    // 歌词是否仍在加载：加载中且暂无内容时留空，避免切歌瞬间闪一下"暂无歌词"。
    isLoading: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 36.sp,
    centerAligned: Boolean = false,
) {
    val strings = LocalStrings.current

    // list 引用保持稳定,让面板的测量缓存以它为 key 不被误清。
    // 翻译按时间戳精确对齐原句(网易 tlyric 与原 lrc 时间戳一致),缺失的行不显示译文。
    val metroLines = remember(lyrics, translatedLyrics, showTranslation) {
        val tMap = if (showTranslation) translatedLyrics.associateBy { it.timeMs } else emptyMap()
        lyrics.map { MetroLyricLine(it.timeMs, it.text, translation = tMap[it.timeMs]?.text ?: "") }
    }

    // 订阅位置流:collectAsState 建 State;displayPosition 只在面板 draw/derived
    // 阶段被读,按需写一次也只重算当前行,不触发本 Composable 重组。
    val positionState = positionFlow.collectAsState()
    // 时钟跟歌曲走，不跟歌词列表走。切歌时歌词会先被清空再异步填回，
    // 若只 remember(lyrics)，新列表到达前外推仍挂在上一首的锚点上。
    var displayPosition by remember(songId) { mutableLongStateOf(positionState.value) }
    val anchor = remember(songId) {
        PositionAnchor().apply {
            anchorPosMs = positionState.value
            anchorNanos = System.nanoTime()
        }
    }
    // 外推只在"睡到下一行"时才读锚点。切歌后旧外推循环被取消，但新循环的
    // 第一次醒来可能在新采样到达之前，那时锚点还停在切歌瞬间读到的旧进度上，
    // 新歌歌词就会按上一首的时间戳高亮。采样每次到达都记下锤点，外推醒来时
    // 发现锤点变了就先对齐，不再用旧锚点推下一行。
    var sampleStamp by remember(songId) { mutableIntStateOf(0) }
    var consumedStamp by remember(songId) { mutableIntStateOf(0) }

    // 行时间戳(升序),把"下一次跨行"算成精确唤醒时刻。
    val timestamps = remember(lyrics) { LongArray(lyrics.size) { lyrics[it].timeMs } }

    // 2Hz 采样到达时重置外推锚点(首帧前锚点已就位,避免一帧闪到末尾)。
    LaunchedEffect(Unit) {
        snapshotFlow { positionState.value }.collect { pos ->
            anchor.anchorPosMs = pos
            anchor.anchorNanos = System.nanoTime()
            sampleStamp++
            // 关键：若采样位置回退（切歌、倒退 seek、循环重播）或发生大步跳跃，
            // 立即强行对齐 displayPosition，彻底消除高位旧值锁死歌词的 Bug。
            if (shouldSnapLyricPosition(displayPosition, pos)) {
                displayPosition = pos
            }
        }
    }

    // 外部强制定位（如切歌展开、歌词面板滑出）
    LaunchedEffect(forcedLocateTrigger) {
        if (forcedLocateTrigger != 0) {
            displayPosition = positionState.value
            anchor.anchorPosMs = positionState.value
            anchor.anchorNanos = System.nanoTime()
        }
    }

    // 按需外推:播放、未缓冲且可见时,睡到"下一行时间戳"再写一次 displayPosition,而不是
    // 每帧轮询。缓冲中（isBuffering）必须停掉外推，否则墙钟空转会导致歌词比音频提前数秒。
    // 2Hz 采样会把锚点重置回真实值,所以外推误差不会累积。暂停/隐藏即停。
    LaunchedEffect(isPlaying, isBuffering, isVisible, timestamps) {
        if (!isPlaying || isBuffering || !isVisible) {
            displayPosition = positionState.value
            anchor.anchorPosMs = positionState.value
            anchor.anchorNanos = System.nanoTime()
            return@LaunchedEffect
        }
        while (true) {
            // 睡着期间来过新采样：旧锚点作废，先对齐再算下一行。
            if (consumedStamp != sampleStamp) {
                consumedStamp = sampleStamp
                displayPosition = anchor.anchorPosMs
            }
            val nowMs =
                anchor.anchorPosMs + (System.nanoTime() - anchor.anchorNanos) / 1_000_000L
            val next = nextLineBoundaryAfter(timestamps, nowMs)
            if (next == null) {
                // 已越过最后一行:无跨行可等,低频醒来等采样/seek 改变锚点。
                delay(500)
                continue
            }
            val waitMs = next - nowMs
            // 上限 1s:seek/tap 改变锚点后,最多 1s 重新对齐。
            if (waitMs > 0) delay(waitMs.coerceAtMost(1_000L))
            val extrapolated =
                anchor.anchorPosMs + (System.nanoTime() - anchor.anchorNanos) / 1_000_000L
            // 只在真正越过该边界时写,长间隔里不会每秒写一次状态。
            if (extrapolated >= next && extrapolated >= displayPosition) {
                displayPosition = extrapolated
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (lyrics.isEmpty()) {
            if (!isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MetroText(strings.noLyrics, color = LocalMetroColors.current.onSurfaceVariant, style = TextStyle(fontSize = 18.sp))
                }
            }
        } else MetroLyricsPanel(
            lines = metroLines,
            currentPositionMillis = { displayPosition },
            isVisible = isVisible,
            forcedScrollTrigger = forcedLocateTrigger,
            enabled = enabled,
            fontSize = fontSize,
            lineHeight = lineHeight,
            centerAligned = centerAligned,
            onLineClick = if (enabled) { ms ->
                // 点击行:本地立即定位,不等 2Hz 采样回传,seek 手感即时。
                anchor.anchorPosMs = ms
                anchor.anchorNanos = System.nanoTime()
                displayPosition = ms
                onSeekToMs(ms)
            } else { _ -> },
            onUserScrolled = onUserScrolled,
            // 已播/未播行颜色随主题适配：Kanesumi 面板默认写死 White@0.6 / Gray@0.4，
            // 浅色底上白色已播行会看不见。这里改用语义色。
            pastLineColor = LocalMetroColors.current.onBackground.copy(alpha = 0.6f),
            futureLineColor = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        )

        if (lyrics.isNotEmpty()) {
        // 上下边缘淡出,让歌词从黑里浮出来(上下对称靠近边框)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(LocalMetroColors.current.background, Color.Transparent))
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, LocalMetroColors.current.background))
                )
        )
        }
    }
}

/** 采样回退或跳过 1s 时立刻对齐，避免切歌后旧进度锁住新歌歌词。 */
internal fun shouldSnapLyricPosition(displayMs: Long, sampleMs: Long): Boolean =
    sampleMs < displayMs || kotlin.math.abs(sampleMs - displayMs) > 1_000L

private class PositionAnchor {
    var anchorPosMs: Long = 0L
    var anchorNanos: Long = 0L
}

// 返回严格大于 positionMillis 的最小行时间戳;没有则 null。timestamps 升序。
private fun nextLineBoundaryAfter(timestamps: LongArray, positionMillis: Long): Long? {
    if (timestamps.isEmpty() || positionMillis >= timestamps.last()) return null
    var lo = 0
    var hi = timestamps.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (timestamps[mid] <= positionMillis) lo = mid + 1 else hi = mid
    }
    return timestamps[lo]
}
