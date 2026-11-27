package com.mlbb.plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ============================================================
// MLBB Plus — spacing / shape / motion tokens (8dp grid).
//
// Screen margin: 16dp (phones), 24dp on >=600dp windows.
// Card gap: 12dp. Section gap: 24dp. Max content width:
// 960dp (existing Home/Heroes/Items constraint — kept).
// Breakpoints: <600dp phone (bottom nav), 600–839dp rail,
// >=840dp rail + two-pane (Knowledge Base). See MainActivity.
//
// Touch targets: minimum 48dp (Material / WCAG 2.5.8).
// Visual controls may be 40dp only if padded to 48dp hit
// area — the old 32dp CompactDropdown fails this.
// ============================================================

object AppDimens {
    val Space2 = 2.dp
    val Space4 = 4.dp
    val Space6 = 6.dp
    val Space8 = 8.dp
    val Space10 = 10.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp

    val ScreenMargin = 16.dp
    val ScreenMarginWide = 24.dp
    val CardGap = 12.dp
    val SectionGap = 24.dp
    val MaxContentWidth = 960.dp

    val TouchTargetMin = 48.dp
    val IconSize = 24.dp
    val IconSizeSmall = 18.dp

    // Motion (ms). Respect reduced motion: skip non-essential
    // animation when the system animator duration is 0.
    // const: compile-time constants, so animation specs inline them
    // (detekt MayBeConst).
    const val MotionFastMs = 150
    const val MotionMediumMs = 250
    const val MotionSlowMs = 300
}

object AppShapes {
    val Card = RoundedCornerShape(12.dp)
    val CardLarge = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Input = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(8.dp)
    // Buttons use Material 3 full (pill) shape by default.
}
