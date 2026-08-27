package com.kanshu.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kanshu.reader.data.prefs.AppThemeMode

/** 简约日间：暖灰石底 + 低饱和 slate，避免过曝白 */
private val DayColors = lightColorScheme(
    primary = Color(0xFF5C6B73),
    onPrimary = Color(0xFFF5F4F2),
    primaryContainer = Color(0xFFE4E8EA),
    onPrimaryContainer = Color(0xFF2A3238),
    secondary = Color(0xFF8A8178),
    onSecondary = Color(0xFFF5F4F2),
    secondaryContainer = Color(0xFFEBE8E4),
    onSecondaryContainer = Color(0xFF3A3530),
    tertiary = Color(0xFF7A8E86),
    onTertiary = Color(0xFFF5F4F2),
    tertiaryContainer = Color(0xFFE6EBE9),
    onTertiaryContainer = Color(0xFF2A3230),
    background = Color(0xFFF2F0ED),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8E6E2),
    onSurfaceVariant = Color(0xFF6B6660),
    outline = Color(0xFFD8D4CE)
)

/** 简约夜间：深石墨 + 柔灰文字，无高亮蓝光 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFA8B5BC),
    onPrimary = Color(0xFF1A1E20),
    primaryContainer = Color(0xFF2E363A),
    onPrimaryContainer = Color(0xFFD8DEE2),
    secondary = Color(0xFFB0A89E),
    onSecondary = Color(0xFF1E1C18),
    secondaryContainer = Color(0xFF3A3630),
    onSecondaryContainer = Color(0xFFE4E0DA),
    tertiary = Color(0xFF98A8A0),
    onTertiary = Color(0xFF1A201E),
    tertiaryContainer = Color(0xFF323A36),
    onTertiaryContainer = Color(0xFFD8E0DC),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E6E2),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE8E6E2),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA8A4A0),
    outline = Color(0xFF444444)
)

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color
)

fun readerPalette(mode: AppThemeMode): ReaderPalette = when (mode) {
    AppThemeMode.DAY -> ReaderPalette(
        background = Color(0xFFF2F0ED),
        text = Color(0xFF1F1F1F),
        muted = Color(0xFF7A7570),
        accent = Color(0xFF5C6B73)
    )
    AppThemeMode.NIGHT -> ReaderPalette(
        background = Color(0xFF121212),
        text = Color(0xFFE8E6E2),
        muted = Color(0xFF989490),
        accent = Color(0xFFA8B5BC)
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
        typography = KanshuTypography,
        shapes = KanshuShapes,
        content = content
    )
}
