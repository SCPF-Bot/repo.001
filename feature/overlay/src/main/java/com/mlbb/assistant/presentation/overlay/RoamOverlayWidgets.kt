package com.mlbb.assistant.presentation.overlay

// ── Merged: RoamGuideWidget.kt + EnemySpellTrackerWidget.kt ─────────────────────
// DELETED: OutlinedText_SIMPLE.kt (internal OutlinedText with no outline)
// DELETED: GuideControl_SIMPLE.kt (internal GuideControl with no outline, 80% bg, 6dp/5dp padding)
// DELETED: OutlinedTrackerText (duplicate of OutlinedText) — tracker now uses OutlinedText
// WHY MERGE: RoamGuideWidget already calls EnemySpellTrackerWidget, they share the same
// width (52% green line), same GitHub Dark palette, same outline helper. Keeping them
// in separate files forced two copies of OutlinedText to exist, which is how the
// shadowing bug happened. One file = one OutlinedText, one GuideControl, no shadowing.
// UI logic unchanged: 52% landscape width, 45% guide / 15% tracker opacity, 1px gap,
// timer-is-button, 2-col spell picker, gold-fill cooldown, black outline on all text.

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val GuideBackground = Color(0x730D1117) // GitHub Dark #0d1117 at 45%
private val GuideSurface = Color(0x73161B22) // GitHub Dark #161b22 at 45%
private val GuideBorder = Color(0x7330363D) // GitHub Dark #30363d at 45%
private val GuideAccent = Color(0xFF58A6FF) // GitHub Dark blue
private val GuideText = Color(0xFFE6EDF3) // GitHub Dark primary
private val GuideMuted = Color(0xFF8B949E) // GitHub Dark secondary
private val GuideGold = Color(0xFFE3B341) // GitHub Dark yellow
private val GuideGreen = Color(0xFF3FB950)
private val GuideRed = Color(0xFFF85149) // GitHub Dark red

private val TrackerSurface = Color(0x26161B22) // GitHub Dark #161b22 at 15%
private val TrackerSurfaceCooling = Color(0x26161B22) // GitHub Dark #161b22 at 15% — gold fill drawn on top
private val TrackerBorder = Color(0x2630363D) // GitHub Dark #30363d at 15%
private val TrackerGold = Color(0xFFE3B341) // GitHub Dark yellow
private val TrackerText = Color(0xFFE6EDF3) // GitHub Dark primary
private val TrackerMuted = Color(0xFF8B949E) // GitHub Dark secondary

// --- Widget width: matched to the green line in the reference photo ---
// Measured from the photo (720px wide): green line runs x = 150 → 526,
// i.e. left = 20.8%, right = 72.9%, width = 52.1% of screen width.
// Previous value was 0.62f in landscape (too wide, blocked extra vision).
// Portrait is not the in-game case (MLBB is landscape) so it stays wide
// for readability, but landscape — the real case — is now exactly 0.52f.
private const val WIDGET_WIDTH_FRACTION_LANDSCAPE = 0.52f
private const val WIDGET_WIDTH_FRACTION_PORTRAIT = 0.92f
// FIX V2: WIDGET_MIN_WIDTH_DP (300dp) DELETED. It was the reason the widget looked
// "a bit too long" — on a 360dp-wide window 52% = 187dp, coerced up to 300dp = 83%
// of screen. With FLK/RET abbreviations the 5 cells fit in 52% without a min.
private const val ROAM_GUIDE_ASSET = "roam_guide.json"

private data class RoamStep(
    val startSeconds: Int,
    val endSeconds: Int,
    val description: String,
    val timeLabel: String,
    val phase: String,
    val checklist: List<String>,
    val vision: String,
    val decision: String,
    val dont: String
)
// V4: callout field totally removed — no more phase · callout line.
// Description is shown directly (see OutlinedText below).

private data class RoamBlessing(
    val id: String,
    val name: String,
    val effect: String
)

private data class RoamPlan(
    val id: String,
    val label: String,
    val blessing: String,
    val blessingId: String,
    val heroes: List<String>,
    val emblem: String,
    val steps: List<RoamStep>,
    val turtleContest: String,
    val turtleTrade: String,
    val lordContest: String,
    val lordTrade: String
)

private data class RoamGuideData(
    val patch: String = "",
    val blessings: List<RoamBlessing> = emptyList(),
    val plans: List<RoamPlan> = emptyList()
)

private enum class ObjectiveType { TURTLE, LORD }

private data class ObjectiveStatus(
    val type: ObjectiveType,
    val count: Int,
    val isAlive: Boolean,
    val secondsUntil: Int
) {
    val label: String get() = if (type == ObjectiveType.TURTLE) "Turtle" else "Lord"
}

private data class ObjectiveSnapshot(
    val turtleSlain: Int,
    val lordSlain: Int,
    val lastSlainAt: Int?,
    val lastSlainWasLord: Boolean
)

private fun JSONObject.stringList(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until values.length()) {
            values.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun parseStep(json: JSONObject): RoamStep {
    val description = json.optString("instruction").takeIf(String::isNotBlank)
        ?: json.optString("description")
    return RoamStep(
        startSeconds = json.optInt("startSeconds").coerceAtLeast(0),
        endSeconds = json.optInt("endSeconds").coerceAtLeast(0),
        description = description,
        timeLabel = json.optString("timeLabel"),
        phase = json.optString("phase"),
        checklist = json.stringList("checklist"),
        vision = json.optString("vision"),
        decision = json.optString("decision"),
        dont = json.optString("dont")
    )
}

private fun parsePlan(json: JSONObject): RoamPlan {
    val stepArray = json.optJSONArray("steps") ?: JSONArray()
    val steps = buildList {
        for (index in 0 until stepArray.length()) {
            val step = stepArray.optJSONObject(index) ?: continue
            add(parseStep(step))
        }
    }.sortedBy(RoamStep::startSeconds)
    val templates = json.optJSONObject("objective_templates")
    val heroesArray = json.optJSONArray("heroes")
    val heroes = if (heroesArray != null) {
        buildList {
            for (index in 0 until heroesArray.length()) {
                val entry = heroesArray.optJSONObject(index)
                if (entry != null) entry.optString("name").takeIf(String::isNotBlank)?.let(::add)
                else heroesArray.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    } else {
        json.optString("heroes").split(',').map(String::trim).filter(String::isNotEmpty)
    }

    return RoamPlan(
        id = json.optString("id"),
        label = json.optString("label").ifBlank { json.optString("id") },
        blessing = json.optString("blessing"),
        blessingId = json.optString("blessing_id"),
        heroes = heroes,
        emblem = json.optString("emblem"),
        steps = steps,
        turtleContest = templates?.optString("turtle_contest").orEmpty(),
        turtleTrade = templates?.optString("turtle_trade").orEmpty(),
        lordContest = templates?.optString("lord_contest").orEmpty(),
        lordTrade = templates?.optString("lord_trade").orEmpty()
    )
}

private fun loadRoamGuide(context: Context): RoamGuideData = runCatching {
    val source = context.assets.open(ROAM_GUIDE_ASSET).bufferedReader().use { it.readText() }
    val root = JSONObject(source)
    val blessingArray = root.optJSONArray("blessings") ?: JSONArray()
    val blessings = buildList {
        for (index in 0 until blessingArray.length()) {
            val blessing = blessingArray.optJSONObject(index) ?: continue
            add(
                RoamBlessing(
                    id = blessing.optString("id"),
                    name = blessing.optString("name"),
                    effect = blessing.optString("effect")
                )
            )
        }
    }
    val roles = root.optJSONArray("roles") ?: JSONArray()
    val plans = buildList {
        for (index in 0 until roles.length()) {
            val role = roles.optJSONObject(index) ?: continue
            parsePlan(role).takeIf { it.steps.isNotEmpty() }?.let(::add)
        }
    }
    RoamGuideData(
        patch = root.optJSONObject("meta")?.optString("patch").orEmpty(),
        blessings = blessings,
        plans = plans
    )
}.getOrDefault(RoamGuideData())

private fun formatGameTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}

/**
 * Computes the next objective using game time and manually recorded slain events.
 * Before the first Turtle kill, the known schedule is used; subsequent objective
 * respawns are based on the time the player marked the last objective slain.
 */
private fun computeObjective(
    elapsedSeconds: Int,
    turtleSlain: Int,
    lordSlain: Int,
    lastSlainAt: Int?
): ObjectiveStatus {
    if (lordSlain > 0) {
        val slainAt = lastSlainAt ?: elapsedSeconds
        val nextSpawn = slainAt + if (slainAt < 18 * 60) 180 else 120
        return ObjectiveStatus(
            type = ObjectiveType.LORD,
            count = lordSlain + 1,
            isAlive = elapsedSeconds >= nextSpawn,
            secondsUntil = (nextSpawn - elapsedSeconds).coerceAtLeast(0)
        )
    }

    if (elapsedSeconds < 2 * 60 && turtleSlain == 0) {
        return ObjectiveStatus(ObjectiveType.TURTLE, 1, false, 2 * 60 - elapsedSeconds)
    }

    if (elapsedSeconds >= 8 * 60 || turtleSlain >= 3 || (lastSlainAt ?: 0) > 6 * 60) {
        val lordSpawn = (lastSlainAt?.plus(120) ?: 8 * 60)
        return ObjectiveStatus(
            type = ObjectiveType.LORD,
            count = 1,
            isAlive = elapsedSeconds >= lordSpawn,
            secondsUntil = (lordSpawn - elapsedSeconds).coerceAtLeast(0)
        )
    }

    val nextTurtle = lastSlainAt?.plus(120) ?: 2 * 60
    if (nextTurtle >= 8 * 60) {
        return ObjectiveStatus(
            type = ObjectiveType.LORD,
            count = 1,
            isAlive = elapsedSeconds >= 8 * 60,
            secondsUntil = (8 * 60 - elapsedSeconds).coerceAtLeast(0)
        )
    }
    return ObjectiveStatus(
        type = ObjectiveType.TURTLE,
        count = turtleSlain + 1,
        isAlive = elapsedSeconds >= nextTurtle,
        secondsUntil = (nextTurtle - elapsedSeconds).coerceAtLeast(0)
    )
}

/**
 * Compact roam macro guide for use over the game screen. The content width is
 * explicitly bounded because the host overlay window is WRAP_CONTENT; relying on
 * fillMaxWidth alone made long instruction lines size/clamp unpredictably.
 *
 * Width is matched to the green line in the reference photo: 52% of screen
 * width in landscape (150–526px on a 720px-wide screenshot). See
 * WIDGET_WIDTH_FRACTION_LANDSCAPE. The host overlay window should centre
 * this widget horizontally — the widget itself only controls its own width.
 */
@OptIn(ExperimentalLayoutApi::class)

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
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val guide = remember(context) { loadRoamGuide(context) }
    val plans = guide.plans
    var selectedPlanId by rememberSaveable { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var descriptionHidden by rememberSaveable { mutableStateOf(false) }
    var hideMenuExpanded by remember { mutableStateOf(false) }
    var turtleSlain by rememberSaveable { mutableIntStateOf(0) }
    var lordSlain by rememberSaveable { mutableIntStateOf(0) }
    var lastSlainAt by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastSlainWasLord by rememberSaveable { mutableStateOf(false) }
    var previousObjective by remember { mutableStateOf<ObjectiveSnapshot?>(null) }

    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1 else isRunning = false
        }
    }

    val currentStep = selectedPlan?.steps?.firstOrNull {
        elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds
    } ?: selectedPlan?.steps?.lastOrNull()
    val objective = computeObjective(elapsedSeconds, turtleSlain, lordSlain, lastSlainAt)
    val isTurtle = objective.type == ObjectiveType.TURTLE
    val objectiveText = if (objective.isAlive) {
        if (isTurtle) "Turtle ${objective.count} UP" else "Lord UP"
    } else {
        "${objective.label} in ${formatGameTime(objective.secondsUntil)}"
    }
    val objectiveColor = when {
        objective.isAlive && isTurtle -> GuideGold
        objective.isAlive -> GuideRed
        isTurtle -> GuideGold
        else -> GuideRed
    }
    val objectiveDescription = when {
        objective.isAlive && isTurtle -> selectedPlan?.turtleContest.orEmpty()
        objective.isAlive -> selectedPlan?.lordContest.orEmpty()
        lastSlainAt != null -> "${objective.label} slain. Next in ${formatGameTime(objective.secondsUntil)}. Push, farm, or reset."
        else -> currentStep?.description.orEmpty()
    }
    // FIX V3 (touch): window is now WRAP_CONTENT, so fillMaxWidth(0.52f)
    // has nothing to be 52% OF — it would wrap / stretch wrong.
    // Width must be absolute: 52% of the REAL display width.
    // displayMetrics (not LocalConfiguration) because LocalConfiguration
    // in an overlay window reports portrait size even in-game (landscape).
    // No 300dp min — that was the old overshoot (52% of 360dp = 187dp
    // coerced to 300dp = 83%). With real landscape width (e.g. 800dp),
    // 52% = 416dp, already > 300dp, so no min is needed.
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
    val widgetWidth = (screenWidthDp * WIDGET_WIDTH_FRACTION_LANDSCAPE)
        .coerceAtMost((screenWidthDp - 8).toFloat())
    val blessing = guide.blessings.firstOrNull {
        it.id == selectedPlan?.blessingId ||
            it.name.equals(selectedPlan?.blessing?.substringBefore('/')?.trim(), ignoreCase = true)
    }

    // Gap between the two widgets = exactly 1 physical pixel.
    // 1.dp is NOT 1px (at 3x density 1.dp = 3px), so convert 1px -> dp.
    val onePx = with(LocalDensity.current) { 1.toDp() }
    // FIX V3: was fillMaxWidth(0.52f) — only works in a MATCH_PARENT window.
    // Window is now WRAP_CONTENT (for touch passthrough), so the Column
    // must define the window width: absolute 52% of display.
    // This Column IS the window width — sides stay outside the window.
    Column(
        modifier = Modifier
            .width(widgetWidth.dp),
        // No outer horizontal padding: the card itself is 52%,
        // so its visible edges line up exactly with the green line.
        verticalArrangement = Arrangement.spacedBy(onePx)
    ) {
        if (selectedPlan == null || currentStep == null) {
            GuideMessage("Roam guide unavailable — check roam_guide.json")
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GuideBackground, RoundedCornerShape(8.dp))
                .border(1.dp, GuideBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuideControl(
                    label = "${if (isRunning) "⏸ " else "▶ "}${formatGameTime(elapsedSeconds)}",
                    color = if (isRunning) GuideGreen else GuideText,
                    modifier = Modifier.width(56.dp),
                    fontWeight = FontWeight.Bold,
                    onClick = { isRunning = !isRunning && elapsedSeconds < 18 * 60 },
                    contentDescription = if (isRunning) "Pause timer" else "Start timer"
                    // Timer IS the play/pause button — tap the timer to start/pause. No separate button.
                )
                Box(modifier = Modifier.weight(1f)) {
                    GuideControl(
                        selectedPlan.label.substringBefore(" - ").trim(),
                        GuideAccent,
                        Modifier.fillMaxWidth(),
                        maxLines = 1,
                        onClick = { planMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = planMenuExpanded,
                        onDismissRequest = { planMenuExpanded = false }
                    ) {
                        plans.forEach { plan ->
                            DropdownMenuItem(
                                text = {
                                    OutlinedText(
                                        plan.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    selectedPlanId = plan.id
                                    planMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                // FIX: objective had no weight, so it squeezed Plan ("Crowd Control") to ~40dp
                // and clipped it on both sides in screenshot. Give it weight(false) + maxLines 1.
                GuideControl(
                    objectiveText,
                    objectiveColor,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    background = objectiveColor.copy(alpha = if (objective.isAlive) 0.22f else 0.12f),
                    borderColor = objectiveColor.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                    onClick = {
                        if (objective.isAlive) {
                            previousObjective = ObjectiveSnapshot(
                                turtleSlain,
                                lordSlain,
                                lastSlainAt,
                                lastSlainWasLord
                            )
                            lastSlainWasLord = !isTurtle
                            if (isTurtle) turtleSlain += 1 else lordSlain += 1
                            lastSlainAt = elapsedSeconds
                        }
                    },
                    contentDescription = if (objective.isAlive) "Mark ${objective.label} as slain" else objectiveText
                )
                GuideControl(if (detailsExpanded) "−" else "+", GuideMuted, modifier = Modifier.width(24.dp)) {
                    detailsExpanded = !detailsExpanded
                }
                GuideControl("×", GuideMuted, modifier = Modifier.width(24.dp), onClick = onBack, contentDescription = "Close roam guide")
            }

            if (!descriptionHidden) {
                // FIX V4: call-out totally removed. Was:
                //   phase · callout (+ " — description" when expanded)
                //   e.g. "Setup · Guard buff, don't feed"
                // Now: description directly, no prefix / suffix.
                // objectiveDescription already = currentStep.description
                // (or turtle/lord template when that objective is relevant).
                if (objectiveDescription.isNotBlank()) {
                    OutlinedText(
                        text = objectiveDescription,
                        color = GuideText,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        maxLines = if (detailsExpanded) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (detailsExpanded) {
                    OutlinedText(
                        "${currentStep.timeLabel.ifBlank { "${formatGameTime(currentStep.startSeconds)}–${formatGameTime(currentStep.endSeconds)}" }}${guide.patch.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                        color = GuideMuted,
                        fontSize = 9.sp
                    )
                    currentStep.checklist.forEach { item ->
                        OutlinedText("• $item", color = GuideText, fontSize = 9.sp)
                    }
                    if (currentStep.vision.isNotBlank()) GuideDetail("Vision", currentStep.vision)
                    if (currentStep.decision.isNotBlank()) GuideDetail("Decision", currentStep.decision)
                    if (currentStep.dont.isNotBlank()) GuideDetail("Avoid", currentStep.dont)
                    if (blessing != null && blessing.effect.isNotBlank()) {
                        GuideDetail(selectedPlan.blessing, blessing.effect)
                    }
                    if (elapsedSeconds == 0 && selectedPlan.heroes.isNotEmpty()) {
                        GuideDetail("Suggested heroes", selectedPlan.heroes.take(6).joinToString())
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        GuideControl("−10s", GuideMuted) { elapsedSeconds = (elapsedSeconds - 10).coerceAtLeast(0) }
                        GuideControl("+10s", GuideMuted) { elapsedSeconds = (elapsedSeconds + 10).coerceAtMost(18 * 60) }
                        GuideControl("Reset", GuideMuted) {
                            elapsedSeconds = 0
                            isRunning = false
                            turtleSlain = 0
                            lordSlain = 0
                            lastSlainAt = null
                            lastSlainWasLord = false
                            previousObjective = null
                        }
                        if (previousObjective != null) {
                            GuideControl("Undo slain", GuideGold, onClick = {
                                previousObjective?.let { snapshot ->
                                    turtleSlain = snapshot.turtleSlain
                                    lordSlain = snapshot.lordSlain
                                    lastSlainAt = snapshot.lastSlainAt
                                    lastSlainWasLord = snapshot.lastSlainWasLord
                                }
                                previousObjective = null
                            })
                        }
                        Box {
                            GuideControl(
                                if (descriptionHidden) "Show text" else "Hide text",
                                GuideMuted,
                                onClick = { hideMenuExpanded = true }
                            )
                            DropdownMenu(
                                expanded = hideMenuExpanded,
                                onDismissRequest = { hideMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { OutlinedText("Show instructions") },
                                    onClick = { descriptionHidden = false; hideMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { OutlinedText("Hide instructions") },
                                    onClick = { descriptionHidden = true; hideMenuExpanded = false }
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(GuideBorder, RoundedCornerShape(2.dp))
                    ) {
                        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
                        val progress = ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(GuideAccent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // The spell cooldown panel is its own composable/file so it can be changed
        // independently from the much larger guide UI.
        EnemySpellTrackerWidget(
            elapsedSeconds = elapsedSeconds,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EnemySpellTrackerWidget(elapsedSeconds: Int, modifier: Modifier = Modifier) {
    var trackedSpells by rememberSaveable { mutableStateOf(initialTrackedSpells()) }
    var openPickerLane by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Header replaces the old bottom hint, which truncated at 52% width.
        // It tells the gesture once, up top, where it never gets cut off.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FIX: header was 2 long strings (170dp) in a 224dp widget, clipped in screenshot.
            // Shorten to fit + save height.
            OutlinedText(
                text = "ENEMY",
                color = TrackerMuted,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1
            )
            OutlinedText(
                text = "Tap = used · Hold = change",
                color = TrackerMuted,
                fontSize = 6.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

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
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCoolingDown) TrackerSurfaceCooling else TrackerSurface)
                            .border(
                                1.dp,
                                if (isCoolingDown) TrackerGold.copy(alpha = 0.85f) else TrackerBorder,
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.padding(horizontal = 2.dp).zIndex(1f)
                        ) {
                            // Lane always visible. When cooling, also show the spell
                            // abbreviation so you don't lose which spell is on CD.
                            OutlinedText(
                                text = if (isCoolingDown) {
                                    "${laneSpell.lane} · ${abbreviationFor(laneSpell.spell.name)}"
                                } else {
                                    laneSpell.lane
                                },
                                color = if (isCoolingDown) TrackerGold else TrackerMuted,
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // FIX: was full name ("Retribution" ~55dp) in a ~40dp cell, so ROAM
                            // was clipped on the right in screenshot. Use abbreviation always.
                            // Full name stays in contentDescription for accessibility.
                            OutlinedText(
                                text = if (isCoolingDown) formatCooldown(remaining) else abbreviationFor(laneSpell.spell.name),
                                color = if (isCoolingDown) TrackerGold else TrackerText,
                                fontSize = if (isCoolingDown) 11.sp else 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Gold bar fills the whole cell background from empty -> full.
                        // progress = elapsed / cooldown (0 at click, 1 when ready).
                        // Timer text is drawn on top, with black outline so it stays readable.
                        if (isCoolingDown) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(TrackerGold.copy(alpha = 0.38f))
                                    .align(Alignment.CenterStart)
                                    .zIndex(0f)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = openPickerLane == laneSpell.lane,
                        modifier = Modifier.width(260.dp).heightIn(max = 200.dp),
                        onDismissRequest = { openPickerLane = null }
                    ) {
                        // Two columns: spells chunked into pairs, each pair is a Row with 2 items.
                        availableSpells.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                pair.forEach { spell ->
                                    val isSelected = spell.name == laneSpell.spell.name
                                    Box(modifier = Modifier.weight(1f)) {
                                        DropdownMenuItem(
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            text = {
                                                OutlinedText(
                                                    text = if (isSelected) "✓ ${spell.name} ${spell.cooldownSeconds}s"
                                                    else "${spell.name} ${spell.cooldownSeconds}s",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 11.sp,
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
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideMessage(message: String) {
    OutlinedText(
        text = message,
        color = GuideText,
        fontSize = 9.sp,
        modifier = Modifier
            .background(GuideBackground, RoundedCornerShape(8.dp))
            .padding(6.dp)
    )
}

@Composable
private fun OutlinedText(
    text: String,
    color: Color,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier
) {
    // Black outline on every text so it stands out over the game.
    // FIX: 1dp padding so the ±0.9dp black offsets don't clip descenders
    // (screenshot: 3rd line "jungler..." clipped by progress bar).
    Box(
        modifier = modifier.then(Modifier.padding(1.dp)),
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        val offsets = listOf(
            -0.9f to -0.9f, 0.9f to -0.9f, -0.9f to 0.9f, 0.9f to 0.9f,
            0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f
        )
        offsets.forEach { (dx, dy) ->
            androidx.compose.material3.Text(
                text = text, color = Color.Black, fontSize = fontSize, fontWeight = fontWeight,
                maxLines = maxLines, overflow = overflow, textAlign = textAlign,
                lineHeight = lineHeight, letterSpacing = letterSpacing,
                modifier = Modifier.offset(dx.dp, dy.dp)
            )
        }
        androidx.compose.material3.Text(
            text = text, color = color, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = maxLines, overflow = overflow, textAlign = textAlign,
            lineHeight = lineHeight, letterSpacing = letterSpacing
        )
    }
}


/**
 * Simple overload for menu labels that don't need a custom color.
 * REPLACES the deleted OutlinedText_SIMPLE.kt — but this one DELEGATES to the
 * full outlined version, so it DOES draw the black outline.
 * Calls that used the deleted file: OutlinedText("Show instructions"),
 * OutlinedText(plan.label, maxLines = 1, overflow = ...).
 * Without this overload those calls would not compile (full version needs `color`).
 */
@Composable
private fun OutlinedText(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    OutlinedText(
        text = text,
        color = GuideText,
        fontSize = 9.sp,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

// NOTE: Simple GuideControl(label, color, onClick) overload DELETED.
// The full GuideControl below already supports trailing-lambda calls like
// GuideControl("−10s", GuideMuted) { ... } — onClick is the last param, so the
// lambda binds to it. Keeping the simple overload caused ambiguity + shadowing.
@Composable
private fun GuideControl(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    contentDescription: String = label,
    background: Color = GuideSurface,
    borderColor: Color = GuideBorder,
    fontWeight: FontWeight = FontWeight.SemiBold,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        OutlinedText(
            text = label,
            color = color,
            fontSize = 8.sp,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GuideDetail(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedText(
            text = "$label:",
            color = GuideMuted,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        OutlinedText(
            text = text,
            color = GuideText,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
