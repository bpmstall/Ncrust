package com.takahashirinta.ncrust.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.takahashirinta.kanesumi.anim.sokuou.rememberMetroFlingBehavior
import io.github.takahashirinta.kanesumi.controls.MetroDivider
import io.github.takahashirinta.kanesumi.controls.MetroSelectorFlyout
import io.github.takahashirinta.kanesumi.controls.MetroSwitch
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroColors
import io.github.takahashirinta.kanesumi.core.theme.LocalMetroTypography
import io.github.takahashirinta.kanesumi.core.theme.MetroIcon
import io.github.takahashirinta.kanesumi.core.theme.MetroText
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takahashirinta.ncrust.auth.CookieManager
import com.takahashirinta.ncrust.cache.ContentCache
import com.takahashirinta.ncrust.lyric.LyricsCache
import android.content.Context
import android.widget.Toast
import com.takahashirinta.ncrust.network.PlaylistApi
import com.takahashirinta.ncrust.network.RetrofitClient
import com.takahashirinta.ncrust.power.BackgroundActivity
import com.takahashirinta.ncrust.ui.BottomOverlayInsetDp
import com.takahashirinta.ncrust.ui.components.QrAuthorizeScreen
import com.takahashirinta.ncrust.ui.components.QrLoginDialog
import com.takahashirinta.ncrust.ui.icons.AppIcons
import com.takahashirinta.ncrust.ui.i18n.LocalStrings
import com.takahashirinta.ncrust.ui.i18n.LanguagePreset
import com.takahashirinta.ncrust.ui.i18n.getSavedLanguageCode
import com.takahashirinta.ncrust.ui.i18n.languagePresets
import com.takahashirinta.ncrust.ui.theme.ThemeColorSelector
import com.takahashirinta.ncrust.ui.theme.ThemeMode
import com.takahashirinta.ncrust.ui.theme.themeColorPresets
import com.takahashirinta.ncrust.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UserScreen(
    onOpenAbout: () -> Unit = {},
    themeIndex: Int = 0,
    onThemeChange: (Int) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onShowWebLogin: () -> Unit = {},
    refreshTrigger: Int = 0,
    onLanguageChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var showAccountDialog by remember { mutableStateOf(false) }
    var showQrLogin by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var coverCacheSize by remember { mutableStateOf(0L) }
    var lyricsCacheSize by remember { mutableStateOf(0L) }
    var totalCacheSize by remember { mutableStateOf(0L) }

    suspend fun updateCacheSizes() {
        val (cover, lyrics, total) = withContext(Dispatchers.IO) {
            val coverBytes = coil.Coil.imageLoader(context).diskCache?.size ?: 0L
            val lyricsBytes = LyricsCache.sizeBytes(context)
            val totalBytes = currentCacheSize(context) + lyricsBytes
            Triple(coverBytes, lyricsBytes, totalBytes)
        }
        coverCacheSize = cover
        lyricsCacheSize = lyrics
        totalCacheSize = total
    }

    LaunchedEffect(Unit) {
        updateCacheSizes()
    }
    var hasCookie by remember { mutableStateOf(CookieManager.hasCookie(context)) }
    // 扫码登录为平板 / 大屏独占：手机端未登录点头像仍直接进 WebView 官方登录页。
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600

    var userProfile by remember { mutableStateOf<PlaylistApi.UserProfile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("ncrust_settings", 0) }
    val playerViewModel: PlayerViewModel = viewModel()
    var wifiQuality by remember { mutableIntStateOf(prefs.getInt("wifi_quality", 3)) }
    var mobileQuality by remember { mutableIntStateOf(prefs.getInt("mobile_quality", 1)) }
    var lowPowerMode by remember { mutableStateOf(prefs.getBoolean("low_power_mode", false)) }
    var gaplessEnabled by remember { mutableStateOf(prefs.getBoolean("gapless_playback", true)) }
    var lyricsTranslation by remember { mutableStateOf(prefs.getBoolean("lyrics_translation", true)) }
    var lyricsFontSize by remember { mutableIntStateOf(prefs.getInt("lyrics_font_size", 1)) }
    var lyricsAlignCenter by remember { mutableStateOf(prefs.getBoolean("lyrics_align_center", false)) }
    var lyricsCompactControls by remember { mutableStateOf(prefs.getBoolean("lyrics_compact_controls", true)) }
    var dynamicArtworkBackground by remember { mutableStateOf(prefs.getBoolean("dynamic_artwork_background", true)) }
    var modernRoundedCorners by remember { mutableStateOf(prefs.getBoolean("modern_rounded_corners", true)) }
    var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var lyricsKeepScreenOn by remember { mutableStateOf(prefs.getBoolean("lyrics_keep_screen_on", false)) }

    var selectedLanguageCode by remember { mutableStateOf(getSavedLanguageCode(context)) }

    fun loadProfile() {
        if (!CookieManager.hasCookie(context)) {
            userProfile = null
            hasCookie = false
            return
        }
        coroutineScope.launch {
            isLoadingProfile = true
            try {
                val profile = PlaylistApi.getUserProfile()
                // 服务端对失效 cookie 会返回空 account/profile → userId=0。
                // 此时判定为已过期，主动清除本地 cookie，避免 UI 卡在 "UID: 0"。
                if (profile.userId == 0L) {
                    CookieManager.clearCookie(context)
                    RetrofitClient.updateCookie(null)
                    hasCookie = false
                    userProfile = null
                } else {
                    userProfile = profile
                    hasCookie = true
                }
            } catch (_: Exception) {
                userProfile = null
            } finally {
                isLoadingProfile = false
            }
        }
    }

    LaunchedEffect(Unit) { loadProfile() }
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            hasCookie = CookieManager.hasCookie(context)
            loadProfile()
        }
    }

    if (showAccountDialog) AccountDialog(
        userProfile = userProfile,
        onDismiss = { showAccountDialog = false },
        onScanAuthorize = {
            showAccountDialog = false
            showScanner = true
        },
        onLogout = {
            CookieManager.clearCookie(context)
            RetrofitClient.updateCookie(null)
            hasCookie = false
            userProfile = null
            showAccountDialog = false
        }
    )

    // 手机端扫码授权：扫平板登录二维码 → 解析 unikey → 局域网回传本机 cookie。
    // 连接/成功/失败状态都在扫码页内呈现，不再扫到即关闭。
    if (showScanner) QrAuthorizeScreen(
        onAuthorized = { showScanner = false },
        onClose = { showScanner = false }
    )

    if (showQrLogin) QrLoginDialog(
        onLoginSuccess = { cookie ->
            CookieManager.saveCookie(context, cookie)
            RetrofitClient.updateCookie(cookie)
            hasCookie = true
            showQrLogin = false
            loadProfile()
        },
        onGenericLogin = {
            showQrLogin = false
            onShowWebLogin()
        },
        onDismiss = { showQrLogin = false }
    )

    if (showClearCacheConfirm) ClearCacheConfirmDialog(
        onConfirm = {
            showClearCacheConfirm = false
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    ContentCache.clearAll()
                    LyricsCache.clear(context)
                    runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
                    runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }
                    runCatching {
                        context.cacheDir?.let { dir ->
                            dir.listFiles()
                                ?.filter { it.name == "WebView" || it.name == "http" || it.name == "image_cache" }
                                ?.forEach { it.deleteRecursively() }
                        }
                    }
                }
                updateCacheSizes()
                Toast.makeText(context, strings.cacheCleared, Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = { showClearCacheConfirm = false }
    )

    // 宽屏设置/资料内容居中限宽（上限 720dp），避免设置行横跨平板。
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxHeight(),
        contentPadding = PaddingValues(bottom = BottomOverlayInsetDp),
        flingBehavior = rememberMetroFlingBehavior()
    ) {
        // Groove 大字页头。
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            ) {
                MetroText(
                    strings.tabUser,
                    color = LocalMetroColors.current.onBackground,
                    style = LocalMetroTypography.current.pageHeading,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Profile 块。整块可点：已登录 → 账户管理；未登录 → 直接进 WebView 登录。
        item {
            ProfileBlock(
                isLoading = isLoadingProfile,
                profile = userProfile,
                notLoggedInText = strings.notLoggedIn,
                loginHintText = strings.loginHint,
                uidLabel = strings.uidLabel(userProfile?.userId?.toString() ?: ""),
                onClick = {
                    when {
                        hasCookie -> showAccountDialog = true
                        // 平板/大屏：原生扫码（官方 App 扫），下面再给「通用登录」退回 WebView
                        isWideLayout -> showQrLogin = true
                        else -> onShowWebLogin()
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        // 音质
        item {
            SectionHeader(AppIcons.AudioQuality, strings.qualitySectionTitle)
            SettingsCard {
                MetroDropdownRow(
                    label = strings.wifiQualityLabel,
                    selectedIndex = wifiQuality,
                    options = strings.qualityOptions,
                    onSelect = {
                        wifiQuality = it
                        prefs.edit().putInt("wifi_quality", it).apply()
                    }
                )
                CardDivider()
                MetroDropdownRow(
                    label = strings.mobileQualityLabel,
                    selectedIndex = mobileQuality,
                    options = strings.qualityOptions,
                    onSelect = {
                        mobileQuality = it
                        prefs.edit().putInt("mobile_quality", it).apply()
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 播放与系统集成
        item {
            SectionHeader(AppIcons.Playback, strings.playbackSectionTitle)
            SettingsCard {
                SettingSwitchRow(
                    title = strings.gaplessSectionTitle,
                    description = strings.gaplessDescription,
                    checked = gaplessEnabled,
                    onCheckedChange = {
                        gaplessEnabled = it
                        prefs.edit().putBoolean("gapless_playback", it).apply()
                        playerViewModel.refreshGaplessSetting()
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // 歌词与显示
        item {
            SectionHeader(AppIcons.Lyrics, "歌词与交互")
            SettingsCard {
                SettingSwitchRow(
                    title = strings.lyricsTranslationLabel,
                    checked = lyricsTranslation,
                    onCheckedChange = {
                        lyricsTranslation = it
                        prefs.edit().putBoolean("lyrics_translation", it).apply()
                        playerViewModel.setLyricsTranslation(it)
                    }
                )
                CardDivider()
                MetroDropdownRow(
                    label = "歌词大小",
                    selectedIndex = lyricsFontSize,
                    options = listOf("紧凑 (22sp)", "标准 (26sp)", "大号 (30sp)", "特大 (34sp)"),
                    onSelect = {
                        lyricsFontSize = it
                        prefs.edit().putInt("lyrics_font_size", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "歌词居中对齐",
                    description = "关闭为居左对齐，开启为居中对齐",
                    checked = lyricsAlignCenter,
                    onCheckedChange = {
                        lyricsAlignCenter = it
                        prefs.edit().putBoolean("lyrics_align_center", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "歌词模式精简控制栏",
                    description = "打开歌词时缩小底部播放控制按钮，留出更大视野与显示行数",
                    checked = lyricsCompactControls,
                    onCheckedChange = {
                        lyricsCompactControls = it
                        prefs.edit().putBoolean("lyrics_compact_controls", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "看歌词时屏幕常亮",
                    checked = lyricsKeepScreenOn,
                    onCheckedChange = {
                        lyricsKeepScreenOn = it
                        prefs.edit().putBoolean("lyrics_keep_screen_on", it).apply()
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // 视觉与性能
        item {
            SectionHeader(AppIcons.Visual, "视觉与性能")
            SettingsCard {
                SettingSwitchRow(
                    title = strings.lowPowerModeTitle,
                    description = strings.lowPowerModeDesc,
                    checked = lowPowerMode,
                    onCheckedChange = {
                        lowPowerMode = it
                        prefs.edit().putBoolean("low_power_mode", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "动态封面氛围模糊背景",
                    description = "从当前歌曲封面提取色彩与柔和高斯模糊，在全屏播放器背景渲染",
                    checked = dynamicArtworkBackground,
                    onCheckedChange = {
                        dynamicArtworkBackground = it
                        prefs.edit().putBoolean("dynamic_artwork_background", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "Fluent Design 质感小圆角与微边框",
                    description = "卡片与封面采用 8dp/6dp 小圆角，叠加 0.75dp 半透明微边框描边提升层次质感",
                    checked = modernRoundedCorners,
                    onCheckedChange = {
                        modernRoundedCorners = it
                        prefs.edit().putBoolean("modern_rounded_corners", it).apply()
                    }
                )
                CardDivider()
                SettingSwitchRow(
                    title = "触觉轻震反馈",
                    description = "播放、暂停、切歌与模式切换时提供轻微触觉反馈",
                    checked = hapticFeedback,
                    onCheckedChange = {
                        hapticFeedback = it
                        prefs.edit().putBoolean("haptic_feedback", it).apply()
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // 外观：主题模式 + 主题色 + 语言
        item {
            SectionHeader(AppIcons.Language, "外观与个性化")
            SettingsCard {
                ThemeModeSelector(
                    selected = themeMode,
                    labels = Triple(
                        strings.themeModeSystem,
                        strings.themeModeDark,
                        strings.themeModeLight
                    ),
                    onSelect = onThemeModeChange
                )
                CardDivider()
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ThemeColorSelector(
                        selectedIndex = themeIndex,
                        presets = themeColorPresets,
                        onSelect = onThemeChange
                    )
                }
                CardDivider()
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MetroLanguageDropdown(
                        selectedCode = selectedLanguageCode,
                        presets = languagePresets,
                        onSelect = { code ->
                            if (code != selectedLanguageCode) {
                                selectedLanguageCode = code
                                onLanguageChange(code)
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 存储与缓存管理
        item {
            SectionHeader(AppIcons.Storage, strings.storageSectionTitle)
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroText(
                        strings.coverCacheLabel(coverCacheSize),
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroText(
                        strings.lyricsCacheLabel(lyricsCacheSize),
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearCacheConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroText(
                        strings.cacheSizeLabel(totalCacheSize),
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f)
                    )
                    MetroText(
                        strings.clearCache,
                        color = LocalMetroColors.current.primary,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 系统与关于
        item {
            SectionHeader(AppIcons.Settings, "系统与关于")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(BackgroundActivity.requestIntent(context))
                            }.onFailure {
                                runCatching {
                                    context.startActivity(BackgroundActivity.appDetailsIntent(context))
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroText(
                        strings.batteryTitle,
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 14.5.sp),
                        modifier = Modifier.weight(1f)
                    )
                    MetroIcon(
                        AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = LocalMetroColors.current.onSurfaceVariant,
                        sizeDp = 18.dp,
                    )
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAbout)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroText(
                        strings.aboutButton,
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 14.5.sp),
                        modifier = Modifier.weight(1f)
                    )
                    MetroIcon(
                        AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = LocalMetroColors.current.onSurfaceVariant,
                        sizeDp = 18.dp,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                color = LocalMetroColors.current.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 0.75.dp,
                color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
    ) {
        content()
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(LocalMetroColors.current.surfaceVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetroIcon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalMetroColors.current.primary,
            sizeDp = 18.dp
        )
        Spacer(Modifier.width(8.dp))
        MetroText(
            title,
            color = LocalMetroColors.current.onBackground,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Profile 块：64dp 头像 + 现代化卡片容器 + 昵称 + UID/登录状态 + 右箭头 */
@Composable
private fun ProfileBlock(
    isLoading: Boolean,
    profile: PlaylistApi.UserProfile?,
    notLoggedInText: String,
    loginHintText: String,
    uidLabel: String,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                color = LocalMetroColors.current.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 0.75.dp,
                color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(LocalMetroColors.current.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (profile?.avatarUrl?.isNotEmpty() == true) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = strings.userAvatarDesc,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                MetroIcon(
                    AppIcons.NavUser,
                    strings.userIconDesc,
                    tint = LocalMetroColors.current.onSurfaceVariant,
                    sizeDp = 32.dp,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> MetroText(
                    strings.loading,
                    color = LocalMetroColors.current.onSurfaceVariant,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                )
                profile != null -> {
                    MetroText(
                        profile.nickname,
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    MetroText(
                        uidLabel,
                        color = LocalMetroColors.current.onSurfaceVariant,
                        style = LocalMetroTypography.current.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    MetroText(
                        notLoggedInText,
                        color = LocalMetroColors.current.onBackground,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    MetroText(
                        loginHintText,
                        color = LocalMetroColors.current.onSurfaceVariant,
                        style = LocalMetroTypography.current.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        MetroIcon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = LocalMetroColors.current.onSurfaceVariant,
            sizeDp = 20.dp
        )
    }
}

/**
 * 现代圆角胶囊开关：跟手滑块 + 平滑三次贝塞尔过渡，提供细腻质感。
 */
@Composable
private fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalMetroColors.current.primary,
    trackOffColor: Color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.3f),
) {
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        progress.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
    }
    val density = LocalDensity.current
    val trackShape = RoundedCornerShape(13.dp)
    val thumbShape = CircleShape

    Box(
        modifier = modifier
            .width(46.dp)
            .height(26.dp)
            .background(
                color = lerp(trackOffColor, accentColor, progress.value),
                shape = trackShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbOffsetPx = with(density) { (20.dp * progress.value).toPx() }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = thumbOffsetPx
                }
                .size(20.dp)
                .background(Color.White, thumbShape)
        )
    }
}

/**
 * 开关设置行：标题与 Switch 同一行垂直居中，描述（可选）另起一行。
 * 描述不参与对齐，避免 Switch 被顶到与描述顶部对齐。
 */
@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetroText(
            title,
            color = LocalMetroColors.current.onBackground,
            style = LocalMetroTypography.current.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        ModernSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (description != null) {
        MetroText(
            description,
            color = LocalMetroColors.current.onSurfaceVariant,
            style = LocalMetroTypography.current.caption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )
    }
}

/** 主题模式三选一：跟随系统 / 深色 / 浅色。 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    labels: Triple<String, String, String>,
    onSelect: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to labels.first,
        ThemeMode.DARK to labels.second,
        ThemeMode.LIGHT to labels.third
    )
    val chipShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        0.75.dp,
                        if (active) LocalMetroColors.current.primary
                        else LocalMetroColors.current.divider.copy(alpha = 0.5f),
                        chipShape
                    )
                    .background(
                        if (active) LocalMetroColors.current.primary.copy(alpha = 0.14f)
                        else Color.Transparent,
                        chipShape
                    )
                .clickable { onSelect(mode) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            MetroText(
                label,
                color = if (active) LocalMetroColors.current.primary
                else LocalMetroColors.current.onSurfaceVariant,
                style = TextStyle(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
}

/** 清除缓存确认弹窗。 */
@Composable
private fun ClearCacheConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        val dialogShape = RoundedCornerShape(8.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalMetroColors.current.surface, dialogShape)
                .border(
                    0.75.dp,
                    LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.22f),
                    dialogShape
                )
                .padding(24.dp)
        ) {
            MetroText(
                strings.clearCache,
                color = LocalMetroColors.current.onBackground,
                style = LocalMetroTypography.current.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            MetroText(
                strings.clearCacheConfirm,
                color = LocalMetroColors.current.onSurfaceVariant,
                style = LocalMetroTypography.current.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton(text = strings.cancel, accent = false, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogButton(text = strings.clearCache, accent = true, onClick = onConfirm)
            }
        }
    }
}

/**
 * 单行下拉：左侧 label + 右侧「选中值 + ▼」，点击整行弹出垂直菜单。
 *
 * 多语言鲁棒：
 *  - label 用 weight(1f)，任何语言都能换行，不会挤到右侧值。
 *  - 选中值用 maxLines=1 + Ellipsis + widthIn(max=160dp)，极端长文会截断但不会撑破布局。
 *  - 下拉展开的菜单里每项独占一行，完整显示，用户始终能看到完整名字。
 */
@Composable
private fun MetroDropdownRow(
    label: String,
    selectedIndex: Int,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroText(
                label,
                color = LocalMetroColors.current.onBackground,
                style = TextStyle(fontSize = 15.sp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            MetroText(
                options.getOrElse(selectedIndex) { "" },
                color = LocalMetroColors.current.primary,
                style = TextStyle(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            MetroIcon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = LocalMetroColors.current.onSurfaceVariant,
                sizeDp = 20.dp,
            )
        }
        // UWP ComboBox 移植:选中项落回锚点原位,菜单从锚点双向展开。
        MetroSelectorFlyout(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AccountDialog(
    userProfile: PlaylistApi.UserProfile?,
    onDismiss: () -> Unit,
    onScanAuthorize: () -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        val dialogShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalMetroColors.current.surface, dialogShape)
                .border(
                    1.dp,
                    LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.2f),
                    dialogShape
                )
                .padding(24.dp)
        ) {
            MetroText(
                strings.accountDialogTitle,
                color = LocalMetroColors.current.onBackground,
                style = LocalMetroTypography.current.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(16.dp))

            if (userProfile != null) {
                MetroText(
                    strings.nicknameLabel(userProfile.nickname),
                    color = LocalMetroColors.current.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                MetroText(
                    strings.uidLabel(userProfile.userId.toString()),
                    color = LocalMetroColors.current.onSurfaceVariant,
                    style = TextStyle(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
            }

            // 扫码授权其他设备：扫平板的登录二维码，经局域网把本机账号授权过去。
            FullWidthDialogButton(
                text = strings.scanEntryTitle,
                accent = false,
                borderColor = LocalMetroColors.current.primary,
                textColor = LocalMetroColors.current.primary,
                onClick = onScanAuthorize
            )
            Spacer(Modifier.height(12.dp))

            // 全宽按钮：容器 fillMaxWidth，文字 Center + 换行——极长翻译最多多占一行，不会撑破对话框。
            FullWidthDialogButton(
                text = strings.logoutButton,
                accent = false,
                borderColor = Color.Red.copy(alpha = 0.5f),
                textColor = Color.Red,
                onClick = onLogout
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton(
                    text = strings.close,
                    accent = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

/** 短按钮：内容包裹式（wrap content）。用于对话框右下"取消/关闭/保存"。 */
@Composable
private fun DialogButton(
    text: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    val btnShape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .then(
                if (accent) Modifier.background(LocalMetroColors.current.primary, btnShape)
                else Modifier.border(1.dp, LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.4f), btnShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        MetroText(
            text,
            color = if (accent) Color.Black else LocalMetroColors.current.onSurfaceVariant,
            style = TextStyle(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 全宽按钮：填满可用宽度，文字居中，多行安全。用于对话框内的"更新 Cookie/退出登录"。 */
@Composable
private fun FullWidthDialogButton(
    text: String,
    accent: Boolean,
    borderColor: Color = LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.4f),
    textColor: Color = LocalMetroColors.current.onBackground,
    onClick: () -> Unit
) {
    val btnShape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (accent) Modifier.background(LocalMetroColors.current.primary, btnShape)
                else Modifier.border(1.dp, borderColor, btnShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        MetroText(
            text,
            color = if (accent) Color.Black else textColor,
            style = TextStyle(fontSize = 14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 语言下拉。闭合态显示当前选中语言名 + 箭头；点击弹出所有语言。
 *
 * 多语言鲁棒：闭合态 Text 设 maxLines=1 + Ellipsis + weight(1f)。极长名如
 * "Советский русский"/"Middle English" 最多截断，绝不换行撑破箭头位置。
 */
@Composable
fun MetroLanguageDropdown(
    selectedCode: String,
    presets: List<LanguagePreset>,
    onSelect: (String) -> Unit
) {
    val selected = presets.find { it.code == selectedCode } ?: presets.first()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LocalMetroColors.current.onSurfaceVariant.copy(alpha = 0.4f))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroText(
                selected.displayName,
                color = LocalMetroColors.current.onBackground,
                style = TextStyle(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            MetroIcon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = LocalMetroColors.current.onSurfaceVariant,
                sizeDp = 20.dp,
            )
        }

        // UWP ComboBox 移植:选中项落回锚点原位,内部滚动到选中语言。
        val selectedIndex = presets.indexOfFirst { it.code == selectedCode }.coerceAtLeast(0)
        MetroSelectorFlyout(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = presets.map { it.displayName },
            selectedIndex = selectedIndex,
            onSelect = { index ->
                onSelect(presets[index].code)
                expanded = false
            },
        )
    }
}


/** 统计应用缓存占用：图片磁盘缓存 + 缓存目录（含 WebView 缓存）。 */
private fun currentCacheSize(context: Context): Long {
    var total = 0L
    runCatching { coil.Coil.imageLoader(context).diskCache?.size?.let { total += it } }
    runCatching { context.cacheDir?.let { total += folderSize(it) } }
    return total
}

private fun folderSize(dir: java.io.File): Long {
    var size = 0L
    dir.listFiles()?.forEach { f ->
        size += if (f.isDirectory) folderSize(f) else f.length()
    }
    return size
}
