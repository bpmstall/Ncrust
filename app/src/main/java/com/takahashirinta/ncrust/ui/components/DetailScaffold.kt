package com.takahashirinta.ncrust.ui.components

import com.takahashirinta.ncrust.LocalPlayerExpanded
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.takahashirinta.ncrust.network.CoverUrls
import com.takahashirinta.ncrust.ui.BottomOverlayInsetDp
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import io.github.takahashirinta.kanesumi.anim.sokuou.SokuouTweens
import io.github.takahashirinta.kanesumi.anim.sokuou.rememberMetroFlingBehavior
import io.github.takahashirinta.kanesumi.controls.MetroIconButton
import io.github.takahashirinta.kanesumi.controls.MetroProgressIndicator
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.MetroIcon
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import com.takahashirinta.ncrust.ui.icons.AppIcons
import io.github.takahashirinta.kanesumi.structure.MetroTopScrim
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalDetailTransitionProgress = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * 无边框详情页骨架。
 *
 * 与旧版差异：
 *  - 没有 M3 TopAppBar 的实体 Surface。顶部使用向下渐隐的深色 scrim 提供图标可读性，
 *    避免以往"半透明黑色方块"堆在角落的突兀感——scrim 与内容边缘自然融合，仍无任何 border。
 *  - 返回箭头是裸图标，无背板，Ripple 以图标中心为原点，触控区扩大到 44dp。
 *  - 内容 LazyColumn 从屏幕顶部开始（延伸到 status bar 下），第一项 header() 会填满整个可视区宽度。
 *  - 列表下滑后原本标题平滑过渡为顶层吸顶标题栏，提供返回键、标题与播放全部键。
 *  - 接入 Android Predictive Back 从上往下带模糊的预测性返回手势动画。
 */
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String? = null,
    coverUrl: String? = null,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    // 缓存兜底：调用方已有可立即渲染的内容时传 true，跳过全屏 loader。
    hasCachedContent: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    DetailScaffoldImpl(
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        onBack = onBack,
        isLoading = isLoading,
        hasCachedContent = hasCachedContent,
        error = error,
        onRetry = onRetry,
        onPlayAll = onPlayAll,
        header = header,
        body = null,
        content = content
    )
}

@Composable
fun DetailScaffold(
    title: String,
    subtitle: String? = null,
    coverUrl: String? = null,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    hasCachedContent: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit
) {
    DetailScaffoldImpl(
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        onBack = onBack,
        isLoading = isLoading,
        hasCachedContent = hasCachedContent,
        error = error,
        onRetry = onRetry,
        onPlayAll = null,
        header = header,
        body = body,
        content = null
    )
}

@Composable
private fun DetailScaffoldImpl(
    title: String,
    subtitle: String? = null,
    coverUrl: String? = null,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    hasCachedContent: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    body: (@Composable () -> Unit)? = null,
    content: (LazyListScope.() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = remember(density, screenHeightDp) { with(density) { screenHeightDp.toPx() } }
    val maxTranslateYPx = remember(density) { with(density) { 96.dp.toPx() } }

    // 动态封面色彩氛围提取：根据当前专辑/歌单封面提炼基调色，注入背景与吸顶栏
    var dominantColor by remember(coverUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrEmpty()) {
            dominantColor = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(CoverUrls.small(coverUrl))
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val colorInt = palette.getDominantColor(0)
                    if (colorInt != 0) {
                        withContext(Dispatchers.Main) {
                            dominantColor = Color(colorInt)
                        }
                    }
                }
            } catch (_: Exception) {
                // 异常静默回退默认背景
            }
        }
    }

    // 渐变过渡计算：封面高度为 screenWidthDp，当滚动到标题位置时平滑过渡
    // 采用 lambda provider 避免在滚动时引发 DetailScaffold 及 LazyColumn 全局重组
    val transitionRangePx = remember(density) { with(density) { 80.dp.toPx() } }
    val startOffsetPx = remember(density, screenWidthDp) {
        with(density) { (screenWidthDp - 56.dp).toPx() }
    }
    val transitionProgressProvider = remember(listState, startOffsetPx, transitionRangePx) {
        {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - startOffsetPx) / transitionRangePx).coerceIn(0f, 1f)
        }
    }

    // 预测性返回动画驱动：自然下移 + 缩放淡出，去除模糊
    val backScale = remember { Animatable(1f) }
    val backTranslateY = remember { Animatable(0f) }
    val backAlpha = remember { Animatable(1f) }

    val isPlayerExpanded = LocalPlayerExpanded.current

    PredictiveBackHandler(enabled = !isPlayerExpanded) { events ->
        try {
            events.collect { event ->
                val progress = event.progress
                val targetScale = (1f - progress * 0.05f).coerceIn(0.94f, 1f)
                val targetTranslateY = progress * maxTranslateYPx
                val targetAlpha = (1f - progress * 0.1f).coerceIn(0.85f, 1f)
                backScale.snapTo(targetScale)
                backTranslateY.snapTo(targetTranslateY)
                backAlpha.snapTo(targetAlpha)
            }
            withContext(NonCancellable) {
                launch { backTranslateY.animateTo(screenHeightPx, tween(200, easing = FastOutSlowInEasing)) }
                launch { backAlpha.animateTo(0f, tween(180)) }
            }
            onBack()
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                launch { backScale.animateTo(1f, SokuouTweens.QuickSwitch) }
                launch { backTranslateY.animateTo(0f, SokuouTweens.QuickSwitch) }
                launch { backAlpha.animateTo(1f, SokuouTweens.QuickSwitch) }
            }
        }
    }

    Box(
        // 必须 opaque：nav popExit 动画期间详情页会 slide + 部分透明，
        // 若无 bg 会透视到下方主 tab 屏（返回时 mount/绘制未完成 → 用户看到"低一层残影"）。
        // 主 tab 屏一直在下层挂载，DetailScaffold 显示期间就是它遮住 tab 屏。
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = backScale.value
                scaleY = backScale.value
                translationY = backTranslateY.value
                alpha = backAlpha.value
            }
            .background(LocalMetroColors.current.background)
    ) {
        // 顶部动态封面氛围质感渐变层
        val bgBase = LocalMetroColors.current.background
        val accentTone = dominantColor?.let { lerp(bgBase, it, 0.32f) } ?: bgBase
        if (dominantColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenWidthDp + 80.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to accentTone,
                            0.55f to lerp(bgBase, accentTone, 0.45f),
                            1f to bgBase
                        )
                    )
            )
        }
        val stateKey = when {
            error != null -> DetailScaffoldState.Error
            isLoading && !hasCachedContent -> DetailScaffoldState.Loading
            else -> DetailScaffoldState.Content
        }
        Crossfade(
            targetState = stateKey,
            animationSpec = SokuouTweens.CoverFade,
            modifier = Modifier.fillMaxSize(),
            label = "DetailScaffoldCrossfade"
        ) { state ->
            when (state) {
                DetailScaffoldState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MetroProgressIndicator(color = LocalMetroColors.current.primary)
                    }
                }
                DetailScaffoldState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MetroText(error ?: "", color = Color.Red, style = TextStyle(fontSize = 16.sp))
                            if (onRetry != null) {
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .background(LocalMetroColors.current.primary)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onRetry
                                        )
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MetroText(strings.retry, color = Color.Black)
                                }
                            }
                        }
                    }
                }
                DetailScaffoldState.Content -> {
                    // 入场只动绘制属性。AnimatedVisibility 的 slide 每帧改布局，
                    // 专辑页首屏几十行歌曲会跟着重排，120Hz 上就是打开时的掉帧。
                    val reveal = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { reveal.animateTo(1f, SokuouTweens.QuickSwitch) }
                    if (body != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = reveal.value
                                    if (p < 1f) {
                                        alpha = p
                                        translationY = 12.dp.toPx() * (1f - p)
                                    }
                                }
                        ) {
                            header()
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                body()
                            }
                        }
                    } else if (content != null) {
                        CompositionLocalProvider(LocalDetailTransitionProgress provides transitionProgressProvider) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val p = reveal.value
                                        if (p < 1f) {
                                            alpha = p
                                            translationY = 12.dp.toPx() * (1f - p)
                                        }
                                    },
                                state = listState,
                                contentPadding = PaddingValues(bottom = BottomOverlayInsetDp),
                                flingBehavior = rememberMetroFlingBehavior()
                            ) {
                                item { header() }
                                content()
                            }
                        }
                    }
                }
            }
        }

        // 顶部深色渐隐 scrim：在封面未滚出时保护白色返回图标的可读性，随 transitionProgressProvider() 平滑渐隐
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .graphicsLayer {
                    alpha = (1f - transitionProgressProvider()).coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    )
                )
        )

        // 吸顶标题栏：常驻挂载以规避首次进入时的突发重组掉帧，透明度/位移/背景纯走 GPU 绘制阶段。
        // 外层 MainActivity 容器已处理 systemBars inset，此处贴紧顶边（高度 56dp），不重复 statusBarsPadding() 造成多余间隙。
        val barBgBase = LocalMetroColors.current.background
        val barBgColor = dominantColor?.let { lerp(barBgBase, it, 0.18f) } ?: barBgBase

        // 吸顶栏背景与下沿柔和过渡层：56dp 顶栏底色 + 20dp 向下渐变隐退虚化，使列表内容向上滚入时自然隐去，消除生硬割裂线
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = transitionProgressProvider()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(barBgColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp)
                    .height(20.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to barBgColor,
                            1f to Color.Transparent
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(56.dp)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroIconButton(
                onClick = onBack,
                touchTargetDp = 56.dp
            ) {
                MetroIcon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = strings.back,
                    tint = LocalMetroColors.current.onBackground
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        val p = transitionProgressProvider()
                        alpha = p
                        translationY = (1f - p) * 16.dp.toPx()
                    },
                verticalArrangement = Arrangement.Center
            ) {
                MetroText(
                    text = title,
                    color = LocalMetroColors.current.onBackground,
                    style = TextStyle(fontSize = if (subtitle != null) 15.sp else 18.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    MetroText(
                        text = subtitle,
                        color = LocalMetroColors.current.primary,
                        style = TextStyle(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onPlayAll != null) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val p = transitionProgressProvider()
                        alpha = p
                        scaleX = 0.5f + 0.5f * p
                        scaleY = 0.5f + 0.5f * p
                    }
                ) {
                    PlayAllButton(
                        size = 36.dp,
                        onClick = onPlayAll
                    )
                }
            }
        }
    }
}

/**
 * 顶部渐隐 scrim + 裸图标。scrim 承担"让白色图标在任意封面上可读"的职责，
 * 图标本身无背板。所有二级页面（详情、关于、WebView 登录）共用此组件以保证一致性。
 *
 * 实现委托给库的 MetroTopScrim（同语义：渐隐 scrim + 48dp 触控区 + 直角闪切），
 * 保留本签名以便现有调用方不改。
 */
@Composable
fun TopScrimIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.TopStart
) {
    MetroTopScrim(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        alignment = alignment
    )
}

private enum class DetailScaffoldState { Loading, Error, Content }

/**
 * 无边框详情页头部。
 *
 * 窄屏：封面全宽通铺（fillMaxWidth + aspectRatio 1:1），下方是标题 / 副标题 / info
 * / 右侧大圆播放按钮；封面贴屏幕边缘、无 padding。
 *
 * 宽屏（>=600dp）：改为 Apple Music 式两栏——左侧固定 220dp 方封面，右侧信息列
 * 底部对齐。避免封面在平板上铺成一整屏的正方形。
 */
@Composable
fun DetailHeader(
    coverUrl: String?,
    title: String,
    subtitle: String? = null,
    infoLines: List<String> = emptyList(),
    onPlayAll: (() -> Unit)? = null,
    // 副标题可点击(如专辑页的作曲者 → 跳歌手页), 且无按动反馈
    onSubtitleClick: (() -> Unit)? = null,
    headerActions: @Composable ColumnScope.() -> Unit = {}
) {
    val strings = LocalStrings.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 顶部留出返回按钮触控区的高度（系统栏 inset 已由外层内容容器处理）。
                .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = CoverUrls.large(coverUrl),
                contentDescription = strings.coverDesc,
                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(LocalMetroColors.current.surfaceVariant),
                modifier = Modifier.size(220.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                DetailHeaderInfoRow(
                    title = title,
                    subtitle = subtitle,
                    infoLines = infoLines,
                    onPlayAll = onPlayAll,
                    onSubtitleClick = onSubtitleClick,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = headerActions
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = CoverUrls.large(coverUrl),
            contentDescription = strings.coverDesc,
            // 纯色占位:详情首开封面解码完成前不闪空块(Metro 直接落图,不做 crossfade)
            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(LocalMetroColors.current.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(20.dp))
        DetailHeaderInfoRow(
            title = title,
            subtitle = subtitle,
            infoLines = infoLines,
            onPlayAll = onPlayAll,
            onSubtitleClick = onSubtitleClick,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // 右侧附加操作（如"收藏专辑"按钮），左对齐、无 divider。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = headerActions
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 标题 / 副标题 / info 行 + 右侧大圆播放按钮。宽窄屏共用。 */
@Composable
private fun DetailHeaderInfoRow(
    title: String,
    subtitle: String?,
    infoLines: List<String>,
    onPlayAll: (() -> Unit)?,
    onSubtitleClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val progressProvider = LocalDetailTransitionProgress.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    val progress = progressProvider()
                    alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    translationY = -progress * 14.dp.toPx()
                }
        ) {
            MetroText(
                title,
                color = LocalMetroColors.current.onBackground,
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Normal),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                MetroText(
                    subtitle,
                    color = LocalMetroColors.current.primary,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = if (onSubtitleClick != null)
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSubtitleClick
                        )
                    else Modifier
                )
            }
            infoLines.forEach { line ->
                Spacer(Modifier.height(3.dp))
                MetroText(line, color = LocalMetroColors.current.onSurfaceVariant, style = TextStyle(fontSize = 13.sp))
            }
        }
        if (onPlayAll != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    val progress = progressProvider()
                    alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    scaleX = 1f - 0.3f * progress
                    scaleY = 1f - 0.3f * progress
                }
            ) {
                PlayAllButton(size = 48.dp, onClick = onPlayAll)
            }
        }
    }
}
