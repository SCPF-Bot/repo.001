package com.mlbb.assistant.presentation.overlay

// ── Merged: RoamGuideBubble.kt + FloatingBubble.kt ───────────────────────────────
// WHY MERGE: Both are the same kind of UI — small circular launcher bubbles, no shared
// state, no shared data. Merging them keeps all bubble UI in one place without
// touching any logic. MiniWidget.kt is NOT merged here — it's the 300x200 draft
// orchestrator with its own components/, not a bubble.
// UI logic unchanged: RoamGuideBubble 48dp 'R' with gold border,
// FloatingBubble 56dp pulsing bubble with phase label.

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary

/** Compact launcher bubble for opening the roam guide from the minimized overlay. */
@Composable
fun RoamGuideBubble(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF161B22))
            .border(2.dp, Color(0xFFE3B341), CircleShape)
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Open roam guide" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "R",
            color = Color(0xFFE3B341),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The 56 dp draggable bubble shown when the overlay is minimised.
 *
 * Drag is handled at the View level in OverlayService; when a gesture stays
 * within the drag threshold it is re-dispatched as a synthetic tap so that
 * [onTap] fires correctly without fighting the View touch listener.
 */
@Composable
fun FloatingBubble(session: DraftSession, onTap: () -> Unit) {
    val isDraftActive = session.phase !in listOf(DraftPhase.IDLE, DraftPhase.COMPLETE)

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1.0f,
        targetValue  = if (isDraftActive) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble_scale"
    )
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue  = if (isDraftActive) 0.90f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble_glow"
    )

    val phaseLabel = when (session.phase) {
        DraftPhase.SETUP       -> "SETUP"
        DraftPhase.BAN_ROUND_1 -> "BAN"
        DraftPhase.BAN_ROUND_2 -> "BAN R2"
        DraftPhase.PICK        -> "PICK"
        DraftPhase.TRADING     -> "TRADE"
        DraftPhase.COMPLETE    -> "DONE"
        DraftPhase.IDLE        -> ""
    }

    val accentColor = when (session.phase) {
        DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 ->
            androidx.compose.ui.graphics.Color(0xFFCC2233)
        DraftPhase.PICK ->
            androidx.compose.ui.graphics.Color(0xFF2255CC)
        DraftPhase.TRADING ->
            androidx.compose.ui.graphics.Color(0xFFFFA500)
        else -> MLBBGold
    }

    Box(contentAlignment = Alignment.Center) {
        // Animated glow ring (only during active draft)
        if (isDraftActive) {
            Box(
                Modifier
                    .size(66.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = glowAlpha * 0.25f), accentColor.copy(alpha = 0f))
                        )
                    )
                    .border(1.5.dp, accentColor.copy(alpha = glowAlpha), CircleShape)
            )
        }

        // Main bubble — clickable so the View-level re-dispatched tap reaches here
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(if (isDraftActive) scale else 1f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SurfaceElevated, SurfaceDark)))
                .border(2.dp, if (isDraftActive) accentColor else SurfaceMid, CircleShape)
                .clickable { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Sword icon as text (no emoji — uses Unicode sword character)
                Text(
                    text     = if (isDraftActive) phaseLabel.take(1) else "D",
                    color    = if (isDraftActive) accentColor else MLBBGold,
                    fontSize = if (isDraftActive) 16.sp else 20.sp,
                    fontWeight = FontWeight.Black
                )
                if (phaseLabel.isNotEmpty()) {
                    Text(
                        text       = phaseLabel,
                        color      = TextPrimary.copy(alpha = 0.80f),
                        fontSize   = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
