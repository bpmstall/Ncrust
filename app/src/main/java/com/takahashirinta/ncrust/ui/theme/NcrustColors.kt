package com.takahashirinta.ncrust.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.takahashirinta.kanesumi.core.theme.MetroColors

/**
 * Ncrust 自有配色板。替代 MD3 的 ColorScheme。
 *
 * 设计：深色采用分层中性灰，浅色采用低对比中性底。两套共享同一组强调色。
 * 所有颜色硬编码，不依赖 MD3 的 tonalElevation 调色逻辑。
 */
@Immutable
data class NcrustColors(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color
)

/** Xiaomi-inspired neutral surfaces with a single warm accent. */
val DefaultNcrustColors = NcrustColors(
    primary = Color(0xFFFF6900),
    background = Color(0xFF0F1011),
    surface = Color(0xFF18191B),
    surfaceVariant = Color(0xFF252629),
    onBackground = Color(0xFFF7F7F7),
    onSurface = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFFA6A7AB)
)

/** Clean neutral light surfaces with the same accent for theme continuity. */
val LightNcrustColors = NcrustColors(
    primary = Color(0xFFFF6900),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EAEC),
    onBackground = Color(0xFF191A1C),
    onSurface = Color(0xFF191A1C),
    onSurfaceVariant = Color(0xFF707176)
)

val LocalNcrustColors = compositionLocalOf { DefaultNcrustColors }

/**
 * 由 Ncrust 调色板派生 Kanesumi MetroColors，补齐 MetroColors 独有字段
 * （divider / onPrimary / pressTint），保证两套主题源视觉一致。
 */
fun NcrustColors.toMetroColors(isDark: Boolean): MetroColors = MetroColors(
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant,
    primary = primary,
    onPrimary = Color(0xFFFFFFFF),
    onBackground = onBackground,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    divider = if (isDark) Color(0xFF303136) else Color(0xFFD9DADD),
    pressTint = if (isDark) Color(0x22FFFFFF) else Color(0x14000000),
)
