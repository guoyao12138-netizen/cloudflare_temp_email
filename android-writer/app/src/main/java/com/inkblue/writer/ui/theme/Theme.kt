package com.inkblue.writer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = BlueTint,
    onPrimaryContainer = InkBlueDeep,
    secondary = InkBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = BlueTint,
    onSecondaryContainer = InkBlueDeep,
    background = CloudWhite,
    onBackground = InkText,
    surface = PaperWhite,
    onSurface = InkText,
    surfaceVariant = MistVariant,
    onSurfaceVariant = InkTextSecondary,
    outline = MistBorder,
    outlineVariant = MistVariant,
    error = DangerRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = NightBlue,
    onPrimary = Color(0xFF13203D),
    primaryContainer = NightBlueDeep,
    onPrimaryContainer = NightBlue,
    secondary = NightBlue,
    onSecondary = Color(0xFF13203D),
    secondaryContainer = NightBlueDeep,
    onSecondaryContainer = NightText,
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder,
    outlineVariant = NightVariant,
    error = NightDanger,
    onError = Color(0xFF3B1210),
)

@Composable
fun InkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = InkTypography,
        content = content,
    )
}
