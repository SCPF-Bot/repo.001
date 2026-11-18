package com.mlbb.assistant.presentation.overlay

// ── SINGLE SOURCE OF TRUTH ────────────────────────────────────────────────────
// This is the ONLY file that may declare Roam* / Tracker* / Guide* types.
// Do NOT keep RoamGuideWidget.kt, EnemySpellTrackerWidget.kt, or an older
// RoamOverlayWidgets.kt alongside it — Kotlin treats top-level declarations
// in the same package as redeclarations even when they are `private`.
// If you want split files again, you must delete this file first and make
// TrackedLaneSpell `internal` (not `private`) so the other file can see it.

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// --- Widget width: matched to the green line in the reference photo ---
// Measured from the photo (720px wide): green line runs x = 150 → 526,
// i.e. left = 20.8%, right = 72.9%, width = 52.1% of screen width.
// Previous value was 0.62f in landscape (too wide, blocked extra vision).
// Portrait is not the in-game case (MLBB is landscape) so it stays wide
// for readability, but landscape — the real case — is now exactly 0.52f.
private const val WIDGET_WIDTH_FRACTION_LANDSCAPE = 0.52f
private const val WIDGET_WIDTH_FRACTION_PORTRAIT = 0.92f
private const val WIDGET_MIN_WIDTH_DP = 300f
private const val ROAM_GUIDE_ASSET = "roam_guide.json"

private data class RoamStep(
    val startSeconds: Int,
    val endSeconds: Int,
    val description: String,
    val timeLabel: String,
    val phase: String,
    val callout: String,
    val checklist: List<String>,
    val vision: String,
    val decision: String,
    val dont: String
)

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
        callout = json.optString("callout").ifBlank { json.optString("objective") },
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
    lastSlainAt: Int?,
    lastSlainWasLord: Boolean = false
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

    // FIX V7: Next is Lord only if Turtle is actually done: 3 Turtles, or last
    // slain was Turtle after 6:00 (next Turtle would be after 8:00), or past 8:00.
    if (turtleSlain >= 3 || (lastSlainAt != null && !lastSlainWasLord && lastSlainAt > 6 * 60) || elapsedSeconds >= 8 * 60) {
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
@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val guide = remember(context) { loadRoamGuide(context) }
    val plans = guide.plans
    var selectedPlanId by rememberSaveable { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
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
    val objective = computeObjective(elapsedSeconds, turtleSlain, lordSlain, lastSlainAt, lastSlainWasLord)
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
        // FIX V7: slain label = LAST slain, not NEXT. At 7:26 you killed Turtle,
        // next is Lord — old said "Lord slain".
        lastSlainAt != null -> "${if (lastSlainWasLord) "Lord" else "Turtle"} slain. Next ${objective.label} in ${formatGameTime(objective.secondsUntil)}. Push, farm, or reset."
        else -> currentStep?.description.orEmpty()
    }
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    // V7 FIX: Adaptive width — SAFE version. V6 used displayMetrics (wrong dp in
    // portrait) + fillMaxHeight() (made window full-screen tall). That was the
    // tall skinny strip in your screenshot. This uses LocalConfiguration (window
    // size, not display size) and NO fillMaxHeight anywhere.
    // Base: 52% landscape (green line), 92% portrait. If description is long,
    // expand a bit to the RIGHT so it still fits in 2-3 lines, then shrink back.
    // Max: 78% landscape / 95% portrait, so it never runs off-screen.
    val baseFraction = if (isLandscape) WIDGET_WIDTH_FRACTION_LANDSCAPE else WIDGET_WIDTH_FRACTION_PORTRAIT
    val maxFraction = if (isLandscape) 0.78f else 0.95f
    val adaptiveExtra = when {
        detailsExpanded -> 0.16f
        objectiveDescription.length > 140 -> 0.20f
        objectiveDescription.length > 110 -> 0.14f
        objectiveDescription.length > 85 -> 0.08f
        objectiveDescription.length > 65 -> 0.04f
        else -> 0f
    }
    val widthFraction = (baseFraction + adaptiveExtra).coerceAtMost(maxFraction)
    val widgetWidth = (screenWidthDp * widthFraction)
        .coerceAtLeast(WIDGET_MIN_WIDTH_DP)
        .coerceAtMost((screenWidthDp - 8).toFloat())
    // V7 FIX: Step progress for timer background. Fixed value, no fillMaxHeight.
    val stepProgress = if (currentStep != null) {
        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
        ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
    } else 0f
    val blessing = guide.blessings.firstOrNull {
        it.id == selectedPlan?.blessingId ||
            it.name.equals(selectedPlan?.blessing?.substringBefore('/')?.trim(), ignoreCase = true)
    }

    // Gap between the two widgets = exactly 1 physical pixel.
    // 1.dp is NOT 1px (at 3x density 1.dp = 3px), so convert 1px -> dp.
    val onePx = with(LocalDensity.current) { 1.toDp() }
    Column(
        modifier = Modifier
            .width(widgetWidth.dp),
        // No outer horizontal padding: the card itself is widgetWidth,
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
                // V7 FIX (2): Play/pause icons REMOVED, progress as timer background.
                // V6 bug: progress Box used fillMaxHeight() in a WRAP_CONTENT window —
                // that made the whole window go full-screen tall (your tall strip).
                // This uses FIXED 24.dp height everywhere, no fillMaxHeight / fillMaxSize.
                // Blue fill = step progress, 35% alpha, text on top. Tap = start/pause.
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GuideSurface)
                        .border(1.dp, GuideBorder, RoundedCornerShape(8.dp))
                        .clickable { isRunning = !isRunning && elapsedSeconds < 18 * 60 }
                        .semantics { contentDescription = "Start or pause timer" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stepProgress)
                            .height(24.dp)
                            .background(GuideAccent.copy(alpha = 0.35f))
                            .align(Alignment.CenterStart)
                    )
                    OutlinedText(
                        text = formatGameTime(elapsedSeconds),
                        color = if (isRunning) GuideGreen else GuideText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
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
                GuideControl(
                    objectiveText,
                    objectiveColor,
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
                GuideControl(if (detailsExpanded) "−" else "+", GuideMuted) {
                    detailsExpanded = !detailsExpanded
                }
                // V7: ? help button between + / − and ×. Holds "Tap = used · Hold = change" + other tips.
                Box {
                    GuideControl("?", GuideMuted, onClick = { helpExpanded = true }, contentDescription = "Help and tips")
                    DropdownMenu(
                        expanded = helpExpanded,
                        onDismissRequest = { helpExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { OutlinedText("Timer: Tap timer to start / pause", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Spell: Tap = used · Hold = change", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Spell: Tap again to cancel cooldown", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Objective: Tap Turtle / Lord when slain", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Plan: Tap plan name to switch plan", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Expand: Tap + for checklist / vision", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                    }
                }
                GuideControl("×", GuideMuted, onClick = onBack, contentDescription = "Close roam guide")
            }

            if (!descriptionHidden) {
                // V7 FIX (3): Call-out REMOVED. Show FULL description directly.
                // Old: "Setup · Guard buff, don't feed — Buy Roam boots..."
                // Now: full turtle/lordContest or step description, e.g.
                // "Go Lord pit early, zone front. Save AoE Ultimate for Lord fight..."
                // V6 tried first-sentence-only + displayMetrics width — that cut the
                // key "Save AoE Ultimate..." part. V7 keeps full text + adaptive width.
                if (objectiveDescription.isNotBlank()) {
                    val descFontSize = when {
                        !detailsExpanded && objectiveDescription.length > 140 -> 7.5.sp
                        !detailsExpanded && objectiveDescription.length > 110 -> 8.sp
                        !detailsExpanded && objectiveDescription.length > 85 -> 8.5.sp
                        else -> 9.sp
                    }
                    val descLineHeight = when {
                        !detailsExpanded && objectiveDescription.length > 140 -> 10.sp
                        !detailsExpanded && objectiveDescription.length > 110 -> 11.sp
                        !detailsExpanded && objectiveDescription.length > 85 -> 11.sp
                        else -> 12.sp
                    }
                    OutlinedText(
                        text = objectiveDescription,
                        color = GuideText,
                        fontSize = descFontSize,
                        lineHeight = descLineHeight,
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
                }
                // V7 FIX (2): Separate 4dp progress bar DELETED — now timer background.
                // No extra bar below description.
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

// ── Enemy Spell Tracker ─────────────────────────────────────────────────────
// V7-FIXED: This section lives in the SAME file as RoamGuideWidget.
// That is the fix for the build error:
//   - Roam* types were declared in BOTH RoamGuideWidget.kt and RoamOverlayWidgets.kt
//     → "redeclared" error. Now they are declared ONCE, in this file only.
//   - TrackedLaneSpell was `private` in EnemySpellTrackerWidget.kt but used in
//     RoamOverlayWidgets.kt:871 → "private to a different file" error.
//     Now TrackedLaneSpell is defined in THIS file, in the same file that uses it,
//     so `private` is legal. Do NOT move it back to a separate file without
//     changing it to `internal`.
// DELETE: RoamGuideWidget.kt, EnemySpellTrackerWidget.kt, RoamOverlayWidgets.kt
//         (and any RoamOverlayWidgets_V*.kt). Keep ONLY this file + JetOverlay.kt.

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
    Box(
        modifier = modifier,
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
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        OutlinedText(
            text = text,
            color = GuideText,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
