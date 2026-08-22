package com.jiancuoti.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    outline = Color(0xFFD5E4EF),
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF06283D),
    primaryContainer = Color(0xFF0B3A55),
    onPrimaryContainer = Color(0xFFB8E4FA),
    secondary = Color(0xFF7DD3FC),
    secondaryContainer = Color(0xFF0C344C),
    background = Color(0xFF0E1621),
    surface = Color(0xFF16202E),
    surfaceVariant = Color(0xFF1D2A3A),
    onBackground = Color(0xFFE4EEF6),
    onSurface = Color(0xFFE4EEF6),
    onSurfaceVariant = Color(0xFFA3B8C9),
    outline = Color(0xFF2C3E52),
    error = Color(0xFFFB7185),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
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
