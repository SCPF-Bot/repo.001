package com.mlbb.plus.ui.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mlbb.plus.tts.VoiceGuideManager
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.theme.MLBBPlusColors

/**
 * The floating roam guide in its two shapes:
 *
 * - **Bubble** (both orientations): a small round button. Dragging it moves the window, as before.
 * - **Guide** (landscape only): the full card, opened by tapping the bubble and closed by the
 *   collapse control in its header. Rotating to portrait closes it, and the bubble remains.
 *   Portrait never shows the card, which leaves the screen to the game.
 *
 * The transition animates in both directions. The card grows out of the bubble's position.
 * The card stays composed while the bubble is shown, so its game clock, objective state and
 * voice coach keep their state across a collapse.
 */
@Composable
fun FloatingGuide(
    role: String,
    roleType: String,
    onBack: () -> Unit,
    voice: VoiceGuideManager?
) {
    val landscape = JetOverlay.isLandscape
    var expanded by rememberSaveable { mutableStateOf(false) }
    // The guide is landscape-only: rotating to portrait collapses it, so returning to landscape
    // shows the bubble, not the card.
    LaunchedEffect(landscape) {
        if (!landscape) expanded = false
    }
    val open = guideOpen(landscape = landscape, expanded = expanded)
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "floatingGuideOpen"
    )

    Box(contentAlignment = Alignment.TopCenter) {
        // The card. Grows from the top-centre, which is where the bubble sits.
        Box(
            modifier = Modifier
                .visibleSlot(progress > 0f)
                .graphicsLayer {
                    val scale = START_SCALE + (1f - START_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
        ) {
            RoamGuideWidget(
                role = role,
                roleType = roleType,
                onBack = onBack,
                voice = voice,
                onCollapse = { expanded = false }
            )
        }

        // The bubble. It shrinks away as the card opens.
        Box(
            modifier = Modifier
                .visibleSlot(progress < 1f)
                .graphicsLayer {
                    alpha = 1f - progress
                    val scale = 1f - BUBBLE_SHRINK * progress
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .clip(CircleShape)
                    .background(BubbleBackground)
                    .border(1.dp, BubbleBorder, CircleShape)
                    .clickable(enabled = landscape) { expanded = true }
                    .semantics {
                        contentDescription = if (landscape) {
                            "Open the roam guide"
                        } else {
                            "Roam guide. Turn the phone sideways to open it."
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Shield,
                    contentDescription = null,
                    tint = MLBBPlusColors.Gold,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** True only when the guide may be shown: it is landscape-only, and the player opened it. */
internal fun guideOpen(landscape: Boolean, expanded: Boolean): Boolean = landscape && expanded

/**
 * Keeps the content composed but gives it no size and no place while [visible] is false. A
 * hidden child therefore does not enlarge the overlay window, which would otherwise block touches
 * over the game. Hiding with alpha or scale alone would leave a window-sized transparent area.
 */
private fun Modifier.visibleSlot(visible: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (visible) {
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        layout(0, 0) {}
    }
}

private const val TRANSITION_MS = 280
private const val START_SCALE = 0.4f
private const val BUBBLE_SHRINK = 0.5f
private val BUBBLE_SIZE = 52.dp
private val BubbleBackground = MLBBPlusColors.GithubBg.copy(alpha = 0.9f)
private val BubbleBorder = MLBBPlusColors.GithubBorder.copy(alpha = 0.6f)
