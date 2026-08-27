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
    primaryContainer = Color(0xFFD4E8DE),
    onPrimaryContainer = Color(0xFF0B2E24),
    secondary = Color(0xFF8B6914),
    onSecondary = Color(0xFFFFFBF4),
    secondaryContainer = Color(0xFFF0E4C8),
    onSecondaryContainer = Color(0xFF3D2E06),
    tertiary = Color(0xFF6B5344),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF4A453C),
    outline = Color(0xFFCCC4B4)
)

private val NightColors = darkColorScheme(
    primary = Color(0xFF8FCBB3),
    onPrimary = Color(0xFF0B1F18),
    primaryContainer = Color(0xFF1F3D34),
    onPrimaryContainer = Color(0xFFB8E8D4),
    secondary = Color(0xFFD4B56A),
    onSecondary = Color(0xFF2A2208),
    secondaryContainer = Color(0xFF3D3420),
    onSecondaryContainer = Color(0xFFF0E0B0),
    tertiary = Color(0xFFC4A88A),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1D22),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB8B2A6),
    outline = Color(0xFF4A4F58)
)

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color
)

fun readerPalette(mode: AppThemeMode): ReaderPalette = when (mode) {
    AppThemeMode.DAY -> ReaderPalette(
        background = Color(0xFFF7F3EA),
        text = Color(0xFF1C1917),
        muted = Color(0xFF6B6458),
        accent = Color(0xFF1B4D3E)
    )
    AppThemeMode.NIGHT -> ReaderPalette(
        background = Color(0xFF0E1013),
        text = Color(0xFFD8D2C8),
        muted = Color(0xFF8E8790),
        accent = Color(0xFF8FCBB3)
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
