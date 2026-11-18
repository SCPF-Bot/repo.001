package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TrackerSurface = Color(0x99161B22)
private val TrackerBorder = Color(0xFF30363D)
private val TrackerGold = Color(0xFFE3B341)
private val TrackerText = Color(0xFFC9D1D9)
private val TrackerMuted = Color(0xFF8B949E)

private data class TrackedSpell(val name: String, val cooldownSeconds: Int)
private data class TrackedLaneSpell(
    val lane: String,
    val spell: TrackedSpell,
    val usedAtSeconds: Int? = null
)

private val availableSpells = listOf(
    TrackedSpell("Aegis", 75),
    TrackedSpell("Arrival", 75),
    TrackedSpell("Execute", 90),
    TrackedSpell("Flicker", 120),
    TrackedSpell("Flameshot", 50),
    TrackedSpell("Inspire", 75),
    TrackedSpell("Petrify", 75),
    TrackedSpell("Purify", 90),
    TrackedSpell("Retribution", 35),
    TrackedSpell("Revitalize", 75),
    TrackedSpell("Sprint", 100),
    TrackedSpell("Vengeance", 75)
)

private fun initialTrackedSpells() = listOf("EXP", "JG", "MID", "GOLD", "ROAM").map { lane ->
    val initialSpell = if (lane == "JG") "Retribution" else "Flicker"
    TrackedLaneSpell(lane, availableSpells.first { it.name == initialSpell })
}

private fun formatCooldown(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/**
 * Separate, compact enemy battle-spell cooldown tracker. Tap a lane when its
 * enemy uses the selected spell; long-press to change spell. The cooldown is
 * derived from game time, so pausing or adjusting the game clock stays coherent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnemySpellTrackerWidget(elapsedSeconds: Int, modifier: Modifier = Modifier) {
    var trackedSpells by rememberSaveable { mutableStateOf(initialTrackedSpells()) }
    var openPickerLane by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            trackedSpells.forEach { laneSpell ->
                val elapsedSinceUse = laneSpell.usedAtSeconds?.let { (elapsedSeconds - it).coerceAtLeast(0) }
                val remaining = if (elapsedSinceUse == null) 0
                    else (laneSpell.spell.cooldownSeconds - elapsedSinceUse).coerceAtLeast(0)
                val isCoolingDown = elapsedSinceUse != null && remaining > 0
                val progress = if (isCoolingDown) {
                    (elapsedSinceUse!!.toFloat() / laneSpell.spell.cooldownSeconds).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TrackerSurface)
                            .border(
                                1.dp,
                                if (isCoolingDown) TrackerGold else TrackerBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    trackedSpells = trackedSpells.map { current ->
                                        if (current.lane == laneSpell.lane) {
                                            current.copy(usedAtSeconds = if (isCoolingDown) null else elapsedSeconds)
                                        } else current
                                    }
                                },
                                onLongClick = { openPickerLane = laneSpell.lane },
                                onLongClickLabel = "Change ${laneSpell.lane} battle spell"
                            )
                            .semantics {
                                contentDescription = if (isCoolingDown) {
                                    "${laneSpell.lane}, ${laneSpell.spell.name}, ${formatCooldown(remaining)} remaining. Tap to cancel cooldown; long-press to change spell."
                                } else {
                                    "${laneSpell.lane}, ${laneSpell.spell.name} ready. Tap when used; long-press to change spell."
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCoolingDown) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(28.dp)
                                    .background(TrackerGold.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
                                    .align(Alignment.CenterStart)
                            )
                        }
                        Text(
                            text = if (isCoolingDown) formatCooldown(remaining) else laneSpell.lane,
                            color = if (isCoolingDown) Color.Black else TrackerText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    DropdownMenu(
                        expanded = openPickerLane == laneSpell.lane,
                        onDismissRequest = { openPickerLane = null }
                    ) {
                        availableSpells.forEach { spell ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${spell.name} · ${spell.cooldownSeconds}s",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    trackedSpells = trackedSpells.map { current ->
                                        if (current.lane == laneSpell.lane) current.copy(spell = spell, usedAtSeconds = null)
                                        else current
                                    }
                                    openPickerLane = null
                                }
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Tap when used · tap again to cancel · hold to change spell",
            color = TrackerMuted,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
