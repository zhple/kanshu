package com.kanshu.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kanshu.reader.data.prefs.AppThemeMode

/** 白天：海边咖啡馆 —— 拿铁暖底 + 柔和海蓝点缀 */
private val DayColors = lightColorScheme(
    primary = Color(0xFF6A9FB5),
    onPrimary = Color(0xFFFFFBF7),
    primaryContainer = Color(0xFFD4E8F0),
    onPrimaryContainer = Color(0xFF1E3A47),
    secondary = Color(0xFFB8845A),
    onSecondary = Color(0xFFFFFBF7),
    secondaryContainer = Color(0xFFF0E0CE),
    onSecondaryContainer = Color(0xFF4A3420),
    tertiary = Color(0xFF8FAF9A),
    onTertiary = Color(0xFF1A2E24),
    tertiaryContainer = Color(0xFFDCEADF),
    onTertiaryContainer = Color(0xFF243528),
    background = Color(0xFFF4EBE0),
    onBackground = Color(0xFF4A3F35),
    surface = Color(0xFFFFFAF6),
    onSurface = Color(0xFF4A3F35),
    surfaceVariant = Color(0xFFE6D9CC),
    onSurfaceVariant = Color(0xFF7A6E62),
    outline = Color(0xFFD4C4B4)
)

/** 夜晚：暮色海岸 —— 深蓝海面 + 暖黄灯光 */
private val NightColors = darkColorScheme(
    primary = Color(0xFF9EC4D9),
    onPrimary = Color(0xFF1A2830),
    primaryContainer = Color(0xFF2E4554),
    onPrimaryContainer = Color(0xFFD4EAF4),
    secondary = Color(0xFFD4A574),
    onSecondary = Color(0xFF2A2010),
    secondaryContainer = Color(0xFF4A3828),
    onSecondaryContainer = Color(0xFFF0DCC4),
    tertiary = Color(0xFFA8C4B0),
    onTertiary = Color(0xFF1A2820),
    tertiaryContainer = Color(0xFF344840),
    onTertiaryContainer = Color(0xFFD4E8DC),
    background = Color(0xFF1E2A33),
    onBackground = Color(0xFFEDE4D8),
    surface = Color(0xFF263340),
    onSurface = Color(0xFFEDE4D8),
    surfaceVariant = Color(0xFF3A4854),
    onSurfaceVariant = Color(0xFFC4B8AC),
    outline = Color(0xFF5A6874)
)

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color
)

fun readerPalette(mode: AppThemeMode): ReaderPalette = when (mode) {
    AppThemeMode.DAY -> ReaderPalette(
        background = Color(0xFFF4EBE0),
        text = Color(0xFF4A3F35),
        muted = Color(0xFF8A7E72),
        accent = Color(0xFF6A9FB5)
    )
    AppThemeMode.NIGHT -> ReaderPalette(
        background = Color(0xFF1A242C),
        text = Color(0xFFEDE4D8),
        muted = Color(0xFFA09890),
        accent = Color(0xFF9EC4D9)
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
