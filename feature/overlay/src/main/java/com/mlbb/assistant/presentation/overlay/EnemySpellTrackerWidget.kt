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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TrackerSurface = Color(0xF2161B22)
private val TrackerSurfaceCooling = Color(0xFF2A2413)
private val TrackerBorder = Color(0xFF30363D)
private val TrackerGold = Color(0xFFFFC94D)
private val TrackerText = Color(0xFFE6EDF3)
private val TrackerMuted = Color(0xFF9AA7B4)

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

// Short labels so lane + spell still fit when the whole widget is only
// ~52% of screen width (see RoamGuideWidget width comment).
private val spellAbbreviations = mapOf(
    "Aegis" to "AEG",
    "Arrival" to "ARR",
    "Execute" to "EXE",
    "Flicker" to "FLK",
    "Flameshot" to "FLA",
    "Inspire" to "INS",
    "Petrify" to "PET",
    "Purify" to "PUR",
    "Retribution" to "RET",
    "Revitalize" to "REV",
    "Sprint" to "SPR",
    "Vengeance" to "VEN"
)

private fun abbreviationFor(spellName: String): String =
    spellAbbreviations[spellName] ?: spellName.take(3).uppercase()

private fun initialTrackedSpells() = listOf("EXP", "JG", "MID", "GOLD", "ROAM").map { lane ->
    val initialSpell = if (lane == "JG") "Retribution" else "Flicker"
    TrackedLaneSpell(lane, availableSpells.first { it.name == initialSpell })
}

private fun formatCooldown(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/**
 * Separate, compact enemy battle-spell cooldown tracker. Tap a lane when its
 * enemy uses the selected spell; long-press to change spell. The cooldown is
 * derived from game time, so pausing or adjusting the game clock stays coherent.
 *
 * Width: this composable is intentionally width-agnostic — it fills whatever
 * its parent gives it. [RoamGuideWidget] constrains that parent to the green-line
 * width (~52% of screen in landscape), so each of the 5 cells stays ~1/5 of that.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnemySpellTrackerWidget(elapsedSeconds: Int, modifier: Modifier = Modifier) {
    var trackedSpells by rememberSaveable { mutableStateOf(initialTrackedSpells()) }
    var openPickerLane by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // V7: ENEMY / "Tap = used · Hold = change" header REMOVED.
        // Moved to ? help menu in Roam header. Saves one row + no clipping.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCoolingDown) TrackerSurfaceCooling else TrackerSurface)
                            .border(
                                1.dp,
                                if (isCoolingDown) TrackerGold.copy(alpha = 0.85f) else TrackerBorder,
                                RoundedCornerShape(8.dp)
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            // Lane always visible. When cooling, also show the spell
                            // abbreviation so you don't lose which spell is on CD.
                            Text(
                                text = if (isCoolingDown) {
                                    "${laneSpell.lane} · ${abbreviationFor(laneSpell.spell.name)}"
                                } else {
                                    laneSpell.lane
                                },
                                color = if (isCoolingDown) TrackerGold else TrackerMuted,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCoolingDown) formatCooldown(remaining) else laneSpell.spell.name,
                                color = if (isCoolingDown) TrackerGold else TrackerText,
                                fontSize = if (isCoolingDown) 11.sp else 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Progress is now a thin bar at the bottom instead of a full
                        // gold fill — the timer stays readable at any progress.
                        if (isCoolingDown) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .background(TrackerGold, RoundedCornerShape(2.dp))
                                    .align(Alignment.BottomStart)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = openPickerLane == laneSpell.lane,
                        onDismissRequest = { openPickerLane = null }
                    ) {
                        availableSpells.forEach { spell ->
                            val isSelected = spell.name == laneSpell.spell.name
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isSelected) "✓ ${spell.name} · ${spell.cooldownSeconds}s"
                                        else "${spell.name} · ${spell.cooldownSeconds}s",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TrackerGold else Color.Unspecified
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
    }
}
