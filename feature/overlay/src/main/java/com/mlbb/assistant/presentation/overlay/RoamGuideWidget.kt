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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

// FIX - 12:12 screenshot: description vs To do bullet was still different.
// ROOT CAUSE: MergedInstructionText drew its outline in the text's OWN color (yellow/white stroke),
// while To do bullets use OutlinedText which draws outline in BLACK.
// Result: merged description looked yellow, thick, bigger, with white halo. Bullets looked white, thin, small.
// FIX: MergedInstructionText now uses EXACT same rendering as OutlinedText:
//   Layer 1 = all-black AnnotatedString with Stroke(3f) = black outline
//   Layer 2 = colored AnnotatedString (yellow callout + white description) = fill
// And EXACT same sizes: callout = To do heading, description = To do bullet.
//   Callout / To do heading: GithubGold, 9sp, Bold, 11sp lineHeight
//   Description / Bullet: GithubText, 9sp, Normal, 11sp lineHeight
// No other difference. If you still see a difference, you are on an old .kt - replace whole file.

private data class RoamHero(val name: String, val archetype: String = "", val note: String = "")
private data class RoamBlessing(val id: String, val name: String, val type: String = "", val effect: String = "", val cooldown: String = "", val unlock: String = "")
private data class RoamStep(val startSeconds: Int, val endSeconds: Int, val description: String, val timeLabel: String = "", val phase: String = "", val callout: String = "", val checklist: List<String> = emptyList(), val vision: String = "", val decision: String = "", val dont: String = "")
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
    val merged = obj.optString("instruction").ifBlank { obj.optString("description") }.ifBlank {
        val o = obj.optString("objective")
        val d = obj.optString("description")
        if (o.isNotBlank() && d.isNotBlank()) "$o. $d" else o + d
    }
    return RoamStep(
        startSeconds = obj.optInt("startSeconds"),
        endSeconds = obj.optInt("endSeconds"),
        description = merged,
        timeLabel = obj.optString("timeLabel"),
        phase = obj.optString("phase"),
        callout = obj.optString("callout").ifBlank { obj.optString("objective") },
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

private fun formatTime(s: Int): String {
    val v = s.coerceAtLeast(0)
    return "%02d:%02d".format(v / 60, v % 60)
}

@Composable
private fun OutlinedText(text: String, color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 9.sp, fontWeight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, lineHeight: TextUnit = 11.sp, overflow: TextOverflow = TextOverflow.Visible) {
    Box(modifier = modifier) {
        Text(text = text, color = Color.Black, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, drawStyle = Stroke(width = 3f), shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
        Text(text = text, color = color, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
    }
}

// SHARED STYLE - single source of truth so description and bullet can never drift again.
// Callout / To do heading: GithubGold, 9sp, Bold, 11sp
// Description / Bullet: GithubText, 9sp, Normal, 11sp
@Composable
private fun MergedInstructionText(phase: String, callout: String, description: String, maxLines: Int = Int.MAX_VALUE) {
    // Colored layer - what you actually see
    val colored: AnnotatedString = buildAnnotatedString {
        if (phase.isNotBlank()) {
            withStyle(style = SpanStyle(color = GithubMuted, fontWeight = FontWeight.Bold, fontSize = 8.sp)) {
                append(phase)
                append(" - ")
            }
        }
        withStyle(style = SpanStyle(color = GithubGold, fontWeight = FontWeight.Bold, fontSize = 9.sp)) {
            append(callout)
        }
        if (description.isNotBlank()) {
            withStyle(style = SpanStyle(color = GithubMuted, fontSize = 9.sp)) {
                append(" - ")
            }
            // MUST match To do bullet exactly: GithubText, 9sp, Normal
            withStyle(style = SpanStyle(color = GithubText, fontSize = 9.sp)) {
                append(description)
            }
        }
    }
    // Black outline layer - SAME text, ALL black, SAME sizes/weights. This is what OutlinedText does.
    // Previous bug: outline was drawn in yellow/white, so description looked yellow and thick.
    val blackOutline: AnnotatedString = buildAnnotatedString {
        if (phase.isNotBlank()) {
            withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 8.sp)) {
                append(phase)
                append(" - ")
            }
        }
        withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.sp)) {
            append(callout)
        }
        if (description.isNotBlank()) {
            withStyle(style = SpanStyle(color = Color.Black, fontSize = 9.sp)) {
                append(" - ")
            }
            withStyle(style = SpanStyle(color = Color.Black, fontSize = 9.sp)) {
                append(description)
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Text(text = blackOutline, maxLines = maxLines, overflow = TextOverflow.Visible, style = TextStyle(lineHeight = 11.sp, drawStyle = Stroke(width = 3f), shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
        Text(text = colored, maxLines = maxLines, overflow = TextOverflow.Visible, style = TextStyle(lineHeight = 11.sp, shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
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
    val widgetWidth = if (isLandscape) (config.screenWidthDp * 0.72f).dp else (config.screenWidthDp * 0.98f).dp
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
    val blessingDetail = guide.blessings.firstOrNull { it.id == selectedPlan?.blessingId || it.name.equals(selectedPlan?.blessing?.split("/")?.firstOrNull()?.trim(), ignoreCase = true) }

    Column(modifier = Modifier.width(widgetWidth).wrapContentHeight().background(GithubCanvas.copy(alpha = WIDGET_ALPHA), RoundedCornerShape(6.dp)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
        if (plans.isEmpty() || selectedPlan == null || currentStep == null) {
            OutlinedText("Roam guide unavailable", GithubText, fontSize = 9.sp)
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).clickable { isRunning = !isRunning }.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedText(formatTime(elapsedSeconds), GithubText, fontSize = 14.sp, fontWeight = FontWeight.Black)
                OutlinedText(if (isRunning) "II" else "▶", GithubAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).clickable { planMenuExpanded = true }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedText(selectedPlan.label, GithubAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedText("▾", GithubMuted, fontSize = 10.sp)
                }
                DropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }, modifier = Modifier.background(GithubSurface)) {
                    plans.forEach { plan ->
                        DropdownMenuItem(text = { OutlinedText(plan.label, if (plan.id == selectedPlan.id) GithubAccent else GithubText, fontSize = 10.sp) }, onClick = { selectedPlanId = plan.id; planMenuExpanded = false }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            OutlinedText(if (isExpanded) "▲" else "▼", GithubMuted, modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            OutlinedText("X", GithubMuted, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (!isExpanded) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable { isExpanded = true }.padding(horizontal = 2.dp, vertical = 2.dp)) {
                MergedInstructionText(phase = currentStep.phase, callout = currentStep.callout, description = currentStep.description, maxLines = Int.MAX_VALUE)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 2.dp, vertical = 2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentStep.phase.isNotBlank()) OutlinedText(currentStep.phase, GithubAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    OutlinedText(currentStep.timeLabel.ifBlank { formatTime(currentStep.startSeconds) + "-" + formatTime(currentStep.endSeconds) }, GithubMuted, fontSize = 8.sp)
                    OutlinedText("Ends ${formatTime((currentStep.endSeconds - elapsedSeconds).coerceAtLeast(0))}", GithubMuted, fontSize = 8.sp)
                    if (guide.meta.patch.isNotBlank()) OutlinedText("S42 ${guide.meta.patch}", GithubMuted, fontSize = 8.sp)
                }
                MergedInstructionText(phase = "", callout = currentStep.callout, description = currentStep.description, maxLines = Int.MAX_VALUE)
                if (currentStep.checklist.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    OutlinedText("To do:", GithubGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                    currentStep.checklist.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedText("•", GithubGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                            OutlinedText(item, GithubText, fontSize = 9.sp, lineHeight = 11.sp)
                        }
                    }
                }
                if (currentStep.vision.isNotBlank()) OutlinedText("Vision: ${currentStep.vision}", GithubMuted, fontSize = 9.sp, lineHeight = 11.sp)
                if (currentStep.decision.isNotBlank()) OutlinedText("Do: ${currentStep.decision}", GithubGreen, fontSize = 9.sp, lineHeight = 11.sp)
                if (currentStep.dont.isNotBlank()) OutlinedText("Don't: ${currentStep.dont}", GithubRed, fontSize = 9.sp, lineHeight = 11.sp)
                if (blessingDetail != null) {
                    Spacer(Modifier.height(2.dp))
                    OutlinedText("${selectedPlan.blessing} - ${blessingDetail.effect}", GithubGold, fontSize = 9.sp, lineHeight = 11.sp, maxLines = Int.MAX_VALUE)
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedText("-10s", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable { elapsedSeconds = (elapsedSeconds - 10).coerceIn(0, 18 * 60) }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("+10s", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable { elapsedSeconds = (elapsedSeconds + 10).coerceIn(0, 18 * 60) }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("Reset", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable { elapsedSeconds = 0; isRunning = false }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("Back", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (elapsedSeconds == 0 && selectedPlan.heroes.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    OutlinedText("Pick: ${selectedPlan.heroes.take(6).joinToString(", ") { it.name }}", GithubMuted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = Int.MAX_VALUE)
                    if (selectedPlan.emblem.isNotBlank()) OutlinedText("Emblem: ${selectedPlan.emblem}", GithubGold, fontSize = 9.sp, lineHeight = 11.sp, maxLines = Int.MAX_VALUE)
                }
            }
        }

        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
        val progress = ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(GithubBorder.copy(alpha = 0.5f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp).background(GithubAccent))
        }
    }
}
