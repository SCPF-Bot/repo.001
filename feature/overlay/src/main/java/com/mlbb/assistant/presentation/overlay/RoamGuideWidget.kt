package com.mlbb.assistant.presentation.overlay

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
// OPACITY - per request 2026-11-18 (see screenshot):
// Main widget area (objective, game timer, Turtle/Lord pill, roam type, description) = 45% opaque (55% see-through) - changed 65% -> 45% per request
// Spell tracker buttons = 15% opaque (85% transparent) - stays faint so it never blocks fight view
// Pickers (roam type / spell) are temporary popups = 95% opaque so text stays readable over game
private const val WIDGET_ALPHA = 0.45f // 45% opaque - main widget (was 65%)
private const val MAIN_ALPHA = 0.45f // 45% - Area 1 only, Area 2 stays 15%
private const val PICKER_ALPHA = 0.95f
private const val ENEMY_SPELL_ALPHA = 0.15f // 15% opaque - spell tracker only

// ENEMY BATTLE SPELL TRACKER - v3.2 - Season 42, Patch 2.2.16, Liquipedia ledger
// Tap = enemy used spell -> start CD bar (empty -> full). Tap again on CD = cancel (fix mis-tap).
// Long-press = switch spell (e.g. Flicker -> Revitalize). Defaults: JG = Retribution, EXP/MID/GOLD/ROAM = Flicker.
private data class BattleSpell(val name: String, val cooldown: Int)
private val BATTLE_SPELLS = listOf(
    BattleSpell("Flicker", 120),
    BattleSpell("Retribution", 35),
    BattleSpell("Purify", 90),
    BattleSpell("Sprint", 100),
    BattleSpell("Execute", 90),
    BattleSpell("Petrify", 75),
    BattleSpell("Inspire", 75),
    BattleSpell("Aegis", 75),
    BattleSpell("Vengeance", 75),
    BattleSpell("Flameshot", 50),
    BattleSpell("Arrival", 75),
    BattleSpell("Revitalize", 75)
)
private data class EnemySpellTracker(val lane: String, val spell: BattleSpell, val usedAt: Int?)
private fun defaultEnemySpells(): List<EnemySpellTracker> {
    val flicker = BATTLE_SPELLS.first { it.name == "Flicker" }
    val retri = BATTLE_SPELLS.first { it.name == "Retribution" }
    return listOf(
        EnemySpellTracker("EXP", flicker, null),
        EnemySpellTracker("JG", retri, null),
        EnemySpellTracker("MID", flicker, null),
        EnemySpellTracker("GOLD", flicker, null),
        EnemySpellTracker("ROAM", flicker, null)
    )
}

// DYNAMIC TURTLE / LORD + VERIFIED IN-GAME ROAM TYPES - v3.2 - ENEMY SPELL TRACKER: 5 bottom buttons EXP/JG/MID/GOLD/ROAM, 85% transparent, tap=CD bar, long-press=switch spell
// Roam types are now the verified in-game Hero Specialty tags, not guide archetypes:
// Crowd Control (AoE, controls many), Initiator (starts fight, comeback), Charge (dash fast in),
// Control (guaranteed single lock), Support/Guard (save allies), Heal/Regen (regenerate HP).
// Old mapping: Engage->Crowd Control, Pickoff->Control, Healer->Heal, Utility->Support, Damage->Charge, + NEW Initiator.
// Widget loads roles dynamically from roam_guide.json, so no code change needed for new types - just JSON.
// Verified timers - Season 42, patch 2.2.16a:
// Turtle: first 2:00 exactly, respawn 120s after slain, max 3 per game.
//   No new Turtle if last Turtle slain after 6:00 (360s).
//   If not slain by 8:00 (480s) and no damage for 5s, Turtle turns into Lord after 5s, delayed up to 9:00.
//   Rewards: 60/70/80 gold per hero for 1st/2nd/3rd + EXP + 120s shield. Last-hit: heal + self-repair shield.
// Lord: first Lord is NOT fixed - exactly 120s after last Turtle slain. Ex: Turtle slain 7:45 -> Lord 9:45.
//   If you never tap, widget falls back to 8:00.
//   After Lord slain 8:00-18:00: respawn 180s. After 18:00: respawn 120s. Evolves at 18:00.
// Pill: [00:00 | Plan | Turtle 2 in 01:23 | expand | X] when countdown
//       [00:00 | Plan | Turtle 2 | expand | X] when alive - TAP pill when slain
// Tap pill when ALIVE = mark slain, start countdown, description switches to post-objective.
// When ALIVE, description is dynamic: CONTEST vs TRADE - go Turtle/Lord or rotate elsewhere.

private data class RoamHero(val name: String, val archetype: String = "", val note: String = "")
private data class RoamBlessing(val id: String, val name: String, val type: String = "", val effect: String = "", val cooldown: String = "", val unlock: String = "")
private data class RoamStep(val startSeconds: Int, val endSeconds: Int, val description: String, val timeLabel: String = "", val phase: String = "", val callout: String = "", val checklist: List<String> = emptyList(), val vision: String = "", val decision: String = "", val dont: String = "")
private data class RoamPlan(val id: String, val label: String, val blessing: String, val blessingId: String = "", val playstyle: String = "", val heroes: List<RoamHero> = emptyList(), val battleSpells: List<String> = emptyList(), val emblem: String = "", val winCondition: String = "", val steps: List<RoamStep> = emptyList(), val turtleContest: String = "", val turtleTrade: String = "", val lordContest: String = "", val lordTrade: String = "")
private data class RoamMeta(val patch: String = "", val season: String = "")
private data class RoamGuideData(val meta: RoamMeta = RoamMeta(), val blessings: List<RoamBlessing> = emptyList(), val plans: List<RoamPlan> = emptyList())

private enum class ObjectiveType { TURTLE, LORD }
private data class ObjectiveStatus(
    val type: ObjectiveType,
    val count: Int,
    val isAlive: Boolean,
    val secondsUntil: Int,
    val label: String,
    val nextSpawnSeconds: Int
)

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
    val tmpl = obj.optJSONObject("objective_templates")
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
        steps = steps,
        turtleContest = tmpl?.optString("turtle_contest").orEmpty(),
        turtleTrade = tmpl?.optString("turtle_trade").orEmpty(),
        lordContest = tmpl?.optString("lord_contest").orEmpty(),
        lordTrade = tmpl?.optString("lord_trade").orEmpty()
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

// CORE - dynamic objective state machine. Pure function, easy to test.
// BUG FIX 2026-11-18: Lord slain early (e.g. 08:11 with only 1 Turtle) showed Lord in 01:39 instead of 02:39.
// Cause: the elapsed>=480 branch treated ANY lastSlainAt as a Turtle slain and used +120s (Turtle->Lord delay).
// A Lord slain must use Lord respawn: 180s before 18:00, 120s after 18:00. Fix: check lordSlain>0 FIRST, before any Turtle logic.
private fun computeObjective(elapsed: Int, turtleSlain: Int, lordSlain: Int, lastSlainAt: Int?): ObjectiveStatus {
    // LORD ALREADY SLAIN - must come first, regardless of how many Turtles were slain.
    // Example: Turtle 1 + Lord 1, Lord slain at 08:11 (491s), now 08:32 (512s) -> next Lord 11:11 (671s), in 02:39. NOT 10:11 / 01:39.
    if (lordSlain > 0) {
        val respawn = if ((lastSlainAt ?: 0) < 1080) 180 else 120
        val next = (lastSlainAt ?: elapsed) + respawn
        if (elapsed < next) return ObjectiveStatus(ObjectiveType.LORD, lordSlain + 1, false, next - elapsed, "Lord", next)
        return ObjectiveStatus(ObjectiveType.LORD, lordSlain + 1, true, 0, "Lord", next)
    }
    // NO LORD SLAIN YET - remaining logic is Turtle -> Lord.
    // LORD PHASE - already slain 3 Turtles, no Lord yet
    if (turtleSlain >= 3) {
        val lordSpawn = (lastSlainAt ?: 480) + 120
        if (elapsed < lordSpawn) return ObjectiveStatus(ObjectiveType.LORD, 1, false, lordSpawn - elapsed, "Lord", lordSpawn)
        return ObjectiveStatus(ObjectiveType.LORD, 1, true, 0, "Lord", lordSpawn)
    }
    // TURTLE PHASE, but past 8:00 - Turtle auto-converts to Lord, or last Turtle -> Lord in 120s
    // Here lastSlainAt is guaranteed to be a Turtle slain (lordSlain==0 checked above), so +120 is correct.
    if (elapsed >= 480) {
        if (lastSlainAt != null) {
            val lordSpawn = lastSlainAt + 120
            if (elapsed < lordSpawn) return ObjectiveStatus(ObjectiveType.LORD, 1, false, lordSpawn - elapsed, "Lord", lordSpawn)
            return ObjectiveStatus(ObjectiveType.LORD, 1, true, 0, "Lord", lordSpawn)
        }
        return ObjectiveStatus(ObjectiveType.LORD, 1, true, 0, "Lord", 480)
    }
    // TURTLE PHASE, before 8:00
    if (lastSlainAt == null) {
        if (elapsed < 120) return ObjectiveStatus(ObjectiveType.TURTLE, 1, false, 120 - elapsed, "Turtle", 120)
        return ObjectiveStatus(ObjectiveType.TURTLE, 1, true, 0, "Turtle", 120)
    }
    // A Turtle was slain before
    if (lastSlainAt > 360) {
        // No new Turtle after 6:00 - next is Lord
        val lordSpawn = lastSlainAt + 120
        if (elapsed < lordSpawn) return ObjectiveStatus(ObjectiveType.LORD, 1, false, lordSpawn - elapsed, "Lord", lordSpawn)
        return ObjectiveStatus(ObjectiveType.LORD, 1, true, 0, "Lord", lordSpawn)
    }
    val nextTurtle = lastSlainAt + 120
    if (elapsed < nextTurtle) return ObjectiveStatus(ObjectiveType.TURTLE, turtleSlain + 1, false, nextTurtle - elapsed, "Turtle", nextTurtle)
    if (nextTurtle >= 480) return ObjectiveStatus(ObjectiveType.LORD, 1, true, 0, "Lord", 480)
    return ObjectiveStatus(ObjectiveType.TURTLE, turtleSlain + 1, true, 0, "Turtle", nextTurtle)
}

@Composable
private fun OutlinedText(text: String, color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 9.sp, fontWeight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, lineHeight: TextUnit = 11.sp, overflow: TextOverflow = TextOverflow.Visible) {
    Box(modifier = modifier) {
        Text(text = text, color = Color.Black, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, drawStyle = Stroke(width = 3f), shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
        Text(text = text, color = color, maxLines = maxLines, overflow = overflow, style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)))
    }
}

@Composable
private fun MergedInstructionText(phase: String, callout: String, description: String, maxLines: Int = Int.MAX_VALUE) {
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
            withStyle(style = SpanStyle(color = GithubText, fontSize = 9.sp)) {
                append(description)
            }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp
    // WIDTH +1/3: landscape 72% -> 96% (72% * 4/3 = 96%), portrait 98% stays (already max, capped at screen)
    val widgetWidth = if (isLandscape) (config.screenWidthDp * 0.96f).dp else (config.screenWidthDp * 0.98f).dp
    val guide = remember(context) { loadRoamGuide(context) }
    val plans = guide.plans
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    // HIDE/SHOW DESCRIPTION - upper Hide button hides ONLY description + callout + expanded detail.
    // Top row (game timer, roam type, Turtle/Lord pill, expand, Hide/Show, X) ALWAYS stays visible.
    // When hidden: Top row + spell tracker only. Tap Show to bring description back.
    var isMainHidden by remember { mutableStateOf(false) }
    // DYNAMIC OBJECTIVE STATE - survives plan switch, resets with game Reset
    var turtleSlain by remember { mutableIntStateOf(0) }
    var lordSlain by remember { mutableIntStateOf(0) }
    var lastSlainAt by remember { mutableStateOf<Int?>(null) }
    // For undo: keep last action
    var lastAction by remember { mutableStateOf<String?>(null) }
    // ENEMY SPELL TRACKER - 5 lanes, survives plan switch, resets with game Reset
    var enemySpells by remember { mutableStateOf(defaultEnemySpells()) }
    var spellPickerLane by remember { mutableStateOf<String?>(null) }

    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1 else isRunning = false
        }
    }

    val currentStep = selectedPlan?.steps?.firstOrNull { elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds } ?: selectedPlan?.steps?.lastOrNull()
    val blessingDetail = guide.blessings.firstOrNull { it.id == selectedPlan?.blessingId || it.name.equals(selectedPlan?.blessing?.split("/")?.firstOrNull()?.trim(), ignoreCase = true) }

    // Compute objective every second from elapsed + taps
    val objective = computeObjective(elapsedSeconds, turtleSlain, lordSlain, lastSlainAt)

    // DYNAMIC DESCRIPTION - if Turtle/Lord is ALIVE, override static description with CONTEST vs TRADE
    val isObjectiveAlive = objective.isAlive
    val contestText = if (objective.type == ObjectiveType.TURTLE) selectedPlan?.turtleContest.orEmpty() else selectedPlan?.lordContest.orEmpty()
    val tradeText = if (objective.type == ObjectiveType.TURTLE) selectedPlan?.turtleTrade.orEmpty() else selectedPlan?.lordTrade.orEmpty()
    // BUG FIX: when objective is on countdown (slain), collapsed must NOT show the step's pre-objective text
    // e.g. after Lord slain at 08:11, it showed "Get vision at Lord, need more players - Lord appears at 8:00" even though Lord is dead.
    // Now it shows "Lord slain - Next in 02:39" + "Lord slain. Next in 02:39. Push / farm / vision."
    val dynamicCallout = if (isObjectiveAlive) {
        "${objective.label} ${if (objective.type == ObjectiveType.TURTLE) objective.count else ""} UP".trim()
    } else if (lastSlainAt != null) {
        "${objective.label} slain - Next in ${formatTime(objective.secondsUntil)}"
    } else {
        currentStep?.callout.orEmpty()
    }
    // BRIEF - Epic/Grandmaster: short commands, no baby-talk. JSON templates now ~40-70 chars (was ~90-115).
    // Alive: "CONTEST: ... / TRADE: ..." ( / instead of OR saves space, fits 96% width on 1-2 lines)
    // Slain: "Turtle slain. Next in 02:00. Push / farm / vision." (was "...slain at 08:11. Next Turtle in 02:00 at 10:11. Use this time to...")
    val dynamicDescription = if (isObjectiveAlive) {
        "CONTEST: $contestText / TRADE: $tradeText"
    } else if (lastSlainAt != null) {
        "${objective.label} slain. Next in ${formatTime(objective.secondsUntil)}. Push / farm / vision."
    } else {
        currentStep?.description.orEmpty()
    }
    // Pill text per spec: countdown = "Turtle 2 in 01:23", alive = "Turtle 2" / "Lord"
    val pillText = if (objective.isAlive) {
        if (objective.type == ObjectiveType.TURTLE) "Turtle ${objective.count}" else "Lord"
    } else {
        if (objective.type == ObjectiveType.TURTLE) "Turtle ${objective.count} in ${formatTime(objective.secondsUntil)}" else "Lord in ${formatTime(objective.secondsUntil)}"
    }
    val pillBg = when {
        objective.isAlive && objective.type == ObjectiveType.TURTLE -> GithubGold
        objective.isAlive && objective.type == ObjectiveType.LORD -> GithubRed
        else -> GithubSurface
    }
    val pillTextColor = when {
        objective.isAlive && objective.type == ObjectiveType.TURTLE -> Color.Black
        objective.isAlive && objective.type == ObjectiveType.LORD -> Color.White
        objective.type == ObjectiveType.TURTLE -> GithubGold
        else -> GithubRed
    }

    // SPLIT INTO 2 AREAS (fix: spell tracker was INSIDE 65% box, so 45% + 15% stacked = ~70% opaque, looked dark in screenshot)
    // Outer = TRANSPARENT wrapper, no background, no border - just holds the 2 areas with 4dp gap.
    // AREA 1 = Main widget (objective, timer, Turtle/Lord, description) = 45% opaque, own background + border.
    // AREA 2 = Spell tracker = SEPARATE, NO background, NO border - only the 5 buttons themselves are 15% opaque.
    // So tracker no longer inherits the 65% - it stays truly faint over the game.
    Column(modifier = Modifier.width(widgetWidth).wrapContentHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (plans.isEmpty() || selectedPlan == null || currentStep == null) {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(GithubCanvas.copy(alpha = MAIN_ALPHA), RoundedCornerShape(6.dp)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                OutlinedText("Roam guide unavailable", GithubText, fontSize = 9.sp)
            }
            return@Column
        }
        // ===== AREA 1 - MAIN WIDGET - 45% opaque - TOP ROW ALWAYS STAYS =====
        // FIX: Hide hides ONLY description + callout + expanded detail, NOT the top row.
        // Top row (game timer, roam type, Turtle/Lord pill, expand, Hide/Show, X) ALWAYS stays.
        // When hidden: Area 1 = top row only, then Area 2 (spell tracker) directly below.
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(GithubCanvas.copy(alpha = MAIN_ALPHA), RoundedCornerShape(6.dp)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {

        // ROW 1 - TOP: timer + plan + DYNAMIC TURTLE/LORD PILL (clickable when alive) + expand + X
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).clickable { isRunning = !isRunning }.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedText(formatTime(elapsedSeconds), GithubText, fontSize = 14.sp, fontWeight = FontWeight.Black)
                OutlinedText(if (isRunning) "II" else "▶", GithubAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.weight(1f)) {
                // TOP BAR shows SHORT label only: "Crowd Control" not "Crowd Control - Setter" - saves ~40% width, no "Crowd Cont" truncation
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = MAIN_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).clickable { planMenuExpanded = true }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedText(selectedPlan.label.substringBefore(" - ").trim(), GithubAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedText("▾", GithubMuted, fontSize = 10.sp)
                }
                // ROAM TYPE PICKER - COMPACT 2-COLUMN GRID (was 1-col 6-row tall list)
                // 6 types -> 3 rows x 2 cols, 30dp per cell, total ~98dp tall (was ~216dp). Short + suffix on 2 lines.
                DropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }, modifier = Modifier.width(240.dp).background(GithubSurface.copy(alpha = PICKER_ALPHA)).heightIn(max = 130.dp)) {
                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        plans.chunked(2).forEach { rowPlans ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowPlans.forEach { plan ->
                                    val isSel = plan.id == selectedPlan.id
                                    val short = plan.label.substringBefore(" - ").trim()
                                    val suffix = plan.label.substringAfter(" - ", "").trim()
                                    Box(modifier = Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(5.dp)).background(if (isSel) GithubGold.copy(alpha = 0.25f) else GithubSurface).border(1.dp, if (isSel) GithubGold else GithubBorder, RoundedCornerShape(5.dp)).clickable { selectedPlanId = plan.id; planMenuExpanded = false }.padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = short, color = if (isSel) GithubGold else GithubText, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (suffix.isNotEmpty()) Text(text = suffix, color = GithubMuted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                if (rowPlans.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            // TURTLE / LORD PILL - TAP ONLY WHEN ALIVE = mark slain
            // Alive: gold (Turtle) / red (Lord), tap to mark slain. Countdown: surface, not clickable, shows "in MM:SS"
            val pillModifier = if (objective.isAlive) {
                Modifier.clip(RoundedCornerShape(5.dp)).background(pillBg).border(1.dp, pillTextColor.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).clickable {
                    // Mark slain at current game time
                    lastAction = if (objective.type == ObjectiveType.TURTLE) "Turtle ${objective.count}" else "Lord"
                    if (objective.type == ObjectiveType.TURTLE) turtleSlain += 1 else lordSlain += 1
                    lastSlainAt = elapsedSeconds
                }.padding(horizontal = 6.dp, vertical = 4.dp)
            } else {
                Modifier.clip(RoundedCornerShape(5.dp)).background(pillBg.copy(alpha = WIDGET_ALPHA)).border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 4.dp)
            }
            Box(modifier = pillModifier, contentAlignment = Alignment.Center) {
                // For alive pill, use plain Text (no black outline) so gold/red bg stays readable. For countdown, use OutlinedText.
                if (objective.isAlive) {
                    Text(text = pillText, color = pillTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                } else {
                    OutlinedText(pillText, pillTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            OutlinedText(if (isExpanded) "▲" else "▼", GithubMuted, modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            // HIDE/SHOW BUTTON - upper side: tap to hide ONLY description + callout + expanded detail.
            // Top row (timer, roam, Turtle/Lord pill, X) stays. Label flips Hide <-> Show.
            OutlinedText(if (isMainHidden) "Show" else "Hide", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = MAIN_ALPHA)).clickable { isMainHidden = !isMainHidden }.padding(horizontal = 6.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            OutlinedText("X", GithubMuted, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } // end ROW 1 - TOP - always visible, even when description hidden

        // DESCRIPTION + CALLOUT - HIDDEN when isMainHidden, top row above stays
        if (!isMainHidden) {
        if (!isExpanded) {
            // COLLAPSED: dynamic callout + description, adaptive height
            // If Turtle/Lord alive: "Turtle 2 UP - CONTEST: ... OR TRADE: ..."
            // If countdown: normal step description
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable { isExpanded = true }.padding(horizontal = 2.dp, vertical = 2.dp)) {
                MergedInstructionText(phase = currentStep.phase, callout = dynamicCallout, description = dynamicDescription, maxLines = Int.MAX_VALUE)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 2.dp, vertical = 2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentStep.phase.isNotBlank()) OutlinedText(currentStep.phase, GithubAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    OutlinedText(currentStep.timeLabel.ifBlank { formatTime(currentStep.startSeconds) + "-" + formatTime(currentStep.endSeconds) }, GithubMuted, fontSize = 8.sp)
                    OutlinedText("Ends ${formatTime((currentStep.endSeconds - elapsedSeconds).coerceAtLeast(0))}", GithubMuted, fontSize = 8.sp)
                    if (guide.meta.patch.isNotBlank()) OutlinedText("S42 ${guide.meta.patch}", GithubMuted, fontSize = 8.sp)
                }
                MergedInstructionText(phase = "", callout = dynamicCallout, description = dynamicDescription, maxLines = Int.MAX_VALUE)
                // OBJECTIVE DETAIL - only in expanded, shows Contest vs Trade clearly + how pill works
                Spacer(Modifier.height(3.dp))
                val objTitle = if (objective.isAlive) {
                    "${objective.label} ${if (objective.type == ObjectiveType.TURTLE) objective.count else ""} ALIVE - Tap pill when slain".trim()
                } else {
                    "${objective.label} ${if (objective.type == ObjectiveType.TURTLE) objective.count else ""} in ${formatTime(objective.secondsUntil)} - Slain: $turtleSlain Turtle, $lordSlain Lord".trim()
                }
                OutlinedText("Objective: $objTitle", if (objective.type == ObjectiveType.TURTLE) GithubGold else GithubRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                if (objective.isAlive) {
                    OutlinedText("Contest: $contestText", GithubGreen, fontSize = 9.sp, lineHeight = 11.sp)
                    OutlinedText("Trade (if no contest): $tradeText", GithubText, fontSize = 9.sp, lineHeight = 11.sp)
                } else {
                    OutlinedText("Next: ${objective.label} in ${formatTime(objective.secondsUntil)}. Use this time to push, farm, vision, reset.", GithubMuted, fontSize = 9.sp, lineHeight = 11.sp)
                    if (lastSlainAt != null) OutlinedText("Last slain: $lastAction at ${formatTime(lastSlainAt ?: 0)} - Tap Undo if wrong.", GithubMuted, fontSize = 8.sp, lineHeight = 10.sp)
                }
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
                    OutlinedText("Reset", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable { elapsedSeconds = 0; isRunning = false; turtleSlain = 0; lordSlain = 0; lastSlainAt = null; lastAction = null; enemySpells = defaultEnemySpells(); spellPickerLane = null }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedText("Back", GithubMuted, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (lastSlainAt != null) {
                        OutlinedText("Undo Slain", GithubGold, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(GithubSurface.copy(alpha = WIDGET_ALPHA)).clickable {
                            if (lastAction?.startsWith("Lord") == true) lordSlain = (lordSlain - 1).coerceAtLeast(0) else turtleSlain = (turtleSlain - 1).coerceAtLeast(0)
                            lastSlainAt = null; lastAction = null
                        }.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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
        } // end if (!isMainHidden) - description/callout hidden, top row stays
        } // ===== END AREA 1 - 45% - top row always stays =====

        // ===== AREA 2 - SPELL TRACKER - SEPARATE, 15% ONLY, NO 45% BACKGROUND =====
        // This Column has NO background / NO border - so buttons are truly 15% opaque over the game,
        // not 15% on top of 45% (which made them look dark in your screenshot).
        // Always visible, collapsed + expanded. Same simplified design:
        // Not clicked: [EXP] [JG] [MID] [GOLD] [ROAM] - lane only. Clicked: [01:45 gold fill] - timer only.
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            enemySpells.forEach { tracker ->
                val spell = tracker.spell
                val elapsedSinceUse = tracker.usedAt?.let { elapsedSeconds - it } ?: Int.MAX_VALUE
                val remaining = if (tracker.usedAt == null) 0 else (spell.cooldown - elapsedSinceUse).coerceAtLeast(0)
                val isOnCooldown = tracker.usedAt != null && remaining > 0
                // Gold fill = button background itself. 0 = empty (transparent), 1 = full gold.
                val cdProgress = if (isOnCooldown) (elapsedSinceUse.toFloat() / spell.cooldown.toFloat()).coerceIn(0f, 1f) else 0f
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(GithubSurface.copy(alpha = ENEMY_SPELL_ALPHA))
                            .border(1.dp, if (isOnCooldown) GithubGold.copy(alpha = 0.8f) else GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = {
                                    enemySpells = enemySpells.map {
                                        if (it.lane == tracker.lane) it.copy(usedAt = if (isOnCooldown) null else elapsedSeconds) else it
                                    }
                                },
                                onLongClick = { spellPickerLane = tracker.lane }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // GOLD BAR FILLING BACKGROUND - only when on CD, fills left -> right
                        if (isOnCooldown) {
                            Box(modifier = Modifier.fillMaxWidth(cdProgress).height(28.dp).background(GithubGold).align(Alignment.CenterStart))
                        }
                        // Text: lane when idle, timer when on CD. No spell name, no READY, no small bar.
                        // Use OutlinedText (black outline + light text) so timer stays readable when gold fill is only 25% -
                        // plain black text would be invisible on the dark 75% side.
                        OutlinedText(
                            text = if (isOnCooldown) formatTime(remaining) else tracker.lane,
                            color = if (isOnCooldown) Color.White else GithubText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                    // SPELL PICKER - COMPACT 2-COLUMN GRID (was 1-col 12-row tall list that covered screen)
                    // 12 spells -> 6 rows x 2 cols, 26dp per cell, total ~175dp tall (was ~384dp). Fits in landscape, no full-screen cover.
                    DropdownMenu(expanded = spellPickerLane == tracker.lane, onDismissRequest = { spellPickerLane = null }, modifier = Modifier.width(230.dp).background(GithubSurface.copy(alpha = PICKER_ALPHA)).heightIn(max = 185.dp)) {
                        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            BATTLE_SPELLS.chunked(2).forEach { rowSpells ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    rowSpells.forEach { s ->
                                        val isSel = s.name == spell.name
                                        Box(modifier = Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(if (isSel) GithubGold.copy(alpha = 0.25f) else GithubSurface).border(1.dp, if (isSel) GithubGold else GithubBorder, RoundedCornerShape(5.dp)).clickable {
                                            enemySpells = enemySpells.map { if (it.lane == tracker.lane) it.copy(spell = s, usedAt = null) else it }
                                            spellPickerLane = null
                                        }.padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                            Text(text = if (isSel) "✓ ${s.name.uppercase()} ${s.cooldown}s" else "${s.name.uppercase()} ${s.cooldown}s", color = if (isSel) GithubGold else GithubText, fontSize = 8.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (rowSpells.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedText("Tap = enemy used spell  •  Tap again = cancel  •  Long-press = switch spell", GithubMuted, fontSize = 7.sp)
        } // ===== END AREA 2 - 15% only =====
    } // ===== END OUTER WRAPPER (transparent) =====
}
