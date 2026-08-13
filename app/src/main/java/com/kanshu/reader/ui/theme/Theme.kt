package com.kanshu.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kanshu.reader.data.prefs.AppThemeMode

private val DayColors = lightColorScheme(
    primary = Color(0xFF1B4D3E),
    onPrimary = Color(0xFFF5F0E6),
    secondary = Color(0xFF8B6914),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF4A453C)
)

private val NightColors = darkColorScheme(
    primary = Color(0xFF8FCBB3),
    onPrimary = Color(0xFF0B1F18),
    secondary = Color(0xFFD4B56A),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1D22),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB8B2A6)
)

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color
)

fun readerPalette(mode: AppThemeMode): ReaderPalette = when (mode) {
    AppThemeMode.DAY -> ReaderPalette(
        background = Color(0xFFF7F3EA),
        text = Color(0xFF1C1917),
        muted = Color(0xFF6B6458)
    )
    AppThemeMode.NIGHT -> ReaderPalette(
        background = Color(0xFF0E1013),
        text = Color(0xFFD8D2C8),
        muted = Color(0xFF8E8790)
    )
}

@Composable
fun KanshuTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        AppThemeMode.DAY -> false
        AppThemeMode.NIGHT -> true
    }
    MaterialTheme(
        colorScheme = if (dark) NightColors else DayColors,
        content = content
    )
}
