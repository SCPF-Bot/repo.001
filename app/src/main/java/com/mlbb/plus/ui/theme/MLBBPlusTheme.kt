package com.mlbb.plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
// plus/ui/theme/MLBBPlusTheme.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/theme/MLBBPlusTheme.kt
// ------------------------------------------------------------
// FIXES:
//  [1] Tier colors were not distinct. Old: TierS = Gold,
//      TierA = GithubText (E6EDF3, same as primary text),
//      TierB = GithubSecondary (7D8590). TierA blended into
//      normal text, and A vs B was low contrast. Fix:
//      S = Gold, A = blue 58A6FF, B = gray 8B949E.
//      Blue was previously marked "DO NOT USE" as BlueOld -
//      that left no blue for tiers. BlueOld is kept for
//      back-compat but new code uses TierA.
//  [2] GoldBg / AdvancedBg were opaque solid colors.
//      Old: GoldBg = 0xFFE3B341 (opaque Gold), AdvancedBg =
//      0xFFD29922 (opaque Orange). If used as a background
//      with Gold / Orange text on top, contrast fails.
//      Fix: GoldBg = Gold at 15% alpha, AdvancedBg = Orange
//      at 15% alpha. Use these for badge backgrounds.
//  [3] Back-compat aliases lied about color. Old:
//      InfoBlue = Gold, MLBBTeal = Gold, MLBBBlue = Gold,
//      TierSPlus = Gold, TierAPlus = GithubText. Any old file
//      using InfoBlue / Teal / Blue expecting blue/teal got
//      yellow. Fix: InfoBlue / MLBBBlue = real blue,
//      MLBBTeal = real teal, TierSPlus = brighter gold,
//      TierAPlus = lighter blue.
//  [4] File was named MLBBPlusTheme.kt but had no
//      MLBBPlusTheme() composable. MainActivity built its own
//      MaterialTheme(darkColorScheme(...)) inline, duplicating
//      the theme in two places. Fix: added MLBBPlusTheme()
//      wrapper below. MainActivity now uses it. New screens
//      should use MLBBPlusTheme { ... }, not rebuild
//      darkColorScheme() themselves.
//  [5] GithubDisabled duplicated GithubSecondary. Kept for
//      back-compat, but new code should use GithubSecondary.
// ============================================================

object MLBBPlusColors {
    val GithubBg = Color(0xFF0D1117)
    val GithubCard = Color(0xFF161B22)
    val GithubSurface = Color(0xFF21262D)
    val GithubBorder = Color(0xFF30363D)
    val GithubText = Color(0xFFE6EDF3)
    val GithubSecondary = Color(0xFF7D8590)
    val GithubDisabled = Color(0xFF7D8590)

    val Gold = Color(0xFFE3B341)
    val GoldDark = Color(0xFFD29922)
    // FIX [2]: translucent background, not opaque Gold.
    val GoldBg = Color(0xFFE3B341).copy(alpha = 0.15f)

    val Green = Color(0xFF3FB950)
    val Red = Color(0xFFF85149)
    val Orange = Color(0xFFD29922)
    val BlueOld = Color(0xFF2F81F7)

    // --- Tier colors ---
    // FIX [1]: A is now blue, distinct from text and from B.
    val TierS = Gold
    val TierA = Color(0xFF58A6FF)
    val TierB = Color(0xFF8B949E)

    // --- Patch type colors ---
    val PatchBuff = Green
    val PatchNerf = Red
    val PatchRevamp = Gold
    val PatchAdjust = GithubSecondary
    val PatchReversion = Orange

    // --- Advanced Server (Dori 134, Advanced patch) ---
    val Advanced = Orange
    // FIX [2]: translucent background, not opaque Orange.
    val AdvancedBg = Color(0xFFD29922).copy(alpha = 0.15f)

    // --- Hero detail badges ---
    val OP = Gold
    val Meta = Green
}

// FIX [4]: single theme wrapper. Use this in MainActivity and
// any future Activity. Do NOT rebuild darkColorScheme() elsewhere.
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
            secondary = MLBBPlusColors.Gold,
            onSecondary = Color.Black,
            error = MLBBPlusColors.Red,
            onError = Color.White
        ),
        content = content
    )
}

// Back-compat aliases - old files still compile if you copy-paste.
// New code should use MLBBPlusColors.* directly, not these.
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
// FIX [3]: these are now real blue / teal, not Gold.
val InfoBlue = Color(0xFF58A6FF)
val MLBBRed = MLBBPlusColors.Red
val MLBBTeal = Color(0xFF39C5CF)
val MLBBBlue = Color(0xFF2F81F7)
val TierSPlus = Color(0xFFFFD700)
val TierAPlus = Color(0xFF79C0FF)
