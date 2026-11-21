package com.mlbb.plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ============================================================
// Shared overlay theme: original deep-navy surfaces, gold accent, and monospace typography.
//
// Property names (Github*) are kept so every existing screen,
// the overlay widget and the back-compat aliases keep compiling;
// only the VALUES changed.
// ============================================================

object MLBBPlusColors {
    val GithubBg = Color(0xFF0B0C10)          // app background
    val GithubCard = Color(0xFF1A1B2E)        // cards / panels (navy)
    val GithubSurface = Color(0xFF25263F)     // raised chips, inputs
    val GithubBorder = Color(0xFF2E3049)      // hairlines
    val GithubText = Color(0xFFEDEDF7)
    val GithubSecondary = Color(0xFF8B8EAA)
    val GithubDisabled = Color(0xFF8B8EAA)

    val Header = Color(0xFF181A2D)            // title bars + bottom nav

    val Gold = Color(0xFFFFD700)
    val GoldDark = Color(0xFFD4A900)
    val GoldBg = Color(0xFFFFD700).copy(alpha = 0.15f)

    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFE5334B)
    val Orange = Color(0xFFF59E0B)
    val Blue = Color(0xFF4C9AFF)
    val Teal = Color(0xFF2DD4BF)
    val BlueOld = Blue

    // --- Tier colors ---
    val TierS = Gold
    val TierA = Blue
    val TierB = Color(0xFF8B8EAA)

    // --- Patch type colors ---
    val PatchBuff = Green
    val PatchNerf = Red
    val PatchRevamp = Gold
    val PatchAdjust = GithubSecondary
    val PatchReversion = Orange

    // --- Advanced Server ---
    val Advanced = Orange
    val AdvancedBg = Color(0xFFF59E0B).copy(alpha = 0.15f)

    // --- Hero detail badges ---
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
    titleLarge = BaseType.titleLarge.copy(fontFamily = Mono),
    titleMedium = BaseType.titleMedium.copy(fontFamily = Mono),
    titleSmall = BaseType.titleSmall.copy(fontFamily = Mono),
    bodyLarge = BaseType.bodyLarge.copy(fontFamily = Mono),
    bodyMedium = BaseType.bodyMedium.copy(fontFamily = Mono),
    bodySmall = BaseType.bodySmall.copy(fontFamily = Mono),
    labelLarge = BaseType.labelLarge.copy(fontFamily = Mono),
    labelMedium = BaseType.labelMedium.copy(fontFamily = Mono),
    labelSmall = BaseType.labelSmall.copy(fontFamily = Mono)
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

// Back-compat aliases - old files still compile.
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


/** App-screen palette. Kept separate so the floating overlay retains its existing appearance. */
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

@Composable
fun AppUiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppUiColors.Gold,
            onPrimary = Color(0xFF171717),
            background = AppUiColors.GithubBg,
            onBackground = AppUiColors.GithubText,
            surface = AppUiColors.GithubCard,
            onSurface = AppUiColors.GithubText,
            surfaceVariant = AppUiColors.GithubSurface,
            onSurfaceVariant = AppUiColors.GithubSecondary,
            outline = AppUiColors.GithubBorder,
            secondary = AppUiColors.Gold,
            onSecondary = Color(0xFF171717),
            error = AppUiColors.Red,
            onError = Color.White
        ),
        typography = Typography(),
        content = content
    )
}
