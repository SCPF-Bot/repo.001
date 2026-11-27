package com.mlbb.plus.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================
// MLBB Plus — Material 3 color tokens (app screens only).
//
// Brand: ink-navy surfaces + gold accent in dark mode;
// warm cream surfaces + deep gold primary in light mode.
// The floating overlay keeps MLBBPlusColors in MLBBPlusTheme.kt
// for consistent contrast over bright/dark gameplay frames.
//
// Contrast (WCAG AA, body text >= 4.5:1):
//  - Dark:  InkText #F2F5FA on InkBg #0B0E14        ≈ 15:1
//           InkSecondary #ADB8C8 on #0B0E14         ≈ 8:1
//           onPrimary #171717 on Gold #FFD45A       ≈ 11:1
//  - Light: onSurface #171717 on #FFFFFF            ≈ 16:1
//           onSurfaceVariant #4A5568 on #FFFFFF     ≈ 7:1
//           onPrimary #FFFFFF on Primary #7A5A00    ≈ 6:1
// ============================================================

object AppColorTokens {
    // Brand gold ramp
    val Gold = Color(0xFFFFD45A)
    val GoldDark = Color(0xFFC99A25)
    val GoldDeep = Color(0xFF7A5A00) // light-mode primary (AA on white)
    val GoldContainerDark = Color(0xFF3A2F0A)
    val GoldContainerLight = Color(0xFFFFF0C2)

    // Ink surfaces (dark)
    val InkBg = Color(0xFF0B0E14)
    val InkCard = Color(0xFF151B27)
    val InkSurface = Color(0xFF202A39)
    val InkBorder = Color(0xFF2B394B)
    val InkHeader = Color(0xFF111925)
    val InkText = Color(0xFFF2F5FA)
    val InkSecondary = Color(0xFFADB8C8)

    // Semantic (paired with label/icon — never color-alone)
    val SuccessDark = Color(0xFF3DDC97)
    val SuccessLight = Color(0xFF0F7A4D)
    val ErrorDark = Color(0xFFE85B70)
    val ErrorLight = Color(0xFFBA1A2A)
    val WarningDark = Color(0xFFF5A623)
    val WarningLight = Color(0xFF9A5B00)
    val InfoDark = Color(0xFF68AFFF)
    val InfoLight = Color(0xFF0057A8)
    val TealDark = Color(0xFF42D6C5)
    val TealLight = Color(0xFF006A5E)
}

@Immutable
data class AppExtendedColors(
    val gold: Color,
    val goldContainer: Color,
    val onGoldContainer: Color,
    val headerContainer: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val tierS: Color,
    val tierA: Color,
    val tierB: Color,
    val patchBuff: Color,
    val patchNerf: Color,
    val patchRevamp: Color,
    val patchAdjust: Color,
    val patchReversion: Color
)

val LightExtendedColors = AppExtendedColors(
    gold = AppColorTokens.GoldDeep,
    goldContainer = AppColorTokens.GoldContainerLight,
    onGoldContainer = Color(0xFF3A2F0A),
    headerContainer = Color(0xFFF7F2E3),
    success = AppColorTokens.SuccessLight,
    warning = AppColorTokens.WarningLight,
    info = AppColorTokens.InfoLight,
    tierS = AppColorTokens.GoldDeep,
    tierA = AppColorTokens.InfoLight,
    tierB = Color(0xFF4A5568),
    patchBuff = AppColorTokens.SuccessLight,
    patchNerf = AppColorTokens.ErrorLight,
    patchRevamp = AppColorTokens.GoldDeep,
    patchAdjust = Color(0xFF4A5568),
    patchReversion = AppColorTokens.WarningLight
)

val DarkExtendedColors = AppExtendedColors(
    gold = AppColorTokens.Gold,
    goldContainer = AppColorTokens.GoldContainerDark,
    onGoldContainer = Color(0xFFFFE9A8),
    headerContainer = AppColorTokens.InkHeader,
    success = AppColorTokens.SuccessDark,
    warning = AppColorTokens.WarningDark,
    info = AppColorTokens.InfoDark,
    tierS = AppColorTokens.Gold,
    tierA = AppColorTokens.InfoDark,
    tierB = AppColorTokens.InkSecondary,
    patchBuff = AppColorTokens.SuccessDark,
    patchNerf = AppColorTokens.ErrorDark,
    patchRevamp = AppColorTokens.Gold,
    patchAdjust = AppColorTokens.InkSecondary,
    patchReversion = AppColorTokens.WarningDark
)

val LocalAppExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

val AppLightColorScheme = lightColorScheme(
    primary = AppColorTokens.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = AppColorTokens.GoldContainerLight,
    onPrimaryContainer = Color(0xFF3A2F0A),
    secondary = AppColorTokens.TealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F2EA),
    onSecondaryContainer = Color(0xFF00332C),
    tertiary = AppColorTokens.InfoLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E4FF),
    onTertiaryContainer = Color(0xFF001B3E),
    error = AppColorTokens.ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF171717),
    surface = Color.White,
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF0EDE4),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F4EC),
    surfaceContainer = Color(0xFFF0EDE4),
    surfaceContainerHigh = Color(0xFFECE8DD),
    surfaceContainerHighest = Color(0xFFE6E2D6),
    outline = Color(0xFF8A8F98),
    outlineVariant = Color(0xFFD8D3C5)
)

val AppDarkColorScheme = darkColorScheme(
    primary = AppColorTokens.Gold,
    onPrimary = Color(0xFF171717),
    primaryContainer = AppColorTokens.GoldContainerDark,
    onPrimaryContainer = Color(0xFFFFE9A8),
    secondary = AppColorTokens.TealDark,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF00534B),
    onSecondaryContainer = Color(0xFFC8F2EA),
    tertiary = AppColorTokens.InfoDark,
    onTertiary = Color(0xFF00315E),
    tertiaryContainer = Color(0xFF0F294A),
    onTertiaryContainer = Color(0xFFD6E4FF),
    error = AppColorTokens.ErrorDark,
    onError = Color.White,
    errorContainer = Color(0xFF5C101D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AppColorTokens.InkBg,
    onBackground = AppColorTokens.InkText,
    surface = AppColorTokens.InkCard,
    onSurface = AppColorTokens.InkText,
    surfaceVariant = AppColorTokens.InkSurface,
    onSurfaceVariant = AppColorTokens.InkSecondary,
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Color(0xFF10151F),
    surfaceContainer = AppColorTokens.InkCard,
    surfaceContainerHigh = AppColorTokens.InkSurface,
    surfaceContainerHighest = Color(0xFF2B394B),
    outline = AppColorTokens.InkBorder,
    outlineVariant = Color(0xFF3A4A5E)
)
