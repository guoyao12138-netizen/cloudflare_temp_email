package com.inkblue.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same ink-blue Claude-style palette as the Android app.
private val InkBlue = Color(0xFF4A6FE3)
private val InkBlueDeep = Color(0xFF3354BE)
private val CloudWhite = Color(0xFFF6F8FC)
private val PaperWhite = Color(0xFFFFFFFF)
private val MistVariant = Color(0xFFEDF1F8)
private val MistBorder = Color(0xFFE2E7F0)
private val BlueTint = Color(0xFFE7EDFB)
private val InkText = Color(0xFF1F2433)
private val InkTextSecondary = Color(0xFF6B7385)
private val DangerRed = Color(0xFFC4554D)

private val NightBg = Color(0xFF1B1E26)
private val NightSurface = Color(0xFF242834)
private val NightVariant = Color(0xFF2C3140)
private val NightBorder = Color(0xFF353B4A)
private val NightBlue = Color(0xFF8CA8F5)
private val NightBlueDeep = Color(0xFF31405F)
private val NightText = Color(0xFFE6E9F2)
private val NightTextSecondary = Color(0xFF9AA3B8)
private val NightDanger = Color(0xFFE08B84)

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

private val InkTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun InkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = InkTypography,
        content = content,
    )
}
