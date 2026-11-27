package com.mlbb.plus.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================
// MLBB Plus — Material 3 color tokens (app screens only).
//
// Brand: ink-navy surfaces + gold accent (from AppUiColors).
// The floating overlay keeps MLBBPlusColors in MLBBPlusTheme.kt
// and is intentionally NOT recolored here: in-game contrast
// over a bright/dark game frame matters more than brand
// consistency, and dynamic color must never wash out the
// overlay. See docs/ux-redesign.md §4 for the trade-off.
//
// Contrast (WCAG AA, body text >= 4.5:1):
//  - Dark:  GithubText #F2F5FA on GithubBg #0B0E14  ≈ 15:1 ✓
//           GithubSecondary #ADB8C8 on #0B0E14      ≈ 8:1  ✓
//           onPrimary #171717 on Gold #FFD45A       ≈ 11:1 ✓
//  - Light: onSurface #171717 on #FFFFFF            ≈ 16:1 ✓
//           onSurfaceVariant #4A5568 on #FFFFFF     ≈ 7:1  ✓
//           onPrimary #FFFFFF on Primary #7A5A00    ≈ 6:1  ✓
// Gold #FFD45A is never used as body text on white in light
// mode; the light primary is a darkened gold (#7A5A00).
// ============================================================

object AppColorTokens {
    // Brand gold ramp
    val Gold = Color(0xFFFFD45A)
    val GoldDark = Color(0xFFC99A25)
    val GoldDeep = Color(0xFF7A5A00) // light-mode primary (AA on white)
    val GoldContainerDark = Color(0xFF3A2F0A)
    val GoldContainerLight = Color(0xFFFFF0C2)

    // Ink surfaces (dark, from AppUiColors)
    val InkBg = Color(0xFF0B0E14)
    val InkCard = Color(0xFF151B27)
    val InkSurface = Color(0xFF202A39)
    val InkBorder = Color(0xFF2B394B)
    val InkHeader = Color(0xFF111925)
    val InkText = Color(0xFFF2F5FA)
    val InkSecondary = Color(0xFFADB8C8)

    // Semantic (never color-alone: always paired with label/icon)
    val Success = Color(0xFF3DDC97)
    val Error = Color(0xFFE85B70)
    val Warning = Color(0xFFF5A623)
    val Info = Color(0xFF68AFFF)
    val Teal = Color(0xFF42D6C5)
}

val AppLightColorScheme = lightColorScheme(
    primary = AppColorTokens.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = AppColorTokens.GoldContainerLight,
    onPrimaryContainer = Color(0xFF3A2F0A),
    secondary = Color(0xFF006A5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F2EA),
    onSecondaryContainer = Color(0xFF00332C),
    tertiary = Color(0xFF0057A8),
    onTertiary = Color.White,
    error = Color(0xFFBA1A2A),
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
    secondary = AppColorTokens.Teal,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF00534B),
    onSecondaryContainer = Color(0xFFC8F2EA),
    tertiary = AppColorTokens.Info,
    onTertiary = Color(0xFF00315E),
    error = AppColorTokens.Error,
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
