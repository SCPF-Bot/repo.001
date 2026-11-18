package com.mlbb.assistant.presentation.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val GuideBackground = Color(0xE60D1117)
private val GuideSurface = Color(0xCC161B22)
private val GuideBorder = Color(0xFF30363D)
private val GuideAccent = Color(0xFF58A6FF)
private val GuideText = Color(0xFFC9D1D9)
private val GuideMuted = Color(0xFF8B949E)
private val GuideGold = Color(0xFFE3B341)
private val GuideGreen = Color(0xFF3FB950)
private val GuideRed = Color(0xFFF85149)
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
 */
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
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val widthFraction = if (isLandscape) 0.62f else 0.98f
    val widgetWidth = (screenWidthDp * widthFraction).coerceAtLeast(280f).coerceAtMost(screenWidthDp.toFloat())
    val blessing = guide.blessings.firstOrNull {
        it.id == selectedPlan?.blessingId ||
            it.name.equals(selectedPlan?.blessing?.substringBefore('/')?.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .width(widgetWidth.dp)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuideControl(
                    label = formatGameTime(elapsedSeconds),
                    color = GuideText,
                    modifier = Modifier.width(58.dp),
                    onClick = {}
                )
                GuideControl(if (isRunning) "Ⅱ" else "▶", GuideAccent) {
                    isRunning = !isRunning && elapsedSeconds < 18 * 60
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
                                    Text(
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
                GuideControl("×", GuideMuted, onClick = onBack, contentDescription = "Close roam guide")
            }

            if (!descriptionHidden) {
                Text(
                    text = buildString {
                        if (currentStep.phase.isNotBlank()) append("${currentStep.phase} · ")
                        append(currentStep.callout.ifBlank { objectiveText })
                        if (objectiveDescription.isNotBlank()) append(" — $objectiveDescription")
                    },
                    color = GuideText,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = if (detailsExpanded) 8 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (detailsExpanded) {
                    Text(
                        "${currentStep.timeLabel.ifBlank { "${formatGameTime(currentStep.startSeconds)}–${formatGameTime(currentStep.endSeconds)}" }}${guide.patch.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                        color = GuideMuted,
                        fontSize = 9.sp
                    )
                    currentStep.checklist.forEach { item ->
                        Text("• $item", color = GuideText, fontSize = 9.sp)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                    text = { Text("Show instructions") },
                                    onClick = { descriptionHidden = false; hideMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hide instructions") },
                                    onClick = { descriptionHidden = true; hideMenuExpanded = false }
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(GuideBorder, RoundedCornerShape(1.dp))
                    ) {
                        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
                        val progress = ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(2.dp)
                                .background(GuideAccent, RoundedCornerShape(1.dp))
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
private fun GuideMessage(message: String) {
    Text(
        text = message,
        color = GuideText,
        fontSize = 10.sp,
        modifier = Modifier
            .background(GuideBackground, RoundedCornerShape(6.dp))
            .padding(8.dp)
    )
}

@Composable
private fun GuideControl(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    onClick: () -> Unit,
    contentDescription: String = label
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(GuideSurface)
            .border(1.dp, GuideBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GuideDetail(label: String, text: String) {
    Text(
        text = "$label: $text",
        color = GuideText,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}
