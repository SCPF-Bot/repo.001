package com.mlbb.plus.ui.widgets

// widgets/OverlayWidgets.kt — the compact in-game roam widget (timer + objective +
// enemy spell tracker). The overlay engine lives in JetOverlay.kt.
// DYNAMIC ROLES: the header selector opens Role -> Type menus. The role loads
// assets/<role>_guide.json (roam_guide.json, jungle_guide.json, mid_guide.json,
// gold_guide.json, exp_guide.json); the type is one entry of that file's "roles"
// array. A role whose file is missing/invalid still gets the timer, objective
// and spell tracker. Schema + how to add a role: docs/overview.md.
// V13 — GUIDE-DRIVEN (see docs/overview.md "What the widget reads"): enemy-spell cooldowns +
//   defaults now come from the guide's battle_spells (Sprint is 75s in 2.2.23, the hardcoded
//   table said 100s), Turtle/Lord timers come from meta.objective_timers, per-step
//   objective_contest / objective_trade / compact / phase are shown, the pre-game panel shows
//   win condition + emblem + spells + core items, and the launcher passes the guide TYPE ID.
// V10 — idle fade is now gentle (IDLE_ALPHA 0.6, was 0.15 = unreadable mid-game), the
//   short guide text is visible by default, and the 'first tap only wakes' guard is OFF
//   (FIRST_TAP_ONLY_WAKES) so spell taps always register. Also removed the root
//   pointerInput from the widget while that guard is off: it made Compose claim EVERY touch
//   inside the widget, which is what silently killed dragging (see JetOverlay.kt).
// V9 — LOW-DISTRACTION PASS for in-game use:
//   idle fade (whole widget -> IDLE_ALPHA after IDLE_FADE_MS, first tap only wakes it),
//   fixed width/font (no layout jumps), description hidden by default and only
//   'peeks' on step/objective change, muted colors (gold/red only when objective is
//   <= URGENT_SECONDS or up), single-Text soft shadow instead of 9-copy outline,
//   slimmer 30dp spell row, no per-second text churn after a slain.
// V8 — Fixes: (1) Drag restored, (2) 45% opacity both widgets, (3) Lord at 8:00 always,
// (4) Hide instructions deleted, (5) Timer/plan/buttons 28dp UX, + final sweep.

// ── SINGLE SOURCE OF TRUTH ────────────────────────────────────────────────────
// This is the ONLY file that may declare Roam* / Tracker* / Guide* types.
// Do NOT keep RoamGuideWidget.kt, EnemySpellTrackerWidget.kt, or an older
// RoamOverlayWidgets.kt alongside it — Kotlin treats top-level declarations
// in the same package as redeclarations even when they are `private`.
// If you want split files again, you must delete this file first and make
// TrackedLaneSpell `internal` (not `private`) so the other file can see it.

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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

private val GuideBackground = MLBBPlusColors.GithubBg.copy(alpha = 0.25f)
private val GuideSurface = MLBBPlusColors.GithubCard.copy(alpha = 0.25f)
private val GuideBorder = MLBBPlusColors.GithubBorder.copy(alpha = 0.25f)
private val GuideAccent = MLBBPlusColors.Blue
private val GuideText = MLBBPlusColors.GithubText
private val GuideMuted = MLBBPlusColors.GithubSecondary
private val GuideGold = MLBBPlusColors.Gold
private val GuideRed = MLBBPlusColors.Red

// --- Widget width: matched to the green line in the reference photo ---
// Measured from the photo (720px wide): green line runs x = 150 → 526,
// i.e. left = 20.8%, right = 72.9%, width = 52.1% of screen width.
// Previous value was 0.62f in landscape (too wide, blocked extra vision).
// Portrait is not the in-game case (MLBB is landscape) so it stays wide
// for readability, but landscape — the real case — is now exactly 0.52f.
private const val WIDGET_WIDTH_FRACTION_LANDSCAPE = 0.52f
private const val WIDGET_WIDTH_FRACTION_PORTRAIT = 0.92f
private const val WIDGET_MIN_WIDTH_DP = 300f

// V9 low-distraction knobs — tweak these first.
private const val IDLE_FADE_MS = 4_000L       // no touch for this long -> fade
private const val EXPANDED_IDLE_MS = 10_000L  // longer grace when you expanded to read
private const val IDLE_ALPHA = 0.6f           // whole-widget alpha while idle (spells + guide stay readable)
private const val FIRST_TAP_ONLY_WAKES = false // true = while idle, the first tap only wakes the widget (use with a low IDLE_ALPHA)
private const val PEEK_MS = 5_000L            // how long the hidden description shows on a change
private const val URGENT_SECONDS = 10         // objective turns gold/red + wakes at <= this

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
    // V13: per-step text the widget used to ignore.
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
    // V13: role-level fields the widget used to ignore.
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
    // invariant. detekt UnusedParameter, 2026-11-27 lint scan.
    @Suppress("UnusedParameter")
    lastSlainWasLord: Boolean = false,
    timers: ObjectiveTimers = ObjectiveTimers()
): ObjectiveStatus {
    // V11 FIX: V8 was wrong ("first Lord ALWAYS at 8:00").
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
fun RoamGuideWidget(
    role: String = "Roam",
    roleType: String = "",
    onBack: () -> Unit,
    // Voice Guide coach. Null (the default) keeps every existing caller and
    // preview working, and means the widget behaves exactly as before.
    voice: VoiceGuideManager? = null
) {
    val context = LocalContext.current
    // V8 FIX: Width from displayMetrics (true screen), NOT LocalConfiguration.
    // In a WRAP_CONTENT overlay window, LocalConfiguration returns the WINDOW size,
    // so widgetWidth fed back on itself and shrank to ~35% (your screenshot).
    // displayMetrics is the real screen, so 52% is really 52%.
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
    // True once the user picked something in the selector, so the description peeks
    // even before the game timer has started.
    var selectionTouched by remember { mutableStateOf(false) }
    // V11: 3-state cycle, replaces detailsExpanded (bool).
    // 0 = default: short description (2 lines)
    // 1 = expand: full description (6 lines) + details
    // 2 = full collapsed: even short description hidden, only header left
    // Loop: 0 -> 1 -> 2 -> 0. Positional saveable state keeps this local to the widget.
    var displayMode by rememberSaveable { mutableIntStateOf(0) } // V10: default = short guide text visible
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    // V8 FIX (4): Hide instructions DELETED. It hid the description with no way
    // back (button lived inside expanded section, so once hidden you couldn't
    // unhide without re-expanding). Description is now always visible.
    var turtleSlain by rememberSaveable { mutableIntStateOf(0) }
    var lordSlain by rememberSaveable { mutableIntStateOf(0) }
    var lastSlainAt by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastSlainWasLord by rememberSaveable { mutableStateOf(false) }
    var previousObjective by remember { mutableStateOf<ObjectiveSnapshot?>(null) }

    // V9: idle fade. Any touch on the overlay window bumps JetOverlay.touchTick;
    // we wake, then fade again after the idle delay. snapshotFlow reads the tick
    // outside composition so drags don't recompose the widget.
    var awake by remember { mutableStateOf(true) }
    val awakeNow by rememberUpdatedState(awake)
    val displayModeNow by rememberUpdatedState(displayMode)
    val menuOpenNow by rememberUpdatedState(menuStage != 0 || helpExpanded)
    LaunchedEffect(Unit) {
        snapshotFlow { JetOverlay.touchTick.longValue }.collectLatest {
            awake = true
            delay(if (displayModeNow == 1) EXPANDED_IDLE_MS else IDLE_FADE_MS)
            if (!menuOpenNow) awake = false
        }
    }
    // Menus are separate windows (touches in them never reach the overlay), so wake on
    // open and again on close to restart the idle timer.
    LaunchedEffect(menuStage, helpExpanded) { JetOverlay.wake() }
    val fadeAlpha by animateFloatAsState(
        targetValue = if (awake) 1f else IDLE_ALPHA,
        animationSpec = tween(durationMillis = if (awake) 150 else 900),
        label = "idleFade"
    )
    var peek by remember { mutableStateOf(false) }

    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    val planShort = selectedPlan?.label?.substringBefore(" - ")?.trim().orEmpty()
    val selectorLabel = (if (planShort.isNotBlank()) "$activeRole · $planShort" else activeRole) + " ▾"
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1 else isRunning = false
        }
    }

    // Voice Guide needs the step INDEX (Next / Prev walk the list), so the
    // lookup is index-based. Semantics are unchanged: first step whose window
    // contains the clock, else the last one.
    val currentStepIndex = selectedPlan?.steps?.indexOfFirst {
        elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds
    }?.takeIf { it >= 0 } ?: (selectedPlan?.steps?.lastIndex ?: -1)
    val currentStep = selectedPlan?.steps?.getOrNull(currentStepIndex)
    val hasGuide = selectedPlan != null && currentStep != null

    // ---------------- Voice Guide (floating TTS coach) ----------------
    // Inert unless the service handed us a manager AND the player switched the
    // feature on, so with Voice Guide off the overlay composes exactly what it
    // did before: no extra row, no engine, no audio focus, no download.
    // `voice` is null until the overlay host attaches a manager (and in
    // previews), so the flag is derived from the state alone: no manager means no
    // state means not enabled. Note that voiceOn therefore *proves* both
    // voiceState and voice are non-null, and K2 sees that chain - so the calls
    // guarded by voiceOn below use plain dot access on purpose, they are not
    // unguarded.
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
    val objectiveText = if (objective.isAlive) {
        if (isTurtle) "Turtle ${objective.count} UP" else "Lord UP"
    } else {
        "${objective.label} in ${formatGameTime(objective.secondsUntil)}"
    }
    // V9: muted unless the objective is up or about to be. Gold/red now MEAN something.
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
    // Hidden description only 'peeks' when something actually changes.
    LaunchedEffect(currentStep?.startSeconds, objective.type, objective.count, objective.isAlive, lastSlainAt, activeRole, selectedPlanId) {
        if (elapsedSeconds > 0 || lastSlainAt != null || selectionTouched) {
            peek = true
            JetOverlay.wake()
            delay(PEEK_MS)
            peek = false
        }
    }
    // V13: the step's own objective_contest / objective_trade are written for that moment of the game
    // ("Second Turtle", "First Lord"), so prefer them when the step is about the objective that is up;
    // otherwise fall back to the role-level template. objective_trade was never shown before.
    val stepAboutObjective = currentStep != null && currentStep.phase.contains(objective.label, ignoreCase = true)
    val contestText = (if (stepAboutObjective) currentStep.objectiveContest else null).takeUnless { it.isNullOrBlank() }
        ?: if (isTurtle) selectedPlan?.turtleContest.orEmpty() else selectedPlan?.lordContest.orEmpty()
    val tradeText = (if (stepAboutObjective) currentStep.objectiveTrade else null).takeUnless { it.isNullOrBlank() }
        ?: if (isTurtle) selectedPlan?.turtleTrade.orEmpty() else selectedPlan?.lordTrade.orEmpty()
    val objectiveDescription = if (!hasGuide) "No guide data for $activeRole (${guideAssetFor(activeRole)} missing or invalid). Timer, objective and spells still work." else when {
        objective.isAlive -> contestText
        // FIX V7: slain label = LAST slain, not NEXT. At 7:26 you killed Turtle,
        // next is Lord — old said "Lord slain".
        lastSlainAt != null -> "${if (lastSlainWasLord) "Lord" else "Turtle"} slain. Next: ${objective.label}. Push, farm, or reset." // V9: countdown lives in the pill; no per-second text churn
        else -> currentStep.description
    }
    // V13: hidden mode (≡) peeks the guide's one-line `compact` text instead of the full instruction.
    val peekText = if (objective.isAlive || lastSlainAt != null) objectiveDescription
        else currentStep?.compact.takeUnless { it.isNullOrBlank() } ?: objectiveDescription
    val displayMetrics = context.resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
    val isLandscape = screenWidthDp > screenHeightDp
    // V8 FIX: Adaptive width — displayMetrics (true screen) + NO fillMaxHeight.
    // V6 used fillMaxHeight() in WRAP_CONTENT window -> full-screen tall strip.
    // V7 used LocalConfiguration (window size) -> shrank to ~35% (screenshot).
    // Base: 52% landscape (green line), 92% portrait. If description is long,
    // expand a bit to the RIGHT so it still fits in 2-3 lines, then shrink back.
    // Max: 78% landscape / 95% portrait, so it never runs off-screen.
    val baseFraction = if (isLandscape) WIDGET_WIDTH_FRACTION_LANDSCAPE else WIDGET_WIDTH_FRACTION_PORTRAIT
    val maxFraction = if (isLandscape) 0.78f else 0.95f
    // V9: width never depends on text length (no jumping when the step changes);
    // it only widens when YOU expand to the full view.
    val adaptiveExtra = if (displayMode == 1) 0.16f else 0f
    val widthFraction = (baseFraction + adaptiveExtra).coerceAtMost(maxFraction)
    val widgetWidth = (screenWidthDp * widthFraction)
        .coerceAtLeast(WIDGET_MIN_WIDTH_DP)
        .coerceAtMost(screenWidthDp - 8)
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
            .width(widgetWidth.dp)
            .graphicsLayer { alpha = fadeAlpha }
            // V9: while faded, the first tap only WAKES the widget — the whole
            // gesture is swallowed so a teamfight tap can't hit a button.
            .then(if (FIRST_TAP_ONLY_WAKES) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    var swallowing = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            swallowing = !awakeNow
                        }
                        if (swallowing) {
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) swallowing = false
                        }
                    }
                }
            } else Modifier),
        // No outer horizontal padding: the card itself is widgetWidth,
        // so its visible edges line up exactly with the green line.
        verticalArrangement = Arrangement.spacedBy(onePx)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GuideBackground, RoundedCornerShape(8.dp))
                .border(1.dp, GuideBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // V8 FIX (5): Timer bigger — was 56x24dp / 8sp, too small to read/tap.
                // Now 68x28dp / 11sp. Fixed height, no fillMaxHeight (V6 tall-strip bug).
                // Blue fill = step progress, 35% alpha, text on top. Tap = start/pause.
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(28.dp)
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
                            .height(28.dp)
                            .background(GuideAccent.copy(alpha = 0.25f)) // V12: timer progress 25% to match
                            .align(Alignment.CenterStart)
                    )
                    OutlinedText(
                        text = formatGameTime(elapsedSeconds),
                        color = if (isRunning) GuideText else GuideMuted, // V9: no green
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                // Selector: tap -> role list -> tap a role -> type list -> tap a type.
                // The widget re-targets to <role>_guide.json + that type immediately.
                Box(modifier = Modifier.weight(1f)) {
                    GuideControl(
                        selectorLabel,
                        if (hasGuide) GuideAccent else GuideMuted,
                        Modifier.fillMaxWidth(),
                        maxLines = 1,
                        fontSize = 10.sp,
                        contentDescription = "Change role and type: $activeRole $planShort",
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
                                        OutlinedText(
                                            (if (r == activeRole) "✓ " else "") + r + (if (hasFile) "" else " · no guide"),
                                            color = if (hasFile) GuideText else GuideMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        if (guideFor(r).plans.isEmpty()) {
                                            // Nothing to pick a type from: switch role right away
                                            // (timer / objective / spells still work).
                                            selectionTouched = true
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
                                text = { OutlinedText("‹ $pendingRole · pick type", color = GuideMuted, fontSize = 9.sp, maxLines = 1) },
                                onClick = { menuStage = 1 }
                            )
                            guideFor(pendingRole).plans.forEach { plan ->
                                val isCurrent = pendingRole == activeRole && plan.id == selectedPlan?.id
                                DropdownMenuItem(
                                    text = {
                                        OutlinedText(
                                            (if (isCurrent) "✓ " else "") + plan.label,
                                            color = GuideText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        selectionTouched = true
                                        activeRole = pendingRole
                                        selectedPlanId = plan.id
                                        menuStage = 0
                                    }
                                )
                            }
                        }
                    }
                }
                GuideControl(
                    objectiveText,
                    objectiveColor,
                    background = objectiveColor.copy(alpha = if (objective.isAlive) 0.22f else if (urgent) 0.14f else 0.06f),
                    borderColor = objectiveColor.copy(alpha = if (urgent) 0.65f else 0.30f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
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
                // V8 FIX (5): Icon buttons are 28x28 squares, 14sp — was ~18dp / 8sp, too small.
                // V11: One button cycles 0 (short) -> 1 (full) -> 2 (hidden) -> 0.
                // Label shows CURRENT mode: "+" = short, "−" = full, "≡" = hidden.
                GuideControl(
                    when (displayMode) {
                        0 -> "+"
                        1 -> "−"
                        else -> "≡"
                    },
                    GuideMuted,
                    modifier = Modifier.width(28.dp),
                    fontSize = 14.sp,
                    contentDescription = when (displayMode) {
                        0 -> "Expand to full description"
                        1 -> "Hide description completely"
                        else -> "Back to short description"
                    }
                ) {
                    displayMode = (displayMode + 1) % 3
                }
                // V7: ? help button between + / − and ×. Holds "Tap = used · Hold = change" + other tips.
                Box {
                    GuideControl("?", GuideMuted, modifier = Modifier.width(28.dp), fontSize = 14.sp, onClick = { helpExpanded = true }, contentDescription = "Help and tips")
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
                            text = { OutlinedText("Role/Type: Tap selector, pick role, then type", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { OutlinedText("Mode: + = short · − = full · ≡ = hidden", color = GuideText, fontSize = 8.sp) },
                            onClick = { helpExpanded = false }
                        )
                    }
                }
                GuideControl("×", GuideMuted, modifier = Modifier.width(28.dp), fontSize = 14.sp, onClick = onBack, contentDescription = "Close roam guide")
            }

            if (voiceOn) {
                // Bound to a non-null local so the click lambdas below capture a
                // manager they can call directly: a lambda may run long after this
                // composition, and a captured `voice?.` would silently swallow a
                // tap on a row that is only drawn when a manager exists.
                val coach = voice
                VoiceControlRow(
                    state = voiceState,
                    canStep = hasGuide && spokenSteps.size > 1,
                    onSpeak = { currentSpoken?.let { coach.speak(it) } },
                    onPause = { coach.pause() },
                    onResume = { coach.resume() },
                    onRepeat = { coach.repeat() },
                    onPrev = { coach.speakRelative(spokenSteps, currentStepIndex, -1) },
                    onNext = { coach.speakRelative(spokenSteps, currentStepIndex, 1) },
                    onStop = { coach.stop() },
                    onCancelInstall = { coach.cancelInstall() }
                )
            }

            // V8 FIX (4): Description ALWAYS visible — no hide toggle to trap you.
            // V8 FIX (9): Bare brace block REMOVED — it was parsed as a trailing lambda
            // to the header Row, so everything after it lost @Composable context.
            // Description + details are now direct children of the Column.
                // V7 FIX (3): Call-out REMOVED. Show FULL description directly.
                // Old: "Setup · Guard buff, don't feed — Buy Roam boots..."
                // Now: full turtle/lordContest or step description, e.g.
                // "Go Lord pit early, zone front. Save AoE Ultimate for Lord fight..."
                // V6 tried first-sentence-only + displayMetrics width — that cut the
                // key "Save AoE Ultimate..." part. V7 keeps full text + adaptive width.
                // V11: Mode 2 hides even the short description.
                // Mode 0 = short (2 lines), Mode 1 = full (6 lines) + details.
                val shownDescription = if (displayMode == 2) peekText else objectiveDescription
                if ((displayMode != 2 || peek) && shownDescription.isNotBlank()) {
                    // V9: one fixed size — no font that shrinks/grows with text length.
                    val descFontSize = 9.sp
                    val descLineHeight = 12.sp
                    // Voice Guide: the description is tinted gold while the coach
                    // reads it aloud, so one glance shows which step is being spoken.
                    OutlinedText(
                        text = shownDescription,
                        color = if (coachIsSpeakingThisStep) GuideGold else GuideText,
                        fontSize = descFontSize,
                        lineHeight = descLineHeight,
                        maxLines = if (displayMode == 1) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (displayMode == 1) {
                    if (selectedPlan != null && currentStep != null) {
                    // V13: phase ("First Turtle") was parsed but never shown; flag a guide older than the app's patch.
                    // Compare bare patch numbers (shortVersion): displayVersion can carry a data
                    // stamp ("2.2.23-tier-2026-11-23"), which labelled every current guide as older
                    // in-match. A real mismatch (roam_guide.json 2.2.16a) is still flagged.
                    val livePatch = GameData.patchNotes.shortVersion
                    val patchLabel = guide.patch.substringBefore("-").takeIf(String::isNotBlank)?.let { p ->
                        if (p != livePatch) " · $p (older than $livePatch)" else " · $p"
                    }.orEmpty()
                    OutlinedText(
                        "${currentStep.timeLabel.ifBlank { "${formatGameTime(currentStep.startSeconds)}–${formatGameTime(currentStep.endSeconds)}" }}" +
                            "${currentStep.phase.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}$patchLabel",
                        color = GuideMuted,
                        fontSize = 9.sp
                    )
                    currentStep.checklist.forEach { item ->
                        OutlinedText("• $item", color = GuideText, fontSize = 9.sp)
                    }
                    if (currentStep.vision.isNotBlank()) GuideDetail("Vision", currentStep.vision)
                    if (currentStep.decision.isNotBlank()) GuideDetail("Decision", currentStep.decision)
                    if (currentStep.dont.isNotBlank()) GuideDetail("Avoid", currentStep.dont)
                    if (objective.isAlive && tradeText.isNotBlank()) GuideDetail("If losing", tradeText)
                    if (blessing != null && blessing.effect.isNotBlank() && selectedPlan.blessing.isNotBlank()) {
                        GuideDetail(selectedPlan.blessing, blessing.effect)
                    }
                    if (elapsedSeconds == 0) {
                        // Pre-game only (compact_mode.heroes_note): the loadout the guide recommends.
                        if (selectedPlan.winCondition.isNotBlank()) GuideDetail("Win", selectedPlan.winCondition)
                        if (selectedPlan.loseCondition.isNotBlank()) GuideDetail("Lose", selectedPlan.loseCondition)
                        if (selectedPlan.emblem.isNotBlank()) GuideDetail("Emblem", selectedPlan.emblem)
                        if (selectedPlan.battleSpells.isNotEmpty()) GuideDetail("Spells", selectedPlan.battleSpells.joinToString(" · "))
                        if (selectedPlan.coreItems.isNotEmpty()) GuideDetail("Core items", selectedPlan.coreItems.joinToString(" · "))
                        if (guide.retributions.isNotEmpty()) {
                            GuideDetail("Retribution", guide.retributions.joinToString(" · ") { it.name })
                        }
                        if (blessing != null && blessing.unlock.isNotBlank()) GuideDetail("Unlock", blessing.unlock)
                        if (selectedPlan.heroes.isNotEmpty()) GuideDetail("Suggested heroes", selectedPlan.heroes.take(6).joinToString())
                    } else if (guide.globalRules.isNotEmpty()) {
                        // One rule of thumb per step, rotating with the game phase.
                        val ruleIndex = selectedPlan.steps.indexOf(currentStep).coerceAtLeast(0)
                        GuideDetail("Rule", guide.globalRules[ruleIndex % guide.globalRules.size])
                    }
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
                        // V8: Hide instructions removed — no trap. Description always visible.
                    }
                }
                // V7 FIX (2): Separate 4dp progress bar DELETED — now timer background.
                // No extra bar below description.
        }

        // The spell cooldown panel is its own composable/file so it can be changed
        // independently from the much larger guide UI.
        EnemySpellTrackerWidget(
            elapsedSeconds = elapsedSeconds,
            spells = guide.spells,
            spellDefaults = guide.spellDefaults,
            // Voice Guide: optional spoken "Enemy Flicker used". Off by default
            // in Settings, and the manager allows at most one call per spell per
            // cooldown cycle.
            onSpellUsed = { spellName, cooldownSeconds ->
                if (voiceOn) voice.announceSpell(spellName, cooldownSeconds)
            },
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

private val TrackerSurface = MLBBPlusColors.GithubCard.copy(alpha = 0.25f)
private val TrackerSurfaceCooling = Color(0x402A2413) // V12: 25% like Guide (was 30%)
private val TrackerBorder = MLBBPlusColors.GithubBorder.copy(alpha = 0.25f)
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
 * its parent gives it. [RoamGuideWidget] constrains that parent to the green-line
 * width (~52% of screen in landscape), so each of the 5 cells stays ~1/5 of that.
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
        // V7: ENEMY / "Tap = used · Hold = change" header REMOVED.
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
                            .height(30.dp) // V9: was 38dp
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCoolingDown) TrackerSurfaceCooling else TrackerSurface)
                            .border(
                                1.dp,
                                if (isCoolingDown) TrackerGold.copy(alpha = 0.45f) else TrackerBorder, // V9: softer cooling border
                                RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    // Voice Guide: only the tap that STARTS a
                                    // cooldown means "the enemy just used it";
                                    // tapping again cancels and must stay quiet.
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
                            // V11: Text -> OutlinedText, black outline so lane stays
                            // readable over the game at 30% opacity.
                            OutlinedText(
                                // V9: ready = ONE quiet line ("MID FLK"); cooling adds the countdown below.
                                text = "${laneSpell.lane} ${abbreviationFor(spell.name)}",
                                color = if (isCoolingDown) TrackerGold.copy(alpha = 0.8f) else TrackerMuted,
                                fontSize = if (isCoolingDown) 7.sp else 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            // V11: Text -> OutlinedText, black outline so spell /
                            // cooldown number stays readable over the game.
                            if (isCoolingDown) OutlinedText(
                                text = formatCooldown(remaining),
                                color = TrackerGold.copy(alpha = 0.9f),
                                fontSize = 10.sp,
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
    // V9: ONE Text + soft shadow instead of 9 stacked copies (8-way black outline).
    // Calmer to look at and ~9x fewer text nodes re-laid-out every second.
    Box(
        modifier = modifier,
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        androidx.compose.material3.Text(
            text = text, color = color, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = maxLines, overflow = overflow, textAlign = textAlign,
            lineHeight = lineHeight, letterSpacing = letterSpacing,
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

/**
 * Voice Guide controls, shown only while the feature is enabled.
 *
 * Layout: one 48dp row (the Material minimum touch target - deliberately taller
 * than the 28dp guide controls, because these are used mid-fight without
 * looking) plus a single status line for install progress, errors and hints.
 * The row is composed only when Voice Guide is on, so a widget with the feature
 * off is unchanged down to the last pixel.
 */
@Composable
private fun VoiceControlRow(
    state: VoiceGuideManager.VoiceUiState,
    canStep: Boolean,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onCancelInstall: () -> Unit
) {
    val downloading = state.phase == VoiceGuideManager.Phase.DOWNLOADING
    val speaking = state.phase == VoiceGuideManager.Phase.SPEAKING
    val paused = state.phase == VoiceGuideManager.Phase.PAUSED
    val busy = state.phase == VoiceGuideManager.Phase.PREPARING

    // Label and action come from the shared decision table, so the overlay, the
    // notification and the unit tests all describe the same behaviour.
    val primaryLabel = VoiceGuideTransitions.primaryLabel(state.phase, downloading, busy)
    val primaryAction = VoiceGuideTransitions.primaryAction(state.phase, downloading, busy)
    val primaryDescription = when {
        downloading -> "Cancel the voice model install"
        busy -> "Loading the voice model"
        speaking -> "Pause the coach"
        paused -> "Resume the coach"
        else -> "Speak this step"
    }
    val primaryColor = when {
        downloading || busy -> GuideMuted
        speaking -> GuideGold
        paused -> GuideAccent
        else -> GuideAccent
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            GuideControl(
                primaryLabel,
                primaryColor,
                modifier = Modifier.weight(1.3f).height(VOICE_CONTROL_HEIGHT),
                fontSize = 11.sp,
                contentDescription = primaryDescription,
                onClick = {
                    when (primaryAction) {
                        VoiceGuideTransitions.PrimaryAction.CANCEL_DOWNLOAD -> onCancelInstall()
                        VoiceGuideTransitions.PrimaryAction.PAUSE -> onPause()
                        VoiceGuideTransitions.PrimaryAction.RESUME -> onResume()
                        VoiceGuideTransitions.PrimaryAction.SPEAK -> onSpeak()
                        VoiceGuideTransitions.PrimaryAction.NONE -> Unit
                    }
                }
            )
            GuideControl(
                "Repeat",
                if (speaking || paused) GuideText else GuideMuted,
                modifier = Modifier.weight(1f).height(VOICE_CONTROL_HEIGHT),
                fontSize = 10.sp,
                contentDescription = "Repeat this step",
                onClick = onRepeat
            )
            GuideControl(
                "Prev",
                if (canStep) GuideText else GuideMuted,
                modifier = Modifier.weight(0.8f).height(VOICE_CONTROL_HEIGHT),
                fontSize = 10.sp,
                contentDescription = "Speak the previous step",
                onClick = { if (canStep) onPrev() }
            )
            GuideControl(
                "Next",
                if (canStep) GuideText else GuideMuted,
                modifier = Modifier.weight(0.8f).height(VOICE_CONTROL_HEIGHT),
                fontSize = 10.sp,
                contentDescription = "Speak the next step",
                onClick = { if (canStep) onNext() }
            )
            GuideControl(
                "Stop",
                GuideMuted,
                modifier = Modifier.weight(0.8f).height(VOICE_CONTROL_HEIGHT),
                fontSize = 10.sp,
                contentDescription = "Stop speaking and give audio back to the game",
                onClick = onStop
            )
        }

        // Download progress: a 3dp bar, same visual language as the timer fill.
        if (downloading && state.progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GuideSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(GuideAccent)
                )
            }
        }

        // One status line: progress text, the fallback notice, the TalkBack hint
        // or an error. Blank means the row costs nothing.
        val note = state.progressLabel.ifBlank { state.message }
        if (note.isNotBlank()) {
            OutlinedText(
                text = note,
                color = if (state.phase == VoiceGuideManager.Phase.ERROR) GuideRed else GuideMuted,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 48dp: the Material minimum touch target, so the coach can be driven mid-fight. */
private val VOICE_CONTROL_HEIGHT = 48.dp

/**
 * Reduces one guide step to the fragments the coach may say, most important
 * first. Only prose the player can already read on screen is used: ids, keys and
 * raw JSON never reach this list. [com.mlbb.plus.tts.TtsTextNormalizer] then
 * expands abbreviations and condenses it to 8-18 words, and
 * [com.mlbb.plus.tts.VoiceGuideManager] prefixes the game clock.
 *
 * Missing fields fall through: phase -> callout -> description (which parseStep
 * fills from the JSON "instruction" key first) -> compact, and a step with none
 * of them produces no fragments at all, so the manager says nothing rather than
 * reading a field name aloud.
 */
private fun GuideStep.toSpoken(planId: String, index: Int): SpokenStep = SpokenStep(
    key = "$planId#$index#$startSeconds",
    startSeconds = startSeconds,
    // parseStep() already prefers the JSON "instruction" key and falls back to
    // "description", so `description` here IS the step's instruction. `compact`
    // ("Setup - Guard buff, don't feed") is the last resort for a step that has
    // neither.
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
    onClick: () -> Unit
) {
    // V8 FIX (5): Taller + bigger text. Was 24dp / 8sp / 4dp padding — too cramped
    // to tap in-game (your screenshot). Now 28dp min, 10sp, 6dp/4dp padding.
    // Icon buttons pass fontSize = 14.sp + width(28.dp) for a 28x28 square.
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
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
