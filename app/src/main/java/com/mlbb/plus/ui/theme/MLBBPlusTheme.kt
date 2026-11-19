package com.mlbb.plus.ui.theme

import androidx.compose.ui.graphics.Color

// MLBB Plus — GitHub Dark + Gold Accent
// Old: com.mlbb.assistant.presentation.common.theme.*
// New: single source of truth, no gradients, flat matte

object MLBBPlusColors {
    val GithubBg = Color(0xFF0D1117)        // screen background
    val GithubCard = Color(0xFF161B22)      // cards, dropdowns, search
    val GithubSurface = Color(0xFF21262D)   // unselected chips
    val GithubBorder = Color(0xFF30363D)    // borders
    val GithubText = Color(0xFFE6EDF3)      // primary text
    val GithubSecondary = Color(0xFF7D8590) // subtitles, hints
    val GithubDisabled = Color(0xFF7D8590)

    val Gold = Color(0xFFE3B341)            // accent — replaces old MLBBGold + MLBBBlue
    val GoldDark = Color(0xFFD29922)
    val GoldBg = Color(0xFFE3B341)

    val Green = Color(0xFF3FB950)           // READY, win rate, granted
    val Red = Color(0xFFF85149)             // ban rate, danger
    val Orange = Color(0xFFD29922)          // needs permission
    val BlueOld = Color(0xFF2F81F7)         // DO NOT USE — kept for reference only
}

// Back-compat aliases so old files still read if you copy-paste
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
val TierS = MLBBPlusColors.Gold
val TierSPlus = MLBBPlusColors.Gold
val TierA = MLBBPlusColors.Gold
val TierAPlus = MLBBPlusColors.Gold
val TierB = MLBBPlusColors.GithubSeco
ndary
