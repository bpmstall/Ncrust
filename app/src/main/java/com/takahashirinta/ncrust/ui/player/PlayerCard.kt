package com.takahashirinta.ncrust.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.draw.blur
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.takahashirinta.ncrust.library.LibraryManager
import com.takahashirinta.ncrust.network.SongItem
import com.takahashirinta.ncrust.network.CoverUrls
import com.takahashirinta.ncrust.QueueModes
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import com.takahashirinta.ncrust.ui.viewmodel.PlayerViewModel
import io.github.takahashirinta.kanesumi.anim.sokuou.SokuouTweens
import io.github.takahashirinta.kanesumi.controls.MetroDivider
import io.github.takahashirinta.kanesumi.controls.MetroDropdownMenu
import io.github.takahashirinta.kanesumi.controls.MetroDropdownMenuItem
import com.takahashirinta.ncrust.ui.icons.AppIcons
import io.github.takahashirinta.kanesumi.controls.MetroIconButton
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroTypography
import io.github.takahashirinta.kanesumi.core.theme.MetroIcon
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerCard(
    song: SongItem?,
    isPlaying: Boolean,
    screenHeightPx: Float,
    progress: Animatable<Float, AnimationVector1D>,
    totalDragDistancePx: Float = 0f,
    playbackQueue: List<SongItem> = emptyList(),
    currentQueueIndex: Int = -1,
    playMode: Int = 0,
    onPlayPause: () -> Unit,
    onDismiss: () -> Unit,
    onPlayPrevious: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onPlayFromQueue: (Int) -> Unit = {},
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    onSetPlayMode: (Int) -> Unit = {},
    onPlayNothing: () -> Unit = {},
    onSongInfoClick: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onSavePlaylist: () -> Unit = {},
    onNavigateToUser: () -> Unit = {},
    onQualitySelected: (Int) -> Unit = {}
) {
    val hasSong = song != null
    // 初始落大封面: 歌词未就绪时(加载中/确无), 全屏默认看封面而非空歌词面板;
    // lyricsReady 到位后由下方的 LaunchedEffect 自动切回歌词视图。
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    // 歌词视图里的小封面点开「歌手 / 专辑」，不把整张封面做成按钮。
    val density = LocalDensity.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ncrust_settings", 0) }
    val coroutineScope = rememberCoroutineScope()

    val strings = LocalStrings.current
    val playerViewModel: PlayerViewModel = viewModel()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val translatedLyrics by playerViewModel.translatedLyrics.collectAsState()
    val lyricsLoading by playerViewModel.lyricsLoading.collectAsState()
    val lyricsSongId by playerViewModel.lyricsSongId.collectAsState()
    val lyricsNoContentSongId by playerViewModel.lyricsNoContentSongId.collectAsState()
    val showLyricsTranslation by playerViewModel.showLyricsTranslation.collectAsState()
    // 收藏库状态: 当前歌是否已收藏(右下角 加号/对号 切换用)。切歌或操作后刷新。
    var libraryTick by remember { mutableIntStateOf(0) }
    val isSongSaved = remember(song?.id, libraryTick) {
        song?.let { LibraryManager.isSongSaved(context, it.id) } ?: false
    }
    // currentPosition / progress 是 2Hz 更新的 StateFlow，直接传引用给需要的子组件，
    // 让它们在最小作用域（graphicsLayer / Canvas draw / derivedStateOf / 叶子 Text）内订阅，
    // 避免 PlayerCard 本身随位置更新 4Hz 重组
    // duration / qualityIndex / isBuffering downgraded to leaf collect inside FullPlayerControls;
    // subscribing at this scope would force the whole PlayerCard subtree to recompose on song
    // change / buffer flap, dragging in AsyncImage + Column layout for no reason.

    // 宽屏分栏进度：0 = 单栏（封面居中、控件铺满居中），1 = 两栏（左封面+控件 / 右歌词·队列）。
    // 由是否显示歌词/队列驱动；窄屏不读取该值（不触发额外重组）。
    val wideSplit by animateFloatAsState(
        targetValue = if (showLyrics || showQueue) 1f else 0f,
        animationSpec = tween(280, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "widePlayerSplit"
    )

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val dp24px = with(density) { 24.dp.toPx() }
    // 宽屏播放器两栏（Apple Music 式）：左封面 / 右歌词·队列。
    val isWidePlayer = LocalConfiguration.current.screenWidthDp >= 600
    // 宽屏左栏占整宽的比例：随 wideSplit 在 100%(单栏) 与 44%(两栏) 间过渡。窄屏恒为 1。
    val wideLeftFraction = if (isWidePlayer) 1f - 0.56f * wideSplit else 1f
    // 迷你条与顶栏按钮的触觉反馈
    val haptic = LocalHapticFeedback.current
    var ambientBitmap by remember(song?.id) { mutableStateOf<Bitmap?>(null) }
    var ambientColor by remember(song?.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(ambientBitmap) {
        val bitmap = ambientBitmap ?: return@LaunchedEffect
        ambientColor = withContext(Dispatchers.Default) {
            val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            try {
                Palette.from(readable).generate().getDominantColor(0)
                    .let { Color(it).copy(alpha = 1f) }
            } finally {
                if (readable !== bitmap) readable.recycle()
            }
        }
    }

    // 唯一封面 overlay：全屏 ↔ miniBar 始终是同一个 cover，只平滑移动/缩放，绝不消失。
    // 宽屏大图落点由左栏"封面区"实测得到（区域化，分辨率无关）。
    var cardRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var wideCoverCenter by remember { mutableStateOf(Offset.Zero) }
    var wideCoverSizePx by remember { mutableStateOf(0f) }
    // 窄屏大封面落点跟封面区实测走。用整屏高度反推会在切歌/展开时和控件错位。
    var portraitCoverCenter by remember { mutableStateOf(Offset.Zero) }
    val portraitCoverSidePad = with(density) { 32.dp.toPx() }
    var portraitCoverAvailableHeightPx by remember { mutableFloatStateOf(0f) }
    val coverSizePx = if (isWidePlayer) {
        // 实测前用兜底尺寸，避免首帧 1px 让 Coil 按 1px 解码成纯色（重进时尤为明显）。
        if (wideCoverSizePx > 0f) wideCoverSizePx
        else minOf(screenWidthPx * 0.4f, screenHeightPx * 0.5f)
    } else minOf(
        screenWidthPx - portraitCoverSidePad * 2f,
        portraitCoverAvailableHeightPx.takeIf { it > 0f } ?: screenHeightPx
    ).coerceAtLeast(0f)
    val coverSizeDp = with(density) { coverSizePx.toDp() }
    // 外层迷你栏总高 56dp，扣除上下 4dp 后内容槽为 48dp；封面严格贴合槽位。
    val miniBarPadPx = with(density) { 12.dp.toPx() }
    val miniBarInnerPx = with(density) { 6.dp.toPx() }
    val miniCoverHalfPx = with(density) { 24.dp.toPx() }
    val miniScale = miniCoverHalfPx * 2f / coverSizePx
    val statusBarPx = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        .let { with(density) { it.toPx() } }
    val miniCoverCenterX = miniBarPadPx + miniBarInnerPx + miniCoverHalfPx
    val miniCoverCenterY = statusBarPx + with(density) { 4.dp.toPx() + 24.dp.toPx() }
    val largeCoverCenterX = if (isWidePlayer) wideCoverCenter.x
        else portraitCoverCenter.x.takeIf { it > 0f } ?: screenWidthPx / 2f
    val topBarBottomPx = statusBarPx + with(density) { 56.dp.toPx() }
    val largeCoverCenterY = if (isWidePlayer) wideCoverCenter.y
        else portraitCoverCenter.y.takeIf { it > 0f }
            ?: (topBarBottomPx + (screenHeightPx - with(density) { 220.dp.toPx() })) / 2f
    val boundsCenter = coverSizePx / 2f

    // 完全收起时才激活迷你播放栏；derivedStateOf 将重组限制在阈值穿越处
    val miniBarEnabled by remember { derivedStateOf { progress.value < 0.01f } }
    val expandedEnough by remember { derivedStateOf { progress.value > 0.05f } }
    val miniBarInteractionSource = remember { MutableInteractionSource() }
    // 完全展开时才激活收起按钮
    val dismissEnabled by remember { derivedStateOf { progress.value > 0.99f } }
    // AGENTS 约束：进度低于 0.05 时卸载歌词、队列和完整控件；封面 overlay 仍独立保留。
    // 这样收起态不会让隐藏面板继续占布局或命中触摸区域。

    // lyricAnimProgress：0 = 大封面，1 = 小封面；驱动封面缩放 + 内容淡入淡出
    // queueSlideProgress：0 = 歌词位置，1 = 列表位置；仅 b↔c 时动画，其他时 snap
    // 两个 Animatable 均只在 graphicsLayer { } draw 阶段读取，动画帧内零 recompose
    // 初始 0（大封面）：冷启动/splash 后若当前歌无歌词，首帧即大封面，不会"停"在歌词位
    val lyricAnimProgress = remember { Animatable(0f) }
    val queueSlideProgress = remember { Animatable(0f) }
    // 仅在歌词模式下歌词可交互；阈值穿越处各触发一次重组，其余帧零重组
    val lyricsEnabled by remember { derivedStateOf { lyricAnimProgress.value > 0.5f && queueSlideProgress.value < 0.5f } }
    // 卡片基本展开(>90%)时播放器内容区才可交互。面板常挂载、graphicsLayer 只调 alpha,
    // 折叠态下歌词行/队列行在屏幕底部(迷你条与导航栏之间的缝隙)依然命中测试——
    // 用户"在导航栏底部乱按"会点到不可见的歌词行/队列行, 触发 seek/切歌。
    // 展开阈值和 alpha 淡入阈值(0.7)错开, 保证交互只在内容真正可见后开启。
    val cardExpandedForInput by remember { derivedStateOf { progress.value > 0.9f } }

    // ---- 歌词可达性驱动的「大封面 ↔ 歌词视图」自动切换 ----
    // - 切歌瞬间: 旧歌词已被 ViewModel 清空, 直接落大封面, 绝不残留上一首歌词。
    // - 歌词就绪(lyricsReady): 自动切回歌词视图（Apple Music 语义）。
    // - 歌词未就绪(仍在加载/确无): 保持大封面 + 灰按钮。
    // - 用户在队列视图时不打扰。
    // lyricsReady：歌词**属于当前歌**且非加载中。旧歌词残留(切歌过渡)不算就绪——
    // 由 lyricsSongId == song?.id 保证, 否则无歌词的新歌会被误判就绪显示旧歌词。
    val lyricsReady = lyricsSongId == song?.id && !lyricsLoading && lyrics.isNotEmpty()
    // 只有服务端明确答复"确无歌词"时才置灰歌词按钮；加载中/请求失败都保持可点，
    // 失败时点一下即触发重试，而不是一次网络抖动就把按钮永久禁用（issue: 后台被杀重进后按钮灰掉）。
    val lyricsNoContent = lyricsNoContentSongId == song?.id && lyrics.isEmpty()
    val lyricsUnavailable = lyricsNoContent && !lyricsLoading

    LaunchedEffect(song?.id, lyricsReady, lyricsLoading, showQueue) {
        when {
            showQueue -> {}
            lyricsLoading -> {}
            // 确无歌词且在歌词页 → 回大封面
            else -> if (showLyrics && lyricsNoContent) showLyrics = false
        }
    }

    // 折叠态重置为大封面视图，保证再次展开时默认停在封面界面
    LaunchedEffect(Unit) {
        snapshotFlow { progress.value }
            .collect { p ->
                if (p == 0f) {
                    showLyrics = false
                    showQueue = false
                }
            }
    }

    LaunchedEffect(showLyrics, showQueue) {
        when {
            showLyrics -> {
                // 与封面缩小并行
                launch { lyricAnimProgress.animateTo(1f, SokuouTweens.QuickSwitch) }
                // 若当前 queueSlideProgress > 0（来自列表模式），横滑回歌词位置
                if (queueSlideProgress.value > 0.01f) {
                    queueSlideProgress.animateTo(0f, SokuouTweens.SheetDismiss)
                }
            }
            showQueue -> {
                launch { lyricAnimProgress.animateTo(1f, SokuouTweens.QuickSwitch) }
                when {
                    // 已在列表位置（或正在返回），无需再动
                    queueSlideProgress.value > 0.99f -> {}
                    // 来自稳定歌词模式（lyricAnimProgress 已是 1）→ 横滑
                    lyricAnimProgress.value > 0.95f -> {
                        queueSlideProgress.animateTo(1f, SokuouTweens.SheetDismiss)
                    }
                    // 来自大封面模式 → 不横滑，仅随封面缩小淡入
                    else -> queueSlideProgress.snapTo(1f)
                }
            }
            else -> {
                // 切回大封面：等封面展开完成，内容随 lyricAnimProgress 自然淡出
                lyricAnimProgress.animateTo(0f, SokuouTweens.SheetAppear)
                // 封面展开后重置滑动位置，为下次 b→c 准备
                queueSlideProgress.snapTo(0f)
            }
        }
    }

    // 展开动作触发一次歌词定位: 面板常挂载, isVisible 不会翻转, 若不做强制定位,
    // 从 mini bar 拉起后歌词停在旧位置(或用户上次手动滚动的位置)
    var lyricLocateTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { progress.value }
            .distinctUntilChanged { a, b -> (a > 0.9f) == (b > 0.9f) }
            .collect { p ->
                if (p > 0.9f) lyricLocateTrigger++
            }
    }

    // 歌词/队列面板可交互(展开 + 面板在前台)时,面板区域内的纵向手势归内部列表滚动。
    // 根节点的整卡拖拽与兜底消费器都不得抢手势——否则在面板上一滑,整卡被拖走、
    // 列表几乎滚不动(issue #23)。面板外的封面/顶栏/大封面模式仍驱动整卡。
    val isPanelInteractive by remember {
        derivedStateOf {
            (lyricsEnabled || queueSlideProgress.value > 0.5f) && progress.value > 0.7f
        }
    }
    fun isOverPanel(y: Float, x: Float) = isPanelInteractive && y > topBarBottomPx &&
        (!isWidePlayer || x > screenWidthPx / 2f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { cardRootOrigin = it.boundsInRoot().topLeft }
            // Outer modifier → runs last within this node in Main pass (after drag detector below).
            // Consumes remaining events when fully expanded so Scaffold siblings never receive them.
            // 面板区域不吞事件:内部列表需要先拿到未消费的 MOVE 才能滚动。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (progress.value > 0.99f) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos == null || !isOverPanel(pos.y, pos.x)) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            // Inner modifier → runs first within this node in Main pass.
            // Handles drag-to-expand/collapse; runs before the outer consumer so it sees unconsumed MOVE.
            // 仅在有歌（!hasSong = 暂无播放）时可拖拽；用 hasSong 作 key，来了歌后手势重新激活。
            .pointerInput(hasSong) {
                if (!hasSong) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 面板内的纵向手势交给内部 LazyColumn/进度条；横向仍要留下，
                    // 否则歌词页无法横滑切到队列。折叠态没有面板可切，整段让出。
                    val startedOverPanel = isOverPanel(down.position.y, down.position.x)
                    if (startedOverPanel && progress.value <= 0.9f) return@awaitEachGesture
                    val startProgress = progress.value
                    val pointer = down.id
                    val slop = viewConfiguration.touchSlop
                    // 自定义竖直 slop 检测：**忽略消费标志**累积位移。标准
                    // awaitVerticalTouchSlopOrCancellation 一旦看到事件被消费就返回 null——
                    // miniBar 的 clickable 会消费 down/事件, 导致拖拽永远起不来
                    // ("滑动拉起"失效)。这里越界后再 consume, 之后正常 drag。
                    var accY = 0f
                    var accX = 0f
                    var dragging = false
                    // 展开态横向滑动切歌词 ↔ 队列，封面/顶栏和面板内都认。
                    // 纵向仍归整卡拖拽；面板内的纵向让给列表滚动。
                    var horizontal = false
                    // 面板内的横向判定要快过 LazyColumn 的纵向滚动抢手势，
                    // 否则列表先把事件消费掉，横滑永远到不了阈值。
                    val horizontalSlop = if (startedOverPanel) slop / 2f else slop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (!change.pressed) break
                        accY += change.position.y - change.previousPosition.y
                        accX += change.position.x - change.previousPosition.x
                        if (abs(accY) > slop || abs(accX) > horizontalSlop) {
                            val isHorizontalGesture = abs(accX) > horizontalSlop && abs(accX) > abs(accY)
                            // 展开态认横向切换 封面/歌词/队列；折叠态认横向左右滑动切歌
                            horizontal = (progress.value > 0.9f || progress.value < 0.2f) && isHorizontalGesture
                            // 面板内起手又判成纵向：整段让给列表滚动，不消费。
                            if (startedOverPanel && !horizontal) return@awaitEachGesture
                            dragging = true
                            change.consume()
                            break
                        }
                    }
                    if (!dragging) return@awaitEachGesture
                    if (horizontal) {
                        var totalX = accX
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointer } ?: break
                            if (!change.pressed) break
                            change.consume()
                            totalX += change.position.x - change.previousPosition.x
                        }
                        // 48dp：比点按宽，比翻页窄。
                        val threshold = 48.dp.toPx()
                        if (abs(totalX) > threshold) {
                            if (progress.value < 0.2f) {
                                // 折叠态未展开歌曲栏：左右滑动切歌 (左滑下一首，右滑上一首)
                                if (totalX < 0f) {
                                    onPlayNext()
                                } else {
                                    onPlayPrevious()
                                }
                            } else {
                                // 展开态：左滑 封面→歌词→队列；右滑 队列→歌词→封面。一次只走一页。
                                if (totalX < 0f) {
                                    when {
                                        showQueue -> {}
                                        showLyrics -> {
                                            showQueue = true
                                            showLyrics = false
                                        }
                                        else -> showLyrics = true
                                    }
                                } else {
                                    when {
                                        showQueue -> {
                                            showQueue = false
                                            showLyrics = true
                                        }
                                        showLyrics -> showLyrics = false
                                    }
                                }
                            }
                        }
                        return@awaitEachGesture
                    }
                    // 手动拖动循环：同样忽略消费标志读位移, 边拖边 consume
                    // （压制 miniBar clickable 的按压, 让它不会在抬手时误触发展开）。
                    // pointerInput 作用域不能直接调用 Animatable；只保留一个可取消的
                    // snap 任务，避免每个 MOVE 排队，同时让拖动过程即时反馈。
                    var latestProgress = progress.value
                    var progressUpdate: Job? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val dragAmount = change.position.y - change.previousPosition.y
                        if (dragAmount != 0f) {
                            latestProgress = (latestProgress - dragAmount / totalDragDistancePx).coerceIn(0f, 1f)
                            progressUpdate?.cancel()
                            progressUpdate = coroutineScope.launch { progress.snapTo(latestProgress) }
                        }
                    }
                    progressUpdate?.cancel()
                    coroutineScope.launch {
                        progress.snapTo(latestProgress)
                        val target = if (startProgress < 0.5f) {
                            if (progress.value >= 0.5f) 1f else 0f
                        } else {
                            if (progress.value >= 0.75f) 1f else 0f
                        }
                        val remaining = kotlin.math.abs(target - progress.value)
                        progress.animateTo(
                            target,
                            if (target == 1f)
                                tween(
                                    durationMillis = (400 * remaining).roundToInt().coerceIn(120, 400),
                                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                                )
                            else
                                tween(
                                    durationMillis = (260 * remaining).roundToInt().coerceIn(90, 260),
                                    easing = FastOutSlowInEasing
                                )
                        )
                    }
                }
            }
    ) {
        // 全屏纯黑背景。展开时 translationY=0，卡片顶与封面顶/内容区顶等高；
        // 折叠时整体下移一个 statusBar 高，使顶边对齐 miniBar 内容。miniBar 自带
        // statusBarsPadding（内容被下推 statusBar 高），背景若不跟着下移，miniBar
        // 上方就会多出一条 statusBar 高的 surface 色块——即"手机 miniBar 变高一片"。
        // statusBarPx 在车机(WindowInsets=0)为 0，天然不影响车机。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = statusBarPx * (1f - progress.value)
                }
                .background(
                    ambientColor?.let { lerp(LocalMetroColors.current.background, it, 0.06f) }
                        ?: LocalMetroColors.current.background
                )
        )
        if (prefs.getBoolean("dynamic_artwork_background", true)) {
            ambientBitmap?.let { bitmap ->
                Image(
                    painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                        .graphicsLayer {
                            alpha = (progress.value * 0.10f).coerceIn(0f, 0.10f)
                            translationY = statusBarPx * (1f - progress.value)
                        }
                )
            }
        }
         // 折叠态卡背：卡片整体下移后，miniBar 下方露出的是这张黑底（原底部导航/系统栏
        // 位置）。折叠时用 surface 盖住，与 miniBar 同色，避免底部黑块；展开时透明。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (1f - progress.value * 5f).coerceIn(0f, 1f)
                    translationY = statusBarPx * (1f - progress.value)
                }
                .background(LocalMetroColors.current.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .systemBarsPadding()
        ) {
            if (hasSong && expandedEnough) {
                val s = song!!

                // 底部播放控件（窄屏整宽 / 宽屏左栏共用）。常挂载，alpha 只在 draw 阶段调。
                val playerControls: @Composable () -> Unit = {
                    FullPlayerControls(
                        isPlaying = isPlaying,
                        showLyrics = showLyrics,
                        showQueue = showQueue,
                        progressFlow = playerViewModel.progress,
                        positionFlow = playerViewModel.positionStateMs,
                        durationFlow = playerViewModel.duration,
                        qualityIndexFlow = playerViewModel.currentQualityIndex,
                        qualityOptions = strings.qualityOptions,
                        onQualitySelected = onQualitySelected,
                        onPlayPause = onPlayPause,
                        onPlayPrevious = onPlayPrevious,
                        onPlayNext = onPlayNext,
                        onToggleLyrics = {
                            val nowReady = lyricsReady
                            showLyrics = !showLyrics
                            showQueue = false
                            // 未就绪(失败/尚未成功)时点击 = 触发一次重新加载，
                            // 不必切歌才能恢复歌词。
                            if (!nowReady && !lyricsLoading) playerViewModel.retryLyrics()
                        },
                        onToggleQueue = {
                            showQueue = !showQueue
                            showLyrics = false
                        },
                        onAddToLibrary = {
                            val cur = song
                            if (cur != null) {
                                // 已在库 → 移出; 不在库 → 收藏。本地即时生效, 云端异步同步。
                                if (LibraryManager.isSongSaved(context, cur.id)) {
                                    LibraryManager.removeSong(context, cur.id)
                                    Toast.makeText(context, strings.removedFromLibrary, Toast.LENGTH_SHORT).show()
                                } else {
                                    LibraryManager.saveSong(context, cur)
                                    Toast.makeText(context, strings.addedToLibrary, Toast.LENGTH_SHORT).show()
                                }
                                libraryTick++
                            }
                        },
                        isInLibrary = isSongSaved,
                        isBufferingFlow = playerViewModel.isBuffering,
                        onSeek = { fraction ->
                            val dur = playerViewModel.duration.value
                            if (dur > 0) {
                                playerViewModel.seekTo((fraction * dur).toLong())
                            }
                        },
                        lyricsUnavailable = lyricsUnavailable,
                        previousEnabled = playMode != QueueModes.INFINITY || currentQueueIndex > 0,
                        compact = false,
                        landscape = isWidePlayer
                    )
                }

                // 歌词 / 队列双面板（窄屏整宽 / 宽屏右栏共用）。
                val playerPanels: @Composable (Modifier) -> Unit = { panelModifier ->
                    Box(modifier = panelModifier) {
                        // 歌词面板：translationX 从 0 滑至 -screenWidthPx，确保非歌词模式下完全移出屏幕，
                        // 彻底消除与列表面板的命中测试重叠（combinedClickable 忽略 isConsumed 标志）
                        // alpha 用阶梯而非交叉淡化: 切换时源面板瞬时隐藏、目标面板单层全宽滑入,
                        // 每帧只合成一个面板——原先 260ms 内两个全屏面板同时 alpha 混合是
                        // 低端机上左右切换动作的主要 GPU 成本。
                        if (showLyrics) Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = lyricAnimProgress.value
                                    translationX = -queueSlideProgress.value * size.width
                                }
                        ) {
                            if (!isWidePlayer) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, end = 56.dp, top = 8.dp, bottom = 8.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onSongInfoClick
                                        )
                                ) {
                                    MetroText(
                                        s.name,
                                        color = LocalMetroColors.current.onBackground,
                                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val artistsText = s.artists?.joinToString(" / ") { it.name }
                                    if (!artistsText.isNullOrBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        MetroText(
                                            artistsText,
                                            color = LocalMetroColors.current.primary,
                                            style = TextStyle(fontSize = 13.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                // 切歌时旧歌词渐隐、新歌词渐显（Sokuou UWP 缓动）。
                                // key = song?.id：同一首歌的歌词更新不触发 Crossfade, 只换内容。
                                Crossfade(
                                    targetState = song?.id,
                                    animationSpec = SokuouTweens.CoverFade,
                                    label = "LyricsCrossfade"
                                ) { _ ->
                                    val isBuffering by playerViewModel.isBuffering.collectAsState()
                                    val fontSizeIdx = remember { prefs.getInt("lyrics_font_size", 1) }
                                    val fontSize = when (fontSizeIdx) {
                                        0 -> 22.sp
                                        1 -> 26.sp
                                        2 -> 30.sp
                                        3 -> 34.sp
                                        else -> 26.sp
                                    }
                                    val lineHeight = when (fontSizeIdx) {
                                        0 -> 30.sp
                                        1 -> 36.sp
                                        2 -> 42.sp
                                        3 -> 48.sp
                                        else -> 36.sp
                                    }
                                    val centerAligned = remember { prefs.getBoolean("lyrics_align_center", false) }
                                    LyricsView(
                                        songId = song?.id ?: -1L,
                                        lyrics = lyrics,
                                        translatedLyrics = translatedLyrics,
                                        showTranslation = showLyricsTranslation,
                                        positionFlow = playerViewModel.positionStateMs,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        isVisible = true,
                                        forcedLocateTrigger = lyricLocateTrigger,
                                        onSeekToMs = { ms -> playerViewModel.seekTo(ms) },
                                        enabled = lyricsEnabled && cardExpandedForInput,
                                        onUserScrolled = {},
                                        isLoading = lyricsLoading,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        centerAligned = centerAligned,
                                    )
                                }
                            }
                        }

                        // 列表面板：translationX 从 +screenWidthPx 滑至 0，稳定态时完全在屏幕外
                        // alpha 阶梯同上: q < 0.01 时完全透明, 切换只合成单个面板
                        // 面板标题行高度: 队列自动定位要按"整个队列区域"(含标题行)居中
                        var queueHeaderHeightPx by remember { mutableFloatStateOf(0f) }
                        if (showQueue) Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val q = queueSlideProgress.value
                                    alpha = lyricAnimProgress.value * if (q > 0.01f) 1f else 0f
                                    translationX = (1f - q) * size.width
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 60.dp, top = 8.dp, bottom = 8.dp)
                                    .onGloballyPositioned {
                                        queueHeaderHeightPx = it.size.height.toFloat()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetroText(
                                    strings.queueTitle,
                                    color = LocalMetroColors.current.onBackground,
                                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                // 点一下弹出五档直选：循环点五下才转到想要的模式是最常见的误操作。
                                var modeMenuOpen by remember { mutableStateOf(false) }
                                Box {
                                    MetroIconButton(onClick = { modeMenuOpen = true }) {
                                        MetroIcon(
                                            imageVector = playModeIcon(playMode),
                                            contentDescription = strings.playModeButton,
                                            tint = if (playMode != QueueModes.CYCLE) LocalMetroColors.current.primary else LocalMetroColors.current.onBackground,
                                            sizeDp = 24.dp
                                        )
                                    }
                                    MetroDropdownMenu(
                                        expanded = modeMenuOpen,
                                        onDismissRequest = { modeMenuOpen = false },
                                        alignment = Alignment.TopEnd,
                                        maxWidthDp = 240.dp
                                    ) {
                                        PlayModeOption(QueueModes.CYCLE, strings.playModeCycle, playMode) {
                                            modeMenuOpen = false
                                            onSetPlayMode(it)
                                        }
                                        PlayModeOption(QueueModes.SINGLE, strings.playModeSingle, playMode) {
                                            modeMenuOpen = false
                                            onSetPlayMode(it)
                                        }
                                        PlayModeOption(QueueModes.SHUFFLE, strings.playModeShuffle, playMode) {
                                            modeMenuOpen = false
                                            onSetPlayMode(it)
                                        }
                                        PlayModeOption(QueueModes.LINE, strings.playModeLine, playMode) {
                                            modeMenuOpen = false
                                            onSetPlayMode(it)
                                        }
                                        PlayModeOption(QueueModes.INFINITY, strings.playModeInfinity, playMode) {
                                            modeMenuOpen = false
                                            onSetPlayMode(it)
                                        }
                                    }
                                }
                                MetroIconButton(onClick = onSavePlaylist) {
                                    MetroIcon(
                                        imageVector = AppIcons.Add,
                                        contentDescription = strings.saveAsPlaylist,
                                        tint = LocalMetroColors.current.onBackground,
                                        sizeDp = 24.dp
                                    )
                                }
                                // 清空队列: 停播并回到暂无播放态
                                MetroIconButton(onClick = onClearQueue) {
                                    MetroIcon(
                                        imageVector = AppIcons.DeleteSweep,
                                        contentDescription = strings.clearQueue,
                                        tint = LocalMetroColors.current.onBackground,
                                        sizeDp = 24.dp
                                    )
                                }
                            }
                            MetroDivider(color = LocalMetroColors.current.divider)
                            QueueView(
                                queue = playbackQueue,
                                currentIndex = currentQueueIndex,
                                playMode = playMode,
                                isActive = showQueue,
                                interactive = cardExpandedForInput,
                                queueHeaderHeightPx = queueHeaderHeightPx,
                                onPlayIndex = onPlayFromQueue,
                                onRemoveIndex = onRemoveFromQueue,
                                onMove = onMoveInQueue
                            )
                        }
                    }
                }

                if (isWidePlayer) {
                    // 宽屏：Row 两栏，区域化布局（weight 分区，无绝对定位 / 魔法数字）。
                    // 左栏 = 封面区(weight 撑满剩余) + 歌名 + 控件；右栏 = 歌词·队列占满整轴。
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(wideLeftFraction)
                                .fillMaxHeight()
                        ) {
                            // 封面区：只作为"唯一封面 overlay"在宽屏的落点参考——实测其中心与
                            // 尺寸，交给 overlay 封面定位。这里不再放第二个封面，保证全屏 ↔
                            // miniBar 始终是同一个 cover。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        val b = coords.boundsInRoot()
                                        // 封面在左栏封面区内居中（不贴边，视觉更和谐）。
                                        wideCoverCenter = b.center - cardRootOrigin
                                        wideCoverSizePx = minOf(b.width, b.height)
                                    }
                            )
                            // 歌名 / 歌手 + 控件：限宽居中，与封面成组（单栏时不再铺满整宽显得散）。
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 560.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                                        .clickable { onSongInfoClick() }
                                ) {
                                    MetroText(
                                        s.name,
                                        color = LocalMetroColors.current.onBackground,
                                        style = LocalMetroTypography.current.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    MetroText(
                                        s.artists?.joinToString("/") { it.name } ?: "",
                                        color = LocalMetroColors.current.primary,
                                        style = LocalMetroTypography.current.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 560.dp)
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                                ) {
                                    playerControls()
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        // 右栏常挂载（weight 给极小下限，wideSplit=0 时宽度趋 0 但不卸载）：
                        // 避免开关歌词时面板反复 mount/unmount 导致歌词状态丢失/不再重绘。
                        Box(
                            modifier = Modifier
                                .weight((1f - wideLeftFraction).coerceAtLeast(0.0001f))
                                .fillMaxHeight()
                        ) {
                            playerPanels(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val b = coords.boundsInRoot()
                                portraitCoverCenter = b.center - cardRootOrigin
                                portraitCoverAvailableHeightPx = coords.size.height.toFloat()
                            }
                            .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                            .clickable(
                                enabled = progress.value > 0.8f && !showLyrics && !showQueue,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                showLyrics = true
                            }
                    ) {
                        playerPanels(Modifier.fillMaxSize())
                        // 大封面模式下的曲名/歌手信息：overlay 在内容区底部，不占高度。
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .graphicsLayer {
                                    alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) *
                                            (1f - lyricAnimProgress.value)
                                }
                                // 歌名区域可点: 上拉"转到歌手/转到专辑"菜单(类 Apple Music)
                                .clickable { onSongInfoClick() }
                        ) {
                            MetroText(
                                s.name,
                                color = LocalMetroColors.current.onBackground,
                                style = LocalMetroTypography.current.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MetroText(
                                s.artists?.joinToString("/") { it.name } ?: "",
                                color = LocalMetroColors.current.primary,
                                style = LocalMetroTypography.current.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                    ) {
                        playerControls()
                    }
                }
            }
        }

        // 迷你播放栏叠加层：始终在 Composition 中，透明度仅在绘制阶段控制，避免动画期间触发重组
        // 注意：graphicsLayer 必须置于 background 之前，否则展开态时背景不受 alpha 约束而残留黑框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .graphicsLayer {
                    alpha = (1f - progress.value * 5f).coerceIn(0f, 1f)
                }
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(48.dp)
                .background(LocalMetroColors.current.surface)
                .then(
                    // 暂无播放（hasSong=false）时不可点击展开，避免空白播放器被拉起。
                    if (miniBarEnabled && hasSong) Modifier.clickable(
                        interactionSource = miniBarInteractionSource,
                        indication = null,
                        onClick = {
                            coroutineScope.launch {
                                progress.animateTo(
                                    1f,
                                    tween(durationMillis = 240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                                )
                            }
                        }
                    ) else Modifier
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSong) {
                    val s = song!!
                    // 唯一封面 overlay 会落到这个方形占位处（窄屏与宽屏一致）。
                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        MetroText(
                            s.name,
                            color = LocalMetroColors.current.onBackground,
                            style = LocalMetroTypography.current.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MetroText(
                            s.artists?.joinToString("/") { it.name } ?: "",
                            color = LocalMetroColors.current.onSurfaceVariant,
                            style = LocalMetroTypography.current.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (miniBarEnabled) {
                        MetroIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayPause()
                        }) {
                            MetroIcon(
                                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = null,
                                tint = LocalMetroColors.current.onBackground
                            )
                        }
                        MetroIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayNext()
                        }) {
                            MetroIcon(AppIcons.SkipNext, null, tint = LocalMetroColors.current.onBackground)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(96.dp))
                    }
                } else {
                    // 暂无播放: 卡片仍不可拉起(保持既有约束), 但给一个播放键
                    // 直接开始 Infinity——取每日推荐开播, 无需先有队列
                    MetroText(
                        strings.noSongPlaying,
                        color = LocalMetroColors.current.onSurfaceVariant,
                        style = LocalMetroTypography.current.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    if (miniBarEnabled) {
                        MetroIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayNothing()
                        }) {
                            MetroIcon(
                                imageVector = AppIcons.Play,
                                contentDescription = LocalStrings.current.playButton,
                                tint = LocalMetroColors.current.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }
        }

        // 封面叠在最外层。歌词页缩到顶栏时必须裁掉画出顶栏的部分，
        // 否则大图会整条压在歌名和歌词上。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isWidePlayer || lyricAnimProgress.value < 0.5f) 4f else 1f)
                .graphicsLayer {
                    clip = true
                    shape = RectangleShape
                }
        ) {
        // 唯一封面叠加层：全屏 ↔ miniBar 共用这一个 cover，随 progress 平滑位移/缩放。
        // 窄屏大图=封面区实测中心；宽屏大图=左栏封面区实测中心与尺寸。
        if (hasSong) {
            val s = song!!
            // 折叠态封面只占 48dp，但这里一直按整屏宽解码 1080。点单曲展开的那
            // 400ms 里，大图解码和封面缩放挤在同一帧，120Hz 下直接掉到 150ms+。
            // 折叠时请求小图（列表预取已在内存里），展开过半再换大图，解码落在
            // 动画后半段、用户已经看到封面在动的时候。
            val coverUrl = if (progress.value < 0.5f) {
                CoverUrls.small(s.album?.picUrl)
            } else {
                CoverUrls.large(s.album?.picUrl)
            }
            StableCover(
                model = coverUrl,
                contentDescription = null,
                placeholderColor = LocalMetroColors.current.surfaceVariant,
                 onBitmapReady = { ambientBitmap = it },
                modifier = Modifier
                    .size(coverSizeDp)
                    .graphicsLayer {
                        val p = progress.value
                        val normalizedP = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)

                        val targetCenterX = largeCoverCenterX
                        val targetCenterY = largeCoverCenterY
                        val targetScale = 1f

                        val currentCenterX = miniCoverCenterX + normalizedP * (targetCenterX - miniCoverCenterX)
                        val currentCenterY = miniCoverCenterY + normalizedP * (targetCenterY - miniCoverCenterY)
                        val currentBaseScale = miniScale + normalizedP * (targetScale - miniScale)

                        alpha = if (isWidePlayer) {
                            1f
                        } else {
                            maxOf(1f - lyricAnimProgress.value, (1f - p * 5f).coerceIn(0f, 1f))
                        }
                        scaleX = currentBaseScale
                        scaleY = currentBaseScale
                        translationX = currentCenterX - boundsCenter
                        translationY = currentCenterY - boundsCenter
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .clickable(
                        enabled = progress.value > 0.8f && !showLyrics && !showQueue,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showLyrics = true
                    },
                contentScale = ContentScale.Fit
            )
        }
        }

        // 收起按钮叠加层：z 序最高，保证触摸事件不被任何下层元素拦截
        if (hasSong) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(3f)
                    .statusBarsPadding()
                    .height(56.dp)
                    .graphicsLayer { alpha = ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissEnabled) {
                    MetroIconButton(onClick = onDismiss) {
                        MetroIcon(AppIcons.ArrowDown, strings.collapsePlayer, tint = LocalMetroColors.current.onBackground)
                    }
                }
            }
        }
    }
}

// 新封面超过该阈值仍未就绪，才退化为纯色占位（"实在不出来再禁用"）。
private const val COVER_HOLD_MS = 400L

private fun playModeIcon(mode: Int) = when (mode) {
    QueueModes.SINGLE -> AppIcons.RepeatOne
    QueueModes.SHUFFLE -> AppIcons.Shuffle
    QueueModes.LINE -> AppIcons.Queue
    QueueModes.INFINITY -> AppIcons.Infinity
    else -> AppIcons.Repeat
}

/** 播放模式菜单的一项：当前档用主题色标出并带图标，点选即切。 */
@Composable
private fun PlayModeOption(
    mode: Int,
    label: String,
    current: Int,
    onPick: (Int) -> Unit,
) {
    val selected = mode == current
    MetroDropdownMenuItem(
        text = label,
        onClick = { onPick(mode) },
        textColor = if (selected) LocalMetroColors.current.primary else LocalMetroColors.current.onSurface,
        leading = {
            MetroIcon(
                imageVector = playModeIcon(mode),
                contentDescription = null,
                tint = if (selected) LocalMetroColors.current.primary else LocalMetroColors.current.onSurface,
                sizeDp = 20.dp
            )
        }
    )
}

/**
 * 切歌不闪的封面。Coil 的 [AsyncImagePainter] 在 model 变化时先进入 loading 态、
 * 画 placeholder（纯色），新图没秒出就会闪一下占位色。这里改为：
 *  - 记住最近一次成功加载的封面，切歌换图期间先沿用旧图（视觉无缝）；
 *  - 新图在 [COVER_HOLD_MS] 内就绪 → 直接换上新图；
 *  - 超过阈值仍未就绪 → 才退化为占位色。
 */
@Composable
private fun StableCover(
    model: Any?,
    contentDescription: String?,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onBitmapReady: (Bitmap) -> Unit = {},
) {
    val context = LocalContext.current
    // 封面槽是正方形，但网易封面图常比槽更宽。不指定解码尺寸时 Coil 按原图像素
    // 解码，ContentScale.Crop 再从中心裁，左右被切掉，看起来不是原图。按槽的
    // 像素边长解码成正方形，原图完整落进槽里。
    val density = LocalDensity.current
    val request = remember(model, density) {
        val sidePx = with(density) { 360.dp.roundToPx() }
        ImageRequest.Builder(context)
            .data(model)
            .size(sidePx, sidePx)
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = Coil.imageLoader(context),
    )
    val state = painter.state
    // 最近一次成功加载的封面位图（跨切歌保留）。
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 新图超过阈值仍未就绪 → 退化占位色。
    var timedOut by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val s = state
        if (s is AsyncImagePainter.State.Success) {
            (s.result.drawable as? BitmapDrawable)?.bitmap?.let {
                lastBitmap = it
                onBitmapReady(it)
            }
            timedOut = false
        }
    }
    LaunchedEffect(request) {
        timedOut = false
        delay(COVER_HOLD_MS)
        if (painter.state !is AsyncImagePainter.State.Success) timedOut = true
    }

    Box(modifier = modifier) {
        // 底层：切歌后旧图垫底（超阈值退化占位色）。
        val bg = if (timedOut) null else lastBitmap
        if (bg != null) {
            Image(
                painter = remember(bg) { BitmapPainter(bg.asImageBitmap()) },
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        } else {
            Box(Modifier.matchParentSize().background(placeholderColor))
        }
        // 上层：当前请求的 painter。必须真正绘制它，Coil 才会在 onRemembered 里发起
        // 请求；loading 时它不画东西，露出底层旧图/占位色。
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = contentScale,
        )
    }
}
