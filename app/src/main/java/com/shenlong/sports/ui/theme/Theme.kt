package com.shenlong.sports.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = DragonRed,
    onPrimary = TextOnPrimary,
    primaryContainer = LightRed,
    onPrimaryContainer = DragonRed,
    secondary = DragonOrange,
    onSecondary = TextOnPrimary,
    secondaryContainer = LightOrange,
    onSecondaryContainer = DragonOrange,
    tertiary = DragonGold,
    onTertiary = DragonDark,
    tertiaryContainer = LightGold,
    onTertiaryContainer = DragonDark,
    background = SurfaceWhite,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = LightGray,
    onSurfaceVariant = TextSecondary,
    error = DragonRed,
    onError = TextOnPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = DragonRed,
    onPrimary = TextOnPrimary,
    primaryContainer = DragonDark,
    onPrimaryContainer = DragonRed,
    secondary = DragonOrange,
    onSecondary = TextOnPrimary,
    background = DragonDark,
    onBackground = TextOnPrimary,
    surface = DragonBlue,
    onSurface = TextOnPrimary,
    surfaceVariant = DragonGray,
    onSurfaceVariant = LightGray
)

@Composable
fun ShenlongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
