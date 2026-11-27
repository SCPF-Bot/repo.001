package com.mlbb.plus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

// ============================================================
// Shared overlay theme: deep-navy surfaces, gold accent, and
// monospace numerals + sans-serif prose for in-match HUD.
// ============================================================

object MLBBPlusColors {
    val GithubBg = Color(0xFF0B0C10)
    val GithubCard = Color(0xFF1A1B2E)
    val GithubSurface = Color(0xFF25263F)
    val GithubBorder = Color(0xFF2E3049)
    val GithubText = Color(0xFFEDEDF7)
    val GithubSecondary = Color(0xFF8B8EAA)
    val GithubDisabled = Color(0xFF8B8EAA)

    val Header = Color(0xFF181A2D)

    val Gold = Color(0xFFFFD700)
    val GoldDark = Color(0xFFD4A900)
    val GoldBg = Color(0xFFFFD700).copy(alpha = 0.15f)

    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFE5334B)
    val Orange = Color(0xFFF59E0B)
    val Blue = Color(0xFF4C9AFF)
    val Teal = Color(0xFF2DD4BF)
    val BlueOld = Blue

    val TierS = Gold
    val TierA = Blue
    val TierB = Color(0xFF8B8EAA)

    val PatchBuff = Green
    val PatchNerf = Red
    val PatchRevamp = Gold
    val PatchAdjust = GithubSecondary
    val PatchReversion = Orange

    val Advanced = Orange
    val AdvancedBg = Color(0xFFF59E0B).copy(alpha = 0.15f)

    val OP = Gold
    val Meta = Green
}

private val Mono = FontFamily.Monospace
private val BaseType = Typography()
private val MLBBPlusTypography = Typography(
    displayLarge = BaseType.displayLarge.copy(fontFamily = Mono),
    displayMedium = BaseType.displayMedium.copy(fontFamily = Mono),
    displaySmall = BaseType.displaySmall.copy(fontFamily = Mono),
    headlineLarge = BaseType.headlineLarge.copy(fontFamily = Mono),
    headlineMedium = BaseType.headlineMedium.copy(fontFamily = Mono),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = Mono),
    titleLarge = BaseType.titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = BaseType.titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = BaseType.titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = BaseType.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = BaseType.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = BaseType.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = BaseType.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = BaseType.labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = BaseType.labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

@Composable
fun MLBBPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MLBBPlusColors.Gold,
            onPrimary = Color.Black,
            background = MLBBPlusColors.GithubBg,
            onBackground = MLBBPlusColors.GithubText,
            surface = MLBBPlusColors.GithubCard,
            onSurface = MLBBPlusColors.GithubText,
            surfaceVariant = MLBBPlusColors.GithubSurface,
            onSurfaceVariant = MLBBPlusColors.GithubSecondary,
            outline = MLBBPlusColors.GithubBorder,
            secondary = MLBBPlusColors.Gold,
            onSecondary = Color.Black,
            error = MLBBPlusColors.Red,
            onError = Color.White
        ),
        typography = MLBBPlusTypography,
        content = content
    )
}

// Back-compat aliases
val SurfaceDark = MLBBPlusColors.GithubBg
val SurfaceCard = MLBBPlusColors.GithubCard
val SurfaceMid = MLBBPlusColors.GithubCard
val SurfaceElevated = MLBBPlusColors.GithubBorder
val TextPrimary = MLBBPlusColors.GithubText
val TextSecondary = MLBBPlusColors.GithubSecondary
val TextDisabled = MLBBPlusColors.GithubSecondary
val MLBBGold = MLBBPlusColors.Gold
val SuccessGreen = MLBBPlusColors.Green
val ErrorRed = MLBBPlusColors.Red
val InfoBlue = MLBBPlusColors.Blue
val MLBBRed = MLBBPlusColors.Red
val MLBBTeal = MLBBPlusColors.Teal
val MLBBBlue = MLBBPlusColors.Blue
val TierSPlus = Color(0xFFFFE55C)
val TierAPlus = Color(0xFF79B8FF)

/** Legacy dark-palette aliases kept only for backwards compatibility. */
object AppUiColors {
    val GithubBg = Color(0xFF0B0E14)
    val GithubCard = Color(0xFF151B27)
    val GithubSurface = Color(0xFF202A39)
    val GithubBorder = Color(0xFF2B394B)
    val GithubText = Color(0xFFF2F5FA)
    val GithubSecondary = Color(0xFFADB8C8)
    val GithubDisabled = Color(0xFFADB8C8)
    val Header = Color(0xFF111925)
    val Gold = Color(0xFFFFD45A)
    val GoldDark = Color(0xFFC99A25)
    val GoldBg = Color(0xFFFFD45A).copy(alpha = 0.15f)
    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFE85B70)
    val Orange = Color(0xFFF5A623)
    val Blue = Color(0xFF68AFFF)
    val Teal = Color(0xFF42D6C5)
    val BlueOld = Blue
    val TierS = Gold
    val TierA = Blue
    val TierB = GithubSecondary
    val PatchBuff = Green
    val PatchNerf = Red
    val PatchRevamp = Gold
    val PatchAdjust = GithubSecondary
    val PatchReversion = Orange
    val Advanced = Orange
    val AdvancedBg = Orange.copy(alpha = 0.15f)
    val OP = Gold
    val Meta = Green
}

object AppTheme {
    val colors: AppExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppExtendedColors.current
}

@Composable
fun AppUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme) {
                dynamic.copy(primary = AppColorTokens.Gold, onPrimary = Color(0xFF171717))
            } else {
                dynamic.copy(primary = AppColorTokens.GoldDeep, onPrimary = Color.White)
            }
        }
        darkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalAppExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
