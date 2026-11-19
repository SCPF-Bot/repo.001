package com.mlbb.plus.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// plus/ui/theme/MLBBPlusTheme.kt
// GitHub path: app/src/main/java/com/mlbb/plus/ui/theme/MLBBPlusTheme.kt
// ------------------------------------------------------------
// GitHub Dark + Gold — LOCKED STYLE, flat, no glow, no neon
// Does NOT read heroes.json / items.json / images —
// so NO breaking change needed for the new assets.
// ------------------------------------------------------------
// 2 SMALL FIXES for new JSON features (added below):
//   1. Tier colors were all Gold — S and A looked identical.
//      Now: S = Gold, A = light gray, B = dark gray.
//   2. Patch Notes now shows BUFF / NERF / REVAMP / ADJUST badges
//      from patch_notes.json — added Patch* colors for them.
//   3. Heroes now shows ADV badge for Dori (134) — added Advanced color.
// Old code still works — these are additions, not renames.
// ============================================================

object MLBBPlusColors {
    val GithubBg = Color(0xFF0D1117)        // screen background
    val GithubCard = Color(0xFF161B22)      // cards, dropdowns, search
    val GithubSurface = Color(0xFF21262D)   // unselected chips
    val GithubBorder = Color(0xFF30363D)    // borders
    val GithubText = Color(0xFFE6EDF3)      // primary text
    val GithubSecondary = Color(0xFF7D8590) // subtitles, hints
    val GithubDisabled = Color(0xFF7D8590)

    val Gold = Color(0xFFE3B341)            // accent
    val GoldDark = Color(0xFFD29922)
    val GoldBg = Color(0xFFE3B341)

    val Green = Color(0xFF3FB950)           // READY, win rate, granted, BUFF
    val Red = Color(0xFFF85149)             // ban rate, danger, NERF
    val Orange = Color(0xFFD29922)          // needs permission, ADV badge, REVERSION
    val BlueOld = Color(0xFF2F81F7)         // DO NOT USE — kept for reference only

    // --- NEW: Tier colors (from heroes.json tier field) ---
    val TierS = Gold
    val TierA = GithubText // was Gold — fixed so S vs A are different
    val TierB = GithubSecondary

    // --- NEW: Patch type colors (from patch_notes.json heroChanges[].type) ---
    val PatchBuff = Green
    val PatchNerf = Red
    val PatchRevamp = Gold
    val PatchAdjust = GithubSecondary
    val PatchReversion = Orange

    // --- NEW: Advanced Server (Dori 134, Advanced patch 2.2.32) ---
    val Advanced = Orange
    val AdvancedBg = Color(0xFFD29922)

    // --- NEW: Hero detail badges ---
    val OP = Gold
    val Meta = Green
}

// Back-compat aliases — old files still compile if you copy-paste
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
val InfoBlue = MLBBPlusColors.Gold
val MLBBRed = MLBBPlusColors.Red
val MLBBTeal = MLBBPlusColors.Gold
val MLBBBlue = MLBBPlusColors.Gold
val TierSPlus = MLBBPlusColors.Gold
val TierAPlus = MLBBPlusColors.GithubText
