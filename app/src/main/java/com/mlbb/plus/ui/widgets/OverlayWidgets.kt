package com.mlbb.plus.ui.widgets

/*
 * The in-game roam widget: timer, objective tracker, enemy battle-spell cooldowns and the
 * role/type guide, plus the optional Voice Guide controls. The overlay engine lives in JetOverlay.kt.
 *
 * Roles: the header selector opens Role -> Type menus. The role loads
 * assets/<role>_guide.json; its type is one entry of that file's "roles" array. A role whose
 * file is missing or invalid still gets the timer, objective and spell tracker.
 * Schema and how to add a role: docs/overview.md ("What the widget reads").
 */

// ── SINGLE SOURCE OF TRUTH ────────────────────────────────────────────────────
// This is the ONLY file that may declare Roam* / Tracker* / Guide* types.
// Do NOT keep RoamGuideWidget.kt, EnemySpellTrackerWidget.kt, or an older
// RoamOverlayWidgets.kt alongside it — Kotlin treats top-level declarations
// in the same package as redeclarations even when they are `private`.
// If you want split files again, you must delete this file first and make
// TrackedLaneSpell `internal` (not `private`) so the other file can see it.

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.tts.SpokenStep
import com.mlbb.plus.tts.VoiceGuideManager
import com.mlbb.plus.tts.VoiceGuideTransitions
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.MLBBPlusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

private val GuideBackground = MLBBPlusColors.GithubBg.copy(alpha = 0.42f)
private val GuideSurface = MLBBPlusColors.GithubCard.copy(alpha = 0.46f)
private val GuideBorder = MLBBPlusColors.GithubBorder.copy(alpha = 0.48f)
private val GuideAccent = MLBBPlusColors.Blue
private val GuideText = MLBBPlusColors.GithubText
private val GuideMuted = MLBBPlusColors.GithubSecondary
private val GuideGold = MLBBPlusColors.Gold
private val GuideRed = MLBBPlusColors.Red

// --- Widget width (landscape only; portrait shows just the bubble) ---
// 70% of the screen width. The header holds the timer, role selector, and the five
// spell cells, so it needs this room. The width is the same collapsed or expanded.
// (Earlier builds used 52%, and 68% when expanded; both are gone.)
private const val WIDGET_WIDTH_FRACTION_LANDSCAPE = 0.70f
private const val WIDGET_MIN_WIDTH_DP = 300f
// Card width while the spell tracker is hidden: fits the header row, which has no spell cells.
private const val HEADER_COMPACT_WIDTH_DP = 360f

/** Card width in dp for a landscape screen of [screenWidthDp]. */
internal fun landscapeWidgetWidthDp(screenWidthDp: Float): Float =
    (screenWidthDp * WIDGET_WIDTH_FRACTION_LANDSCAPE)
        .coerceAtLeast(WIDGET_MIN_WIDTH_DP)
        .coerceAtMost(screenWidthDp - 8)

// Low-distraction knobs: tweak these first.
private const val IDLE_FADE_MS = 4_000L       // no touch for this long -> fade
private const val EXPANDED_IDLE_MS = 10_000L  // longer grace when you expanded to read
private const val IDLE_ALPHA = 0.6f           // whole-widget alpha while idle (spells + guide stay readable)
private const val URGENT_SECONDS = 10         // objective turns gold/red + wakes at <= this
private const val GAME_CLOCK_MAX_SECONDS = 18 * 60  // the overlay timer stops at 18:00

private data class GuideStep(
    val startSeconds: Int,
    val endSeconds: Int,
    val description: String,
    val timeLabel: String,
    val phase: String,
    val callout: String,
    val checklist: List<String>,
    val vision: String,
    val decision: String,
    val dont: String,
    // per-step text the widget used to ignore.
    val compact: String = "",
    val objectiveContest: String = "",
    val objectiveTrade: String = ""
)

private data class GuideBlessing(
    val id: String,
    val name: String,
    val effect: String,
    val cooldown: String = "",
    val unlock: String = ""
)

// Turtle / Lord rules. Defaults = patch 2.2.23 values; every guide's meta.objective_timers overrides them.
private data class ObjectiveTimers(
    val turtleFirst: Int = 120,
    val turtleRespawn: Int = 120,
    val turtleMax: Int = 3,
    val noNewTurtleAfter: Int = 360,
    val convertAt: Int = 480,
    val convertDelay: Int = 5,
    val lordAfterTurtle: Int = 120,
    val lordRespawnEarly: Int = 180,
    val lordRespawnLate: Int = 120,
    val lordEvolveAt: Int = 1080
)

private data class GuidePlan(
    val id: String,
    val label: String,
    val blessing: String,
    val blessingId: String,
    val heroes: List<String>,
    val emblem: String,
    val steps: List<GuideStep>,
    val turtleContest: String,
    val turtleTrade: String,
    val lordContest: String,
    val lordTrade: String,
    // role-level fields the widget used to ignore.
    val winCondition: String = "",
    val loseCondition: String = "",
    val battleSpells: List<String> = emptyList(),
    val coreItems: List<String> = emptyList(),
    val blessingAlt: String = ""
)

private data class GuideData(
    val patch: String = "",
    val blessings: List<GuideBlessing> = emptyList(),
    val plans: List<GuidePlan> = emptyList(),
    val retributions: List<GuideBlessing> = emptyList(),
    val globalRules: List<String> = emptyList(),
    val timers: ObjectiveTimers = ObjectiveTimers(),
    val spells: List<TrackedSpell> = emptyList(),
    val spellDefaults: Map<String, String> = emptyMap()
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

private fun parseStep(json: JSONObject): GuideStep {
    val description = json.optString("instruction").takeIf(String::isNotBlank)
        ?: json.optString("description")
    return GuideStep(
        startSeconds = json.optInt("startSeconds").coerceAtLeast(0),
        endSeconds = json.optInt("endSeconds").coerceAtLeast(0),
        description = description,
        timeLabel = json.optString("timeLabel"),
        phase = json.optString("phase"),
        callout = json.optString("callout").ifBlank { json.optString("objective") },
        checklist = json.stringList("checklist"),
        vision = json.optString("vision"),
        decision = json.optString("decision"),
        dont = json.optString("dont"),
        compact = json.optString("compact"),
        objectiveContest = json.optString("objective_contest"),
        objectiveTrade = json.optString("objective_trade")
    )
}

private fun parsePlan(json: JSONObject): GuidePlan {
    val stepArray = json.optJSONArray("steps") ?: JSONArray()
    val steps = buildList {
        for (index in 0 until stepArray.length()) {
            val step = stepArray.optJSONObject(index) ?: continue
            add(parseStep(step))
        }
    }.sortedBy(GuideStep::startSeconds)
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

    return GuidePlan(
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
        lordTrade = templates?.optString("lord_trade").orEmpty(),
        winCondition = json.optString("win_condition"),
        loseCondition = json.optString("lose_condition"),
        battleSpells = json.stringList("battle_spells"),
        coreItems = json.stringList("core_items"),
        blessingAlt = json.optString("blessing_alt")
    )
}

// Role -> asset file: Roam -> roam_guide.json, Jungle -> jungle_guide.json, EXP -> exp_guide.json ...
private fun guideAssetFor(role: String): String = "${role.lowercase()}_guide.json"

// Starting type for a role. The launcher passes the guide TYPE ID ("initiator", "crit", "tank_exp").
// Older launches / saved state may still carry a label ("Support", "Tank"), so fall back to the
// short label, then to the first type.
private fun defaultPlanId(guide: GuideData, roleType: String): String =
    guide.plans.firstOrNull { it.id.equals(roleType, ignoreCase = true) }?.id
        ?: guide.plans.firstOrNull { it.label.substringBefore(" - ").trim().equals(roleType, ignoreCase = true) }?.id
        ?: guide.plans.firstOrNull()?.id.orEmpty()

private fun parseTimers(meta: JSONObject?): ObjectiveTimers {
    val d = ObjectiveTimers()
    val t = meta?.optJSONObject("objective_timers") ?: return d
    val turtle = t.optJSONObject("turtle")
    val lord = t.optJSONObject("lord")
    return ObjectiveTimers(
        turtleFirst = turtle?.optInt("first_spawn_seconds", d.turtleFirst) ?: d.turtleFirst,
        turtleRespawn = turtle?.optInt("respawn_seconds", d.turtleRespawn) ?: d.turtleRespawn,
        turtleMax = turtle?.optInt("max_per_game", d.turtleMax) ?: d.turtleMax,
        noNewTurtleAfter = turtle?.optInt("no_new_turtle_if_last_slain_after_seconds", d.noNewTurtleAfter) ?: d.noNewTurtleAfter,
        convertAt = turtle?.optInt("auto_convert_to_lord_at_seconds", d.convertAt) ?: d.convertAt,
        convertDelay = turtle?.optInt("auto_convert_delay_if_no_damage_seconds", d.convertDelay) ?: d.convertDelay,
        // The "120s after the last Turtle" rule is prose in the JSON (lord.first_spawn_rule), so it stays a constant.
        lordAfterTurtle = d.lordAfterTurtle,
        lordRespawnEarly = lord?.optInt("respawn_seconds_before_18min", d.lordRespawnEarly) ?: d.lordRespawnEarly,
        lordRespawnLate = lord?.optInt("respawn_seconds_after_18min", d.lordRespawnLate) ?: d.lordRespawnLate,
        lordEvolveAt = lord?.optInt("evolves_at_seconds", d.lordEvolveAt) ?: d.lordEvolveAt
    )
}

private fun parseBuffList(array: JSONArray?): List<GuideBlessing> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val o = array.optJSONObject(index) ?: continue
            add(
                GuideBlessing(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    effect = o.optString("effect"),
                    cooldown = o.optString("cooldown"),
                    unlock = o.optString("unlock")
                )
            )
        }
    }
}

private fun loadGuide(context: Context, role: String): GuideData = runCatching {
    val source = context.assets.open(guideAssetFor(role)).bufferedReader().use { it.readText() }
    val root = JSONObject(source)
    val roles = root.optJSONArray("roles") ?: root.optJSONArray("plans") ?: root.optJSONArray("types") ?: JSONArray()
    val plans = buildList {
        for (index in 0 until roles.length()) {
            val entry = roles.optJSONObject(index) ?: continue
            parsePlan(entry).takeIf { it.steps.isNotEmpty() }?.let(::add)
        }
    }
    val meta = root.optJSONObject("meta")
    val battleSpells = root.optJSONObject("battle_spells")
    val spellArray = battleSpells?.optJSONArray("spells")
    val spells = buildList {
        if (spellArray != null) {
            for (index in 0 until spellArray.length()) {
                val o = spellArray.optJSONObject(index) ?: continue
                val name = o.optString("name")
                val cooldown = o.optInt("cooldown_seconds", 0)
                if (name.isNotBlank() && cooldown > 0) add(TrackedSpell(name, cooldown))
            }
        }
    }
    val defaults = buildMap<String, String> {
        val d = battleSpells?.optJSONObject("defaults")
        if (d != null) {
            val keys = d.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k.uppercase(), d.optString(k))
            }
        }
    }
    GuideData(
        patch = meta?.optString("patch").orEmpty(),
        blessings = parseBuffList(root.optJSONArray("blessings")),
        plans = plans,
        retributions = parseBuffList(root.optJSONArray("retribution_types")),
        globalRules = meta?.let { m ->
            val a = m.optJSONArray("global_rules")
            if (a == null) emptyList() else buildList { for (i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        }.orEmpty(),
        timers = parseTimers(meta),
        spells = spells,
        spellDefaults = defaults
    )
}.getOrElse { e ->
    android.util.Log.w("RoamGuideWidget", "Could not load ${guideAssetFor(role)}: ${e.message}")
    GuideData()
}

private fun formatGameTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}

/**
 * Computes the next objective using game time and manually recorded slain events.
 * Before the first Turtle kill, the known schedule is used; subsequent objective
 * respawns are based on the time the player marked the last objective slain.
 * All numbers come from the guide's meta.objective_timers (see [ObjectiveTimers]).
 */
private fun computeObjective(
    elapsedSeconds: Int,
    turtleSlain: Int,
    lordSlain: Int,
    lastSlainAt: Int?,
    // Redundant by invariant, kept for call-site clarity: the slain button sets
    // `lastSlainWasLord = !isTurtle` and increments `lordSlain` in the same click
    // (and undo/reset restore both together), so lastSlainWasLord == true always
    // implies lordSlain > 0 — which is handled by the first branch below and never
    // needs this flag. It IS used for the slain label ("Lord slain" vs "Turtle
    // slain"). Do not add a branch here that reads it without re-checking that
    // invariant.
    @Suppress("UnusedParameter")
    lastSlainWasLord: Boolean = false,
    timers: ObjectiveTimers = ObjectiveTimers()
): ObjectiveStatus {
    // Wiki: "First Lord will appear exactly 2 min after last Turtle slain.
    // E.g. last Turtle 7:45 -> Lord 9:45." + "Turtle not slain after 8:00
    // turns into Lord if no damage for 5s." + "No Turtle if last slain after 6:00."
    // So: Turtle 7:30 -> Lord 9:30, NOT 8:00. Lord only at 8:00 if Turtle
    // was never killed / still alive at 8:00 (it converts).
    if (lordSlain > 0) {
        // Lord already slain once: respawn 180s before the evolve time (18:00), 120s after it.
        val slainAt = lastSlainAt ?: elapsedSeconds
        val nextSpawn = slainAt + if (slainAt < timers.lordEvolveAt) timers.lordRespawnEarly else timers.lordRespawnLate
        return ObjectiveStatus(
            type = ObjectiveType.LORD,
            count = lordSlain + 1,
            isAlive = elapsedSeconds >= nextSpawn,
            secondsUntil = (nextSpawn - elapsedSeconds).coerceAtLeast(0)
        )
    }
    if (elapsedSeconds < timers.turtleFirst && turtleSlain == 0) {
        return ObjectiveStatus(ObjectiveType.TURTLE, 1, false, timers.turtleFirst - elapsedSeconds)
    }
    // All Turtles done -> Lord = last slain + 120 (6:00 -> 8:00, 6:30 -> 8:30).
    if (turtleSlain >= timers.turtleMax) {
        val lordSpawn = (lastSlainAt ?: timers.noNewTurtleAfter) + timers.lordAfterTurtle
        return ObjectiveStatus(ObjectiveType.LORD, 1, elapsedSeconds >= lordSpawn, (lordSpawn - elapsedSeconds).coerceAtLeast(0))
    }
    // Slain after 6:00 -> no more Turtle, next is Lord at slain + 120.
    if (lastSlainAt != null && lastSlainAt > timers.noNewTurtleAfter) {
        val lordSpawn = lastSlainAt + timers.lordAfterTurtle
        return ObjectiveStatus(ObjectiveType.LORD, 1, elapsedSeconds >= lordSpawn, (lordSpawn - elapsedSeconds).coerceAtLeast(0))
    }
    // Turtle still alive / never slain at 8:00 -> it converts to Lord once nobody damages it for
    // 5s (guide: "delayed up to 9:00"). Count the 5s instead of showing Lord UP at exactly 8:00.
    if (elapsedSeconds >= timers.convertAt) {
        val lordSpawn = timers.convertAt + timers.convertDelay
        return ObjectiveStatus(ObjectiveType.LORD, 1, elapsedSeconds >= lordSpawn, (lordSpawn - elapsedSeconds).coerceAtLeast(0))
    }
    // Otherwise next Turtle = last slain + respawn, or the first spawn if none.
    val nextTurtle = lastSlainAt?.plus(timers.turtleRespawn) ?: timers.turtleFirst
    return ObjectiveStatus(
        type = ObjectiveType.TURTLE,
        count = turtleSlain + 1,
        isAlive = elapsedSeconds >= nextTurtle,
        secondsUntil = (nextTurtle - elapsedSeconds).coerceAtLeast(0)
    )
}

/**
 * The roam guide card, in two rows.
 *
 * Row 1, the header: timer | role and plan (e.g. "E4") | turtle or lord timer ("T 00:23") |
 * guide + / - | voice On / Off | x exit | > spell tracker.
 * The spell tracker is hidden by default. Opening it widens the card sideways to 70% of the screen;
 * it never makes the card taller.
 *
 * Row 2, the guide: the current step, shown while the guide is open (the + / - button).
 *
 * There are no voice transport controls. The coach reads each step once, automatically, when the
 * step begins, so there is nothing to replay, pause or skip.
 *
 * Width comes from [screenWidthDp] (the live overlay window), not from LocalConfiguration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoamGuideWidget(
    role: String = "Roam",
    roleType: String = "",
    onBack: () -> Unit,
    // Voice Guide coach. Null (the default) keeps previews and existing callers working,
    // and hides the On / Off control.
    voice: VoiceGuideManager? = null,
    // Width of the screen in dp. Defaults to the live overlay window (JetOverlay); tests pass a fixed value.
    screenWidthDp: Float = JetOverlay.screenWidthDp
) {
    val context = LocalContext.current
    // Dynamic roles: each role's JSON is parsed once and cached for the overlay's lifetime.
    val guideCache = remember { HashMap<String, GuideData>() }
    val guideFor: (String) -> GuideData = { r -> guideCache.getOrPut(r) { loadGuide(context, r) } }
    // Which <role>_guide.json files actually exist (menu marks the missing ones).
    val assetNames = remember(context) { runCatching { context.assets.list("")?.toSet() }.getOrNull().orEmpty() }
    // The launcher's role/type is only the START state; the in-widget selector changes it afterwards.
    val startRole = if (role in RoleTypes.roles) role else "Roam"
    var activeRole by remember { mutableStateOf(startRole) }
    var selectedPlanId by rememberSaveable { mutableStateOf(defaultPlanId(guideFor(startRole), roleType)) }
    LaunchedEffect(role, roleType) {
        // Re-launching from the app with another role/type re-targets the widget.
        activeRole = startRole
        selectedPlanId = defaultPlanId(guideFor(startRole), roleType)
    }
    val guide = guideFor(activeRole)
    val plans = guide.plans
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    // Selector menu: 0 = closed, 1 = role list, 2 = type list for pendingRole.
    var menuStage by remember { mutableIntStateOf(0) }
    var pendingRole by remember { mutableStateOf(startRole) }
    // Row 2 (the guide) is open by default. The + / - button folds it.
    var guideOpen by rememberSaveable { mutableStateOf(true) }
    // The spell tracker is hidden by default. The > / < button opens it.
    var spellsOpen by rememberSaveable { mutableStateOf(false) }
    var turtleSlain by rememberSaveable { mutableIntStateOf(0) }
    var lordSlain by rememberSaveable { mutableIntStateOf(0) }
    var lastSlainAt by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastSlainWasLord by rememberSaveable { mutableStateOf(false) }
    var previousObjective by remember { mutableStateOf<ObjectiveSnapshot?>(null) }

    // idle fade. Any touch on the overlay window bumps JetOverlay.touchTick;
    // we wake, then fade again after the idle delay. snapshotFlow reads the tick
    // outside composition so drags don't recompose the widget.
    var awake by remember { mutableStateOf(true) }
    val guideOpenNow by rememberUpdatedState(guideOpen)
    val menuOpenNow by rememberUpdatedState(menuStage != 0)
    LaunchedEffect(Unit) {
        snapshotFlow { JetOverlay.touchTick.longValue }.collectLatest {
            awake = true
            delay(if (guideOpenNow) EXPANDED_IDLE_MS else IDLE_FADE_MS)
            if (!menuOpenNow) awake = false
        }
    }
    // Menus are separate windows (touches in them never reach the overlay), so wake on
    // open and again on close to restart the idle timer.
    LaunchedEffect(menuStage) { JetOverlay.wake() }
    val fadeAlpha by animateFloatAsState(
        targetValue = if (awake) 1f else IDLE_ALPHA,
        animationSpec = tween(durationMillis = if (awake) 150 else 900),
        label = "idleFade"
    )

    // Width: compact while the spell tracker is hidden, 70% of the screen once it is open.
    // Only the width changes; the card height depends on the guide row alone.
    val expandedWidth = landscapeWidgetWidthDp(screenWidthDp)
    val compactWidth = minOf(HEADER_COMPACT_WIDTH_DP, expandedWidth)
    val cardWidth by animateDpAsState(
        targetValue = (if (spellsOpen) expandedWidth else compactWidth).dp,
        animationSpec = tween(durationMillis = 180),
        label = "cardWidth"
    )

    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    // Plan number is 1-based within the role: "E4" is EXP, plan 4.
    val planNumber = selectedPlan?.let { plans.indexOf(it) + 1 } ?: 1
    val selectorLabel = "${roleLetter(activeRole)}$planNumber ▾"
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < GAME_CLOCK_MAX_SECONDS) elapsedSeconds += 1 else isRunning = false
        }
    }

    // The step index drives both the displayed step and the auto-speak key.
    // Semantics: first step whose window contains the clock, else the last one.
    val currentStepIndex = selectedPlan?.steps?.indexOfFirst {
        elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds
    }?.takeIf { it >= 0 } ?: (selectedPlan?.steps?.lastIndex ?: -1)
    val currentStep = selectedPlan?.steps?.getOrNull(currentStepIndex)
    val hasGuide = selectedPlan != null && currentStep != null

    // ---------------- Voice Guide (floating TTS coach) ----------------
    // Inert unless the service handed us a manager AND the player switched the feature on.
    // `voice` is null until the overlay host attaches a manager (and in previews), so
    // voiceOn proves both voiceState and voice are non-null.
    val voiceState = voice?.state
    val voiceOn = voiceState?.enabled == true
    val spokenSteps = remember(selectedPlan) {
        selectedPlan?.steps?.mapIndexed { index, step -> step.toSpoken(selectedPlan.id, index) }
            ?: emptyList()
    }
    val currentSpoken = spokenSteps.getOrNull(currentStepIndex)
    val coachIsSpeakingThisStep = voiceOn &&
        voiceState.phase == VoiceGuideManager.Phase.SPEAKING &&
        voiceState.speakingKey == currentSpoken?.key
    // Auto-speak once per step change. The key carries the plan id, the index
    // and the start time, so a recomposition or a 1 Hz clock tick cannot
    // re-trigger it, and switching plan or role always does.
    LaunchedEffect(currentSpoken?.key) {
        val step = currentSpoken ?: return@LaunchedEffect
        // Always register, even while the feature is off: the notification's
        // Play action then has a current step the moment the player turns it on.
        voice?.setCurrentStep(step)
        if (voiceOn) voice.onStepAdvanced(step)
    }

    val objective = computeObjective(elapsedSeconds, turtleSlain, lordSlain, lastSlainAt, lastSlainWasLord, guide.timers)
    val isTurtle = objective.type == ObjectiveType.TURTLE
    // "T 00:23" counts down to the turtle; "T UP" while it is alive (tap to mark it slain).
    val objectiveLetter = if (isTurtle) "T" else "L"
    val objectiveText = if (objective.isAlive) {
        "$objectiveLetter UP"
    } else {
        "$objectiveLetter ${formatGameTime(objective.secondsUntil)}"
    }
    // muted unless the objective is up or about to be. Gold/red now MEAN something.
    val urgent = objective.isAlive || objective.secondsUntil <= URGENT_SECONDS
    val objectiveColor = when {
        !urgent -> GuideMuted
        isTurtle -> GuideGold
        else -> GuideRed
    }
    // One gentle wake when the objective becomes urgent (not a constant glow).
    LaunchedEffect(objective.type, objective.count, urgent) {
        if (urgent) JetOverlay.wake()
    }
    // The step's own objective_contest is written for that moment of the game ("Second Turtle"),
    // so prefer it when the step is about the objective that is up. Otherwise use the role template.
    val stepAboutObjective = currentStep != null && currentStep.phase.contains(objective.label, ignoreCase = true)
    val contestText = (if (stepAboutObjective) currentStep.objectiveContest else null).takeUnless { it.isNullOrBlank() }
        ?: if (isTurtle) selectedPlan?.turtleContest.orEmpty() else selectedPlan?.lordContest.orEmpty()
    // The slain label names the LAST slain objective, not the next one.
    val objectiveDescription = if (!hasGuide) {
        "No guide data for $activeRole (${guideAssetFor(activeRole)} missing or invalid). " +
            "Timer, objective and spells still work."
    } else when {
        objective.isAlive -> contestText
        // countdown lives in the pill; no per-second text churn
        lastSlainAt != null -> "${if (lastSlainWasLord) "Lord" else "Turtle"} slain. " +
            "Next: ${objective.label}. Push, farm, or reset."
        else -> currentStep.description
    }
    // Width is set by spellsOpen only (see cardWidth above), not by the text length.
    // Step progress for timer background. Fixed value, no fillMaxHeight.
    val stepProgress = if (currentStep != null) {
        val span = (currentStep.endSeconds - currentStep.startSeconds).coerceAtLeast(1)
        ((elapsedSeconds - currentStep.startSeconds).toFloat() / span).coerceIn(0f, 1f)
    } else 0f

    // Gap between the two widgets = exactly 1 physical pixel.
    // 1.dp is NOT 1px (at 3x density 1.dp = 3px), so convert 1px -> dp.
    val onePx = with(LocalDensity.current) { 1.toDp() }
    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer { alpha = fadeAlpha },
        verticalArrangement = Arrangement.spacedBy(onePx)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overlay_card")
                .clip(RoundedCornerShape(12.dp))
                .background(GuideBackground)
                .border(1.dp, GuideBorder, RoundedCornerShape(12.dp))
                .animateContentSize(animationSpec = tween(durationMillis = 180))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // ---- Row 1: the header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_header"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer: tap to start or pause the game clock.
                val timerProgressWidth by animateFloatAsState(
                    targetValue = stepProgress,
                    animationSpec = tween(durationMillis = 250),
                    label = "timerProgress"
                )
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(GuideSurface)
                        .border(
                            1.dp,
                            if (isRunning) GuideAccent.copy(alpha = 0.65f) else GuideBorder,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { isRunning = !isRunning && elapsedSeconds < GAME_CLOCK_MAX_SECONDS }
                        .semantics { contentDescription = "Start or pause timer" }
                        .testTag("overlay_timer_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(timerProgressWidth)
                            .height(26.dp)
                            .background(GuideAccent.copy(alpha = 0.28f))
                            .align(Alignment.CenterStart)
                    )
                    OutlinedText(
                        text = formatGameTime(elapsedSeconds),
                        color = if (isRunning) GuideText else GuideMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Role and plan: "R1" roam plan 1, "E4" EXP plan 4. Tap to change.
                Box(modifier = Modifier.width(62.dp)) {
                    GuideControl(
                        selectorLabel,
                        if (hasGuide) GuideAccent else GuideMuted,
                        Modifier
                            .fillMaxWidth()
                            .testTag("overlay_role_selector"),
                        maxLines = 1,
                        fontSize = 10.sp,
                        contentDescription = "Change role and plan: $activeRole ${selectedPlan?.label.orEmpty()}",
                        compact = true,
                        onClick = { menuStage = 1 }
                    )
                    DropdownMenu(
                        expanded = menuStage != 0,
                        onDismissRequest = { menuStage = 0 }
                    ) {
                        if (menuStage == 1) {
                            RoleTypes.roles.forEach { r ->
                                val hasFile = guideAssetFor(r) in assetNames
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = (if (r == activeRole) "✓ " else "") + r + (if (hasFile) "" else " · no guide"),
                                            color = if (hasFile) GuideText else GuideMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        if (guideFor(r).plans.isEmpty()) {
                                            activeRole = r
                                            selectedPlanId = ""
                                            menuStage = 0
                                        } else {
                                            pendingRole = r
                                            menuStage = 2
                                        }
                                    }
                                )
                            }
                        } else if (menuStage == 2) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "‹ $pendingRole · pick plan",
                                        color = GuideMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                onClick = { menuStage = 1 }
                            )
                            guideFor(pendingRole).plans.forEach { plan ->
                                val isCurrent = pendingRole == activeRole && plan.id == selectedPlan?.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = (if (isCurrent) "✓ " else "") + plan.label,
                                            color = GuideText,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        activeRole = pendingRole
                                        selectedPlanId = plan.id
                                        menuStage = 0
                                    }
                                )
                            }
                        }
                    }
                }

                // Turtle or lord timer. Tap while it is up to mark it slain.
                val animatedObjColor by animateColorAsState(
                    targetValue = objectiveColor,
                    animationSpec = tween(durationMillis = 220),
                    label = "objectiveColor"
                )
                GuideControl(
                    objectiveText,
                    animatedObjColor,
                    modifier = Modifier
                        .width(64.dp)
                        .testTag("overlay_objective_button"),
                    background = animatedObjColor.copy(alpha = if (objective.isAlive) 0.24f else if (urgent) 0.16f else 0.08f),
                    borderColor = animatedObjColor.copy(alpha = if (urgent) 0.72f else 0.35f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    compact = true,
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
                    contentDescription = if (objective.isAlive) {
                        "Mark ${objective.label} as slain"
                    } else {
                        "${objective.label} in ${formatGameTime(objective.secondsUntil)}"
                    }
                )

                // + / -: fold or open the guide (row 2).
                GuideControl(
                    if (guideOpen) "−" else "+",
                    GuideMuted,
                    modifier = Modifier
                        .width(26.dp)
                        .testTag("overlay_guide_toggle"),
                    fontSize = 14.sp,
                    compact = true,
                    contentDescription = if (guideOpen) "Collapse the guide" else "Expand the guide"
                ) {
                    guideOpen = !guideOpen
                }

                // Voice mode On / Off. Turning it on downloads the selected voice once, as before.
                if (voice != null) {
                    GuideControl(
                        if (voiceOn) "On" else "Off",
                        if (voiceOn) GuideAccent else GuideMuted,
                        Modifier
                            .width(34.dp)
                            .testTag("overlay_voice_toggle"),
                        fontSize = 10.sp,
                        compact = true,
                        contentDescription = if (voiceOn) {
                            "Voice guide is on. Tap to turn it off."
                        } else {
                            "Voice guide is off. Tap to turn it on."
                        }
                    ) {
                        voice.setEnabled(!voiceOn)
                    }
                }

                // x: exit the widget (stops the overlay service).
                GuideControl(
                    "x",
                    GuideMuted,
                    Modifier
                        .width(26.dp)
                        .testTag("overlay_close_button"),
                    fontSize = 14.sp,
                    compact = true,
                    onClick = onBack,
                    contentDescription = "Exit the widget"
                )

                // Spell tracker. It stays composed while hidden, so its cooldowns survive
                // the toggle; only its slot is zero-width while closed.
                Box(
                    modifier = (if (spellsOpen) Modifier.weight(1f) else Modifier.width(0.dp))
                        .clipToBounds()
                ) {
                    EnemySpellTrackerWidget(
                        elapsedSeconds = elapsedSeconds,
                        spells = guide.spells,
                        spellDefaults = guide.spellDefaults,
                        onSpellUsed = { spellName, cooldownSeconds ->
                            if (voiceOn) voice.announceSpell(spellName, cooldownSeconds)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // > opens the spell tracker, < closes it.
                GuideControl(
                    if (spellsOpen) "<" else ">",
                    GuideMuted,
                    Modifier
                        .width(26.dp)
                        .testTag("overlay_spell_toggle"),
                    fontSize = 14.sp,
                    compact = true,
                    contentDescription = if (spellsOpen) "Hide spell tracker" else "Show spell tracker"
                ) {
                    spellsOpen = !spellsOpen
                }
            }

            // ---- Row 2: the guide (open by default; the + / - button folds it) ----
            if (guideOpen) {
                if (objectiveDescription.isNotBlank()) {
                    OutlinedText(
                        text = objectiveDescription,
                        color = if (coachIsSpeakingThisStep) GuideGold else GuideText,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overlay_guide_text")
                    )
                }
                // Clock corrections, kept small and only while the guide is open.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GuideControl("−10s", GuideMuted, compact = true) {
                        elapsedSeconds = (elapsedSeconds - 10).coerceAtLeast(0)
                    }
                    GuideControl("+10s", GuideMuted, compact = true) {
                        elapsedSeconds = (elapsedSeconds + 10).coerceAtMost(GAME_CLOCK_MAX_SECONDS)
                    }
                    GuideControl("Reset", GuideMuted, compact = true) {
                        elapsedSeconds = 0
                        isRunning = false
                        turtleSlain = 0
                        lordSlain = 0
                        lastSlainAt = null
                        lastSlainWasLord = false
                        previousObjective = null
                    }
                    if (previousObjective != null) {
                        GuideControl("Undo slain", GuideGold, compact = true, onClick = {
                            previousObjective?.let { snapshot ->
                                turtleSlain = snapshot.turtleSlain
                                lordSlain = snapshot.lordSlain
                                lastSlainAt = snapshot.lastSlainAt
                                lastSlainWasLord = snapshot.lastSlainWasLord
                            }
                            previousObjective = null
                        })
                    }
                }
            }
        }
    }
}

/** Role letter used in the selector: R, J, M, G or E. */
private fun roleLetter(role: String): String = when (role) {
    "Roam" -> "R"
    "Jungle" -> "J"
    "Mid" -> "M"
    "Gold" -> "G"
    "EXP" -> "E"
    else -> role.take(1).uppercase()
}

private val TrackerSurface = MLBBPlusColors.GithubCard.copy(alpha = 0.46f)
private val TrackerSurfaceCooling = Color(0x663A2E0D)
private val TrackerBorder = MLBBPlusColors.GithubBorder.copy(alpha = 0.50f)
private val TrackerGold = MLBBPlusColors.Gold
private val TrackerMuted = MLBBPlusColors.GithubSecondary

private data class TrackedSpell(val name: String, val cooldownSeconds: Int)
private data class TrackedLaneSpell(
    val lane: String,
    val spell: TrackedSpell,
    val usedAtSeconds: Int? = null
)

// FALLBACK only: used when a guide file has no battle_spells block. The live values come from
// <role>_guide.json -> battle_spells.spells (Sprint went 100s -> 75s in 2.2.23; this table said 100).
private val fallbackSpells = listOf(
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
    TrackedSpell("Sprint", 75),
    TrackedSpell("Vengeance", 75)
)

/** The enemy lane key ("EXP", "JG", "MID", "GOLD", "ROAM") as its one-letter label. */
private fun laneLetter(lane: String): String = when (lane) {
    "EXP" -> "E"
    "JG" -> "J"
    "MID" -> "M"
    "GOLD" -> "G"
    "ROAM" -> "R"
    else -> lane.take(1)
}

/** Seconds left as a short label that fits a 26 dp cell: "42", then "1m" for a minute or more. */
private fun shortCooldown(seconds: Int): String =
    if (seconds >= 60) "${seconds / 60}m" else seconds.toString()

private fun initialTrackedSpells(spells: List<TrackedSpell>, defaults: Map<String, String>) =
    listOf("EXP", "JG", "MID", "GOLD", "ROAM").map { lane ->
        val wanted = defaults[lane] ?: if (lane == "JG") "Retribution" else "Flicker"
        TrackedLaneSpell(lane, spells.firstOrNull { it.name == wanted } ?: spells.first())
    }

private fun formatCooldown(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/**
 * Separate, compact enemy battle-spell cooldown tracker. Tap a lane when its
 * enemy uses the selected spell; long-press to change spell. The cooldown is
 * derived from game time, so pausing or adjusting the game clock stays coherent.
 *
 * Width: this composable is intentionally width-agnostic — it fills whatever
 * its parent gives it. [RoamGuideWidget] places it in the header card (70% of
 * screen width in landscape), so each of the 5 cells is about one fifth of that.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnemySpellTrackerWidget(
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
    spells: List<TrackedSpell> = emptyList(),
    spellDefaults: Map<String, String> = emptyMap(),
    onSpellUsed: ((spellName: String, cooldownSeconds: Int) -> Unit)? = null
) {
    val availableSpells = if (spells.isEmpty()) fallbackSpells else spells
    var trackedSpells by rememberSaveable { mutableStateOf(initialTrackedSpells(availableSpells, spellDefaults)) }
    var openPickerLane by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ENEMY / "Tap = used · Hold = change" header REMOVED.
        // Moved to ? help menu in Roam header. Saves one row + no clipping.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            trackedSpells.forEach { laneSpell ->
                // Cooldown always from the CURRENT guide's table (saved state may hold an older value).
                val spell = availableSpells.firstOrNull { it.name == laneSpell.spell.name } ?: laneSpell.spell
                val elapsedSinceUse = laneSpell.usedAtSeconds?.let { (elapsedSeconds - it).coerceAtLeast(0) }
                val remaining = if (elapsedSinceUse == null) 0
                    else (spell.cooldownSeconds - elapsedSinceUse).coerceAtLeast(0)
                val isCoolingDown = elapsedSinceUse != null && remaining > 0
                val progress = if (isCoolingDown) {
                    (elapsedSinceUse.toFloat() / spell.cooldownSeconds).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isCoolingDown) TrackerSurfaceCooling else TrackerSurface)
                            .border(
                                1.dp,
                                if (isCoolingDown) TrackerGold.copy(alpha = 0.55f) else TrackerBorder,
                                RoundedCornerShape(9.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (!isCoolingDown) {
                                        onSpellUsed?.invoke(spell.name, spell.cooldownSeconds)
                                    }
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
                            }
                            .testTag("overlay_spell_${laneSpell.lane.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        // One line per cell: the cooldown replaces the spell name while it runs,
                        // so the row stays 22 dp tall and the whole header stays short.
                        // One letter per enemy lane (E, J, M, G, R). While cooling down, the
                        // seconds left replace the letter.
                        OutlinedText(
                            text = if (isCoolingDown) shortCooldown(remaining) else laneLetter(laneSpell.lane),
                            color = if (isCoolingDown) TrackerGold.copy(alpha = 0.95f) else TrackerMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = if (isCoolingDown) FontFamily.Monospace else FontFamily.SansSerif,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )

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
private fun OutlinedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.SansSerif,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    Box(
        modifier = modifier,
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.9f),
                    offset = Offset(0f, 1f),
                    blurRadius = 3f
                )
            )
        )
    }
}

/** The step as the coach reads it: a stable key for auto-speak, and the text fragments. */
private fun GuideStep.toSpoken(planId: String, index: Int): SpokenStep = SpokenStep(
    key = "$planId#$index#$startSeconds",
    startSeconds = startSeconds,
    fragments = listOf(phase, callout, description.ifBlank { compact })
        .map { it.trim() }
        .filter { it.isNotBlank() }
)

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
    fontSize: TextUnit = 10.sp,
    // Header controls use the 24 dp row; the expanded panel keeps the touch-friendlier 30 dp.
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 24.dp else 30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 4.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        OutlinedText(
            text = label,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
