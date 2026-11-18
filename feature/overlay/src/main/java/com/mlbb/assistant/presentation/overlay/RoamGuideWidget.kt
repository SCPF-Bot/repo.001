package com.mlbb.assistant.presentation.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val GithubCanvas = Color(0xFF0D1117)
private val GithubSurface = Color(0xFF161B22)
private val GithubBorder = Color(0xFF30363D)
private val GithubAccent = Color(0xFF58A6FF)
private val GithubText = Color(0xFFC9D1D9)
private val GithubMuted = Color(0xFF8B949E)
private val GithubGold = Color(0xFFE3B341)
private val GithubGreen = Color(0xFF3FB950)
private val GithubRed = Color(0xFFF85149)
private const val ROAM_GUIDE_ASSET = "roam_guide.json"
private const val WIDGET_ALPHA = 0.25f

// COMPACT DESIGN NOTES
// Goal: guide roam users in landscape without blocking the fight.
// Collapsed = 2 rows only, ~46dp tall. Expanded = full detail on demand.
// Heroes are pre-game only, so they are hidden in live view.
// Blessing detail, checklist, vision/decision/dont, and -10/+10/Reset live only in expanded.
// Width: portrait 98%, landscape 62% so the widget hugs the top-left and leaves the fight visible.

private data class RoamHero(val name: String, val archetype: String = "", val note: String = "")
private data class RoamBlessing(val id: String, val name: String, val type: String = "", val effect: String = "", val cooldown: String = "", val unlock: String = "")
private data class RoamStep(val startSeconds: Int, val endSeconds: Int, val description: String, val timeLabel: String = "", val phase: String = "", val objective: String = "", val callout: String = "", val checklist: List<String> = emptyList(), val vision: String = "", val decision: String = "", val dont: String = "")
private data class RoamPlan(val id: String, val label: String, val blessing: String, val blessingId: String = "", val playstyle: String = "", val heroes: List<RoamHero> = emptyList(), val battleSpells: List<String> = emptyList(), val emblem: String = "", val winCondition: String = "", val steps: List<RoamStep> = emptyList())
private data class RoamMeta(val patch: String = "", val season: String = "")
private data class RoamGuideData(val meta: RoamMeta = RoamMeta(), val blessings: List<RoamBlessing> = emptyList(), val plans: List<RoamPlan> = emptyList())

private fun JSONObject.optStringList(key: String): List<String> {
    if (!has(key)) return emptyList()
    val arr = optJSONArray(key) ?: return emptyList()
    return buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
}

private fun parseHeroes(obj: JSONObject): List<RoamHero> {
    obj.optJSONArray("heroes")?.let { arr ->
        return buildList {
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                add(RoamHero(name = h.optString("name"), archetype = h.optString("archetype"), note = h.optString("note")))
            }
        }
    }
    if (obj.has("heroes")) {
        val raw = obj.optString("heroes")
        if (raw.isNotBlank()) return raw.split(",").mapNotNull { val n = it.trim(); if (n.isEmpty()) null else RoamHero(name = n) }
    }
    return emptyList()
}

private fun parseStep(obj: JSONObject): RoamStep {
    val callout = obj.optString("callout").ifBlank { obj.optString("objective") }
    return RoamStep(
        startSeconds = obj.optInt("startSeconds"),
        endSeconds = obj.optInt("endSeconds"),
        description = obj.optString("description"),
        timeLabel = obj.optString("timeLabel"),
        phase = obj.optString("phase"),
        objective = obj.optString("objective"),
        callout = callout,
        checklist = obj.optStringList("checklist"),
        vision = obj.optString("vision"),
        decision = obj.optString("decision"),
        dont = obj.optString("dont")
    )
}

private fun parsePlan(obj: JSONObject): RoamPlan {
    val stepsJson = obj.optJSONArray("steps") ?: JSONArray()
    val steps = buildList {
        for (i in 0 until stepsJson.length()) {
            val s = stepsJson.optJSONObject(i) ?: continue
            add(parseStep(s))
        }
    }.sortedBy { it.startSeconds }
    return RoamPlan(
        id = obj.optString("id"),
        label = obj.optString("label"),
        blessing = obj.optString("blessing"),
        blessingId = obj.optString("blessing_id"),
        playstyle = obj.optString("playstyle"),
        heroes = parseHeroes(obj),
        battleSpells = obj.optStringList("battle_spells"),
        emblem = obj.optString("emblem"),
        winCondition = obj.optString("win_condition"),
        steps = steps
    )
}

private fun loadRoamGuide(context: Context): RoamGuideData = runCatching {
    val json = context.assets.open(ROAM_GUIDE_ASSET).bufferedReader().use { it.readText() }.trim()
    if (json.startsWith("{")) {
        val root = JSONObject(json)
        val metaObj = root.optJSONObject("meta")
        val meta = RoamMeta(patch = metaObj?.optString("patch").orEmpty(), season = metaObj?.optString("season").orEmpty())
        val blessingsArr = root.optJSONArray("blessings") ?: JSONArray()
        val blessings = buildList {
            for (i in 0 until blessingsArr.length()) {
                val b = blessingsArr.optJSONObject(i) ?: continue
                add(RoamBlessing(id = b.optString("id"), name = b.optString("name"), type = b.optString("type"), effect = b.optString("effect"), cooldown = b.optString("cooldown")))
            }
        }
        val rolesArr = root.optJSONArray("roles") ?: JSONArray()
        val plans = buildList {
            for (i in 0 until rolesArr.length()) {
                val p = rolesArr.optJSONObject(i) ?: continue
                val plan = parsePlan(p)
                if (plan.steps.isNotEmpty()) add(plan)
            }
        }
        RoamGuideData(meta = meta, blessings = blessings, plans = plans)
    } else {
        val arr = JSONArray(json)
        val plans = buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val plan = parsePlan(p)
                if (plan.steps.isNotEmpty()) add(plan)
            }
        }
        RoamGuideData(plans = plans)
    }
}.getOrDefault(RoamGuideData())

private data class NextObjective(val label: String, val secondsUntil: Int, val isLive: Boolean)

private fun nextObjective(elapsed: Int): NextObjective {
    val spawns = listOf(120 to "Turtle", 240 to "Turtle 2", 360 to "Turtle 3", 480 to "Lord", 660 to "Lord 2", 840 to "Lord 3", 1080 to "Evolved Lord")
    val next = spawns.firstOrNull { it.first > elapsed } ?: return NextObjective("Push", 0, true)
    val until = next.first - elapsed
    return NextObjective(next.second, until, until <= 30)
}

private fun formatTime(s: Int): String {
    val v = s.coerceAtLeast(0)
    return "%02d:%02d".format(v / 60, v % 60)
}

@Composable
private fun OutlinedText(text: String, color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 9.sp, fontWeight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip) {
    Box(modifier = modifier) {
        Text(text = text, color = Color.Black, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, drawStyle = Stroke(width = 3f), shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
        Text(text = text, color = color, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
    }
}

@Composable
fun RoamGuideBubble(onTap: () -> Unit) {
    Box(modifier = Modifier.width(56.dp).height(56.dp).clip(CircleShape).background(GithubSurface).border(2.dp, GithubAccent, CircleShape).clickable(onClick = onTap), contentAlignment = Alignment.Center) {
        OutlinedText("R", GithubAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp
    // Landscape: narrow so fight stays visible. Portrait: full width like before.
    val widgetWidth = if (isLandscape) (config.screenWidthDp * 0.62f).dp else (config.screenWidthDp * 0.98f).dp
    val guide = remember(context) { loadRoamGuide(context) }
    val plans = guide.plans
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1 else isRunning = false
        }
    }

    val currentStep = selectedPlan?.steps?.firstOrNull { elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds } ?: selectedPlan?.steps?.lastOrNull()
    val nextObj = nextObjective(elapsedSeconds)
    val blessingDetail = guide.blessings.firstOrNull { it.id == selectedPlan?.blessingId || it.name.equals(selectedPlan?.blessing?.split("/")?.firstOrNull()?.trim(), ignoreCase = true) }

    Column(modifier = Modifier.width(widgetWidth).background(GithubCanvas.copy(alpha = WIDGET_ALPHA), RoundedCornerShape(6.dp)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).padding(horizontal = 3.dp, vertical = 1.dp)) {
        if (plans.isEmpty() || selectedPlan == null || currentStep == null) {
            OutlinedText("Roam guide unavailable", GithubText, fontSize = 9.sp)
            return@Column
        }

        // ROW 1 - controls, single line, no wrap
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            // Timer = play/pause. Long-press feel: tap expands to show Reset/-10/+10, so main row stays clean.
            Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).clickable { isRunning = !isRunning }.padding(horizontal = 4.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedText(formatTime(elapsedSeconds), GithubText, fontSize = 12.sp, fontWeight = FontWeight.Black)
                OutlinedText(if (isRunning) "II" else "▶", GithubAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            // Plan selector - weight 1 so it truncates, not pushes others off
            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).clickable { planMenuExpanded = true }.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedText(selectedPlan.label, GithubAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedText("▾", GithubMuted, fontSize = 8.sp)
                }
                DropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }, modifier = Modifier.background(GithubSurface)) {
                    plans.forEach { plan ->
                        DropdownMenuItem(text = { OutlinedText(plan.label, if (plan.id == selectedPlan.id) GithubAccent else GithubText, fontSize = 9.sp) }, onClick = { selectedPlanId = plan.id; planMenuExpanded = false }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            // Next objective - tiny, only the countdown matters in game
            val objColor = if (nextObj.isLive) GithubRed else GithubGold
            val objText = if (nextObj.isLive) "${nextObj.label} NOW" else "${nextObj.label} ${formatTime(nextObj.secondsUntil)}"
            OutlinedText(objText, objColor, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            // Expand toggle - one tap for full detail, otherwise stays 2 rows
            OutlinedText(if (isExpanded) "▲" else "▼", GithubMuted, modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(horizontal = 2.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            OutlinedText("X", GithubMuted, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 2.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }

        // ROW 2 - COLLAPSED: callout only, 1 line. This is the whole point of compact mode.
        // EXPANDED: callout + full description + checklist + vision/decision/dont + controls
        if (!isExpanded) {
            Row(modifier = Modifier.fillMaxWidth().clickable { isExpanded = true }.padding(horizontal = 2.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (currentStep.phase.isNotBlank()) OutlinedText(currentStep.phase, GithubAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                OutlinedText(currentStep.callout.ifBlank { currentStep.objective }.ifBlank { currentStep.description }, GithubText, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentStep.phase.isNotBlank()) OutlinedText(currentStep.phase, GithubAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    OutlinedText(currentStep.timeLabel.ifBlank { formatTime(currentStep.startSeconds) + "-" + formatTime(currentStep.endSeconds) }, GithubMuted, fontSize = 7.sp)
                    OutlinedText("Ends ${formatTime((currentStep.endSeconds - elapsedSeconds).coerceAtLeast(0))}", GithubMuted, fontSize = 7.sp)
                    if (guide.meta.patch.isNotBlank()) OutlinedText("S42 ${guide.meta.patch}", GithubMuted, fontSize = 7.sp)
                }
                OutlinedText(currentStep.callout.ifBlank { currentStep.objective }, GithubAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                OutlinedText(currentStep.description, GithubText, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 3)
                if (currentStep.checklist.isNotEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    currentStep.checklist.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            OutlinedText("•", GithubGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            OutlinedText(item, GithubText, fontSize = 7.sp, lineHeight = 9.sp)
                        }
                    }
                }
                if (currentStep.vision.isNotBlank()) OutlinedText("Vision: ${currentStep.vision}", GithubMuted, fontSize = 7.sp, lineHeight = 9.sp)
                if (currentStep.decision.isNotBlank()) OutlinedText("Do: ${currentStep.decision}", GithubGreen, fontSize = 7.sp, lineHeight = 9.sp)
                if (currentStep.dont.isNotBlank()) OutlinedText("Don't: ${currentStep.dont}", GithubRed, fontSize = 7.sp, lineHeight = 9.sp)
                // Blessing detail only in expanded - tapping blessing in collapsed does nothing, keeps it clean
                if (blessingDetail != null) {
                    Spacer(Modifier.height(1.dp))
                    OutlinedText("${selectedPlan.blessing} - ${blessingDetail.effect}", GithubGold, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 2)
                }
                // Timer sync controls only in expanded - keeps collapsed row from growing
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedText("-10s", GithubMuted, modifier = Modifier.clickable { elapsedSeconds = (elapsedSeconds - 10).coerceIn(0, 18 * 60) }.padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("+10s", GithubMuted, modifier = Modifier.clickable { elapsedSeconds = (elapsedSeconds + 10).coerceIn(0, 18 * 60) }.padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("Reset", GithubMuted, modifier = Modifier.clickable { elapsedSeconds = 0; isRunning = false }.padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("Back", GithubMuted, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                // Heroes ONLY pre-game (timer at 0) and only when expanded - they are useless mid-fight and cost height
                if (elapsedSeconds == 0 && selectedPlan.heroes.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    OutlinedText("Pick: ${selectedPlan.heroes.take(6).joinToString(", ") { it.name }}", GithubMuted, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 2)
                    if (selectedPlan.emblem.isNotBlank()) OutlinedText("Emblem: ${selectedPlan.emblem}", GithubGold, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 2)
                }
            }
        }

        // Progress - 2dp line, no extra row, no extra height
        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
        val progress = ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(GithubBorder.copy(alpha = 0.5f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(2.dp).background(GithubAccent))
        }
    }
}
