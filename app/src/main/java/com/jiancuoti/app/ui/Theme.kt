package com.jiancuoti.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 主题模式：自动 / 浅色 / 深色 */
enum class ThemeMode { AUTO, LIGHT, DARK }

// 天空蓝主色
val SkyPrimary = Color(0xFF0EA5E9)
val SkyPrimaryDeep = Color(0xFF0284C7)
val SkyPrimaryContainer = Color(0xFFD6EEFC)
val Green = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFF43F5E)

private val LightColors = lightColorScheme(
    primary = SkyPrimaryDeep,
    onPrimary = Color.White,
    primaryContainer = SkyPrimaryContainer,
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF38BDF8),
    secondaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFFEAF4FC),
    surface = Color(0xFFFBFDFF),
    surfaceVariant = Color(0xFFF1F7FC),
    onBackground = Color(0xFF0C2B42),
    onSurface = Color(0xFF0C2B42),
    onSurfaceVariant = Color(0xFF3E5F78),
    outline = Color(0xFF9DB6C8),
    error = Red,
)

// 深色：分层提亮 + 高对比文字，告别「一片黑」
private val DarkColors = darkColorScheme(
    primary = Color(0xFF56CCF9),
    onPrimary = Color(0xFF04283A),
    primaryContainer = Color(0xFF14507A),
    onPrimaryContainer = Color(0xFFC9EAFB),
    secondary = Color(0xFF8AD8FA),
    secondaryContainer = Color(0xFF12455F),
    background = Color(0xFF0A111C),      // 最底层背景
    surface = Color(0xFF1A2736),         // 卡片层（比背景亮一档）
    surfaceVariant = Color(0xFF243447),  // 次级层
    onBackground = Color(0xFFF2F7FC),    // 接近纯白，保证可读
    onSurface = Color(0xFFF2F7FC),
    onSurfaceVariant = Color(0xFFC0D2E2),
    outline = Color(0xFF64809A),         // 提亮描边/辅助字
    error = Color(0xFFFF8DA3),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDarkActive(mode)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}

@Composable
fun isDarkActive(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// ---------- 玻璃拟态（Glassmorphism）----------

/** 玻璃容器色：半透明表面 */
@Composable
fun glassColor(): Color =
    if (isDarkActiveCurrent()) Color(0xFF1A2736).copy(alpha = 0.62f)
    else Color.White.copy(alpha = 0.66f)

/** 玻璃描边：浅色白色高光 / 深色冷白微光 */
@Composable
fun glassBorder(): BorderStroke =
    if (isDarkActiveCurrent()) BorderStroke(1.dp, Color(0xFFBFE0F5).copy(alpha = 0.16f))
    else BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))

@Composable
private fun isDarkActiveCurrent(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** 统一全圆角玻璃卡片（大圆角、无棱角） */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = glassColor(),
        border = glassBorder(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/** 小号玻璃块（缩略图占位、标签底等） */
@Composable
fun glassChipColor(): Color =
    if (isDarkActiveCurrent()) Color(0xFF243447).copy(alpha = 0.7f)
    else Color.White.copy(alpha = 0.55f)
