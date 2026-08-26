package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * NyxTap Theme — Pure Black + Dark Glass + White / Soft Gray + Minimal Geometric
 */
private val StitchColorScheme = darkColorScheme(
    primary = StitchTextPrimary,
    onPrimary = StitchBlack,
    primaryContainer = StitchGlassCardElevated,
    onPrimaryContainer = StitchTextPrimary,
    secondary = StitchTextPrimary,
    onSecondary = StitchBlack,
    secondaryContainer = StitchGlassCardElevated,
    onSecondaryContainer = StitchTextPrimary,
    tertiary = StitchGreen,
    onTertiary = StitchBlack,
    background = StitchBlack,
    onBackground = StitchTextPrimary,
    surface = StitchGlassCard,
    onSurface = StitchTextPrimary,
    surfaceVariant = StitchGlassCardElevated,
    onSurfaceVariant = StitchTextSecondary,
    outline = StitchGlassBorder,
    outlineVariant = StitchGlassBorderSubtle,
    error = StitchRed,
    onError = StitchTextPrimary,
    errorContainer = StitchRedBadgeBg,
    onErrorContainer = StitchRedBadgeText
)

@Composable
fun TempMail2FATheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StitchColorScheme,
        typography = Typography,
        content = content
    )
}
