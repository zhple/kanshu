package com.kanshu.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kanshu.reader.data.prefs.AppThemeMode

/** 高级感日间：亚麻底 + 深青蓝主色 + 香槟金点缀 */
private val DayColors = lightColorScheme(
    primary = Color(0xFF4A6B7C),
    onPrimary = Color(0xFFFDFCFA),
    primaryContainer = Color(0xFFD8E8F0),
    onPrimaryContainer = Color(0xFF1A2E38),
    secondary = Color(0xFFC4A574),
    onSecondary = Color(0xFF2A2010),
    secondaryContainer = Color(0xFFF0E6D4),
    onSecondaryContainer = Color(0xFF3D3020),
    tertiary = Color(0xFF7A9E8E),
    onTertiary = Color(0xFF1A2820),
    tertiaryContainer = Color(0xFFDCEADF),
    onTertiaryContainer = Color(0xFF243528),
    background = Color(0xFFF3EEE6),
    onBackground = Color(0xFF2C2620),
    surface = Color(0xFFFDFCFA),
    onSurface = Color(0xFF2C2620),
    surfaceVariant = Color(0xFFE8E0D4),
    onSurfaceVariant = Color(0xFF6B6258),
    outline = Color(0xFFC8BDB0)
)

/** 高级感夜间：深墨蓝 + 月光银 + 暖金灯光 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFB8D4E8),
    onPrimary = Color(0xFF152028),
    primaryContainer = Color(0xFF2A3D4C),
    onPrimaryContainer = Color(0xFFD8ECF8),
    secondary = Color(0xFFD4B88A),
    onSecondary = Color(0xFF2A2010),
    secondaryContainer = Color(0xFF4A3A28),
    onSecondaryContainer = Color(0xFFF0E4D0),
    tertiary = Color(0xFFA8C4B8),
    onTertiary = Color(0xFF1A2820),
    tertiaryContainer = Color(0xFF344840),
    onTertiaryContainer = Color(0xFFD8EADC),
    background = Color(0xFF141C24),
    onBackground = Color(0xFFE8E4DC),
    surface = Color(0xFF1C2630),
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF2E3844),
    onSurfaceVariant = Color(0xFFB8B0A4),
    outline = Color(0xFF4A5560)
)

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color
)

fun readerPalette(mode: AppThemeMode): ReaderPalette = when (mode) {
    AppThemeMode.DAY -> ReaderPalette(
        background = Color(0xFFF3EEE6),
        text = Color(0xFF2C2620),
        muted = Color(0xFF7A7268),
        accent = Color(0xFF4A6B7C)
    )
    AppThemeMode.NIGHT -> ReaderPalette(
        background = Color(0xFF121820),
        text = Color(0xFFE8E4DC),
        muted = Color(0xFF9A9488),
        accent = Color(0xFFB8D4E8)
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
