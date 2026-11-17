package com.mlbb.assistant.presentation.overlay

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mlbb.assistant.data.local.database.HeroPoolDao
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import com.mlbb.assistant.domain.advisor.BanRecommender
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.engine.Rank
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.capture.FirstPickDetector
import com.mlbb.assistant.capture.PhaseDetector
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that owns all observable overlay UI state and the business logic
 * that drives it (hero loading, session observation, scoring, manual controls).
 *
 * Extracted from [OverlayService] as part of the JetOverlay integration:
 * - [OverlayService] is now a thin foreground-service shell (~200 LOC).
 * - [OverlayCaptureCoordinator] owns the frame-capture + CV pipeline.
 * - This class owns everything between: "what is the current session/score?"
 *   and "what does the overlay Compose tree need to render?"
 *
 * All [androidx.compose.runtime] snapshot state is declared here so that
 * [DraftOverlayContent] observes it directly without any bridging flow.
 *
 * Lifecycle: started by [OverlayService.onCreate] via [start]; stopped via
 * [stop] in [OverlayService.onDestroy]. The underlying [CoroutineScope] is
 * provided by the service (SupervisorJob + Main) and cancelled externally.
 */
@Singleton
class OverlayStateHolder @Inject constructor(
    private val draftSessionManager:  DraftSessionManager,
    private val getHeroesUseCase:     GetHeroesUseCase,
    private val dataStore:            DataStore<Preferences>,
    private val preferencesDataStore: PreferencesDataStore,
    private val heroPoolDao:          HeroPoolDao,
    @param:ApplicationContext private val appContext: Context
) {

    // ── Compose snapshot state (read by DraftOverlayContent) ─────────────────
    val allHeroes       = mutableStateListOf<Hero>()
    val recommendations = mutableStateListOf<HeroScore>()
    val banSuggestions  = mutableStateListOf<BanSuggestion>()
    val enemyWarnings   = mutableStateListOf<String>()
    val isExpanded      = mutableStateOf(false)
    val isRoamGuideExpanded = mutableStateOf(false)
    val isBanTurn       = mutableStateOf(false)

    // ── Self-status banners (todo.md §7) ──────────────────────────────────────
    // Written by OverlayCaptureCoordinator (captureUnavailable) and the
    // accessibility watchdog (accessibilityOff); metaStale is derived locally
    // from GetHeroesUseCase's last-sync timestamp.
    val captureUnavailable = mutableStateOf(false)
    val accessibilityOff   = mutableStateOf(false)
    val metaStaleDays      = mutableStateOf<Int?>(null)

    // ── Scoring configuration (kept in sync with Settings/Room) ──────────────
    @Volatile var currentWeights: ScoreWeights = ScoreWeights.DEFAULT
        private set
    @Volatile var poolMap: Map<Int, Proficiency> = emptyMap()
        private set

    // ── Slot-tracking sets (written by OverlayCaptureCoordinator, read here) ─
    // P0-04 / P0-05: ConcurrentHashMap.newKeySet() because the capture loop
    // runs on Dispatchers.IO / Dispatchers.Default while session resets run
    // on Main. Plain mutableSetOf produces lost updates / CME under that pattern.
    val filledEnemyBanSlots: MutableSet<Int>  = ConcurrentHashMap.newKeySet()
    val filledOurBanSlots:   MutableSet<Int>  = ConcurrentHashMap.newKeySet()
    val filledEnemyPickSlots: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    val filledOurPickSlots:   MutableSet<Int> = ConcurrentHashMap.newKeySet()
    @Volatile var banCatchUpDone: Boolean = false

    // ── DataStore keys (shared with OverlayService for notification extras) ──
    companion object {
        val KEY_BUBBLE_X       = floatPreferencesKey("overlay_bubble_x")
        val KEY_BUBBLE_Y       = floatPreferencesKey("overlay_bubble_y")
        val KEY_SESSION_PHASE  = stringPreferencesKey("session_phase")
        val KEY_SESSION_RANK   = stringPreferencesKey("session_rank")
        val KEY_SESSION_FIRST  = booleanPreferencesKey("session_our_team_first")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Start all observation coroutines using [scope], which is owned and
     * cancelled by [OverlayService]. Call this from [OverlayService.onCreate].
     * [coordinator] is used to pre-load portrait hashes once heroes are loaded.
     */
    fun start(scope: CoroutineScope, coordinator: OverlayCaptureCoordinator) {
        loadHeroes(scope, coordinator)
        observeSession(scope)
        observeScoringConfig(scope)
        scope.launch { restoreSessionSnapshot() }
    }

    fun stop() {
        // Compose snapshot state is discarded with the scope cancellation.
        // No explicit cleanup needed — GC handles the mutableStateListOf fields.
    }

    // ── Hero loading ──────────────────────────────────────────────────────────

    private fun loadHeroes(scope: CoroutineScope, coordinator: OverlayCaptureCoordinator) {
        scope.launch {
            getHeroesUseCase().collectLatest { heroes ->
                // H-02: getHeroesUseCase() is a Room/DataStore-backed flow and is not
                // guaranteed to always emit on Main — wrap the Compose snapshot-state
                // mutations explicitly in Dispatchers.Main.immediate so allHeroes/
                // recommendations writes always land on the thread Compose expects,
                // instead of relying on the caller's ambient dispatcher.
                withContext(Dispatchers.Main.immediate) {
                    allHeroes.clear()
                    allHeroes.addAll(heroes)
                    refreshRecommendations(draftSessionManager.session.value)
                }
                // Preload portrait hashes on IO so the CV pipeline is warm.
                launch(Dispatchers.IO) {
                    runCatching { coordinator.preloadHashes(heroes) }
                }
            }
        }
    }

    // ── Session + scoring config observation ──────────────────────────────────

    private fun observeSession(scope: CoroutineScope) {
        scope.launch {
            draftSessionManager.session.collectLatest { session ->
                // H-02: see loadHeroes() above — same rationale for the
                // recommendations/enemyWarnings/isExpanded snapshot writes.
                withContext(Dispatchers.Main.immediate) {
                    refreshRecommendations(session)
                    if (session.phase == DraftPhase.BAN_ROUND_1 && !isExpanded.value) {
                        isExpanded.value = true
                    }
                }
                // Persist snapshot on IO to avoid blocking the Main dispatcher.
                launch(Dispatchers.IO) { saveSessionSnapshotSuspend(session) }
            }
        }
    }

    private fun observeScoringConfig(scope: CoroutineScope) {
        scope.launch {
            preferencesDataStore.scoreWeightsFlow.collectLatest { weights ->
                withContext(Dispatchers.Main.immediate) {
                    currentWeights = weights
                    refreshRecommendations(draftSessionManager.session.value)
                }
            }
        }
        scope.launch {
            heroPoolDao.getAll().collectLatest { entities ->
                withContext(Dispatchers.Main.immediate) {
                    poolMap = entities.associate { it.heroId to it.toProficiency() }
                    refreshRecommendations(draftSessionManager.session.value)
                }
            }
        }
    }

    // ── Recommendation engine ─────────────────────────────────────────────────

    fun refreshRecommendations(session: DraftSession) {
        val w = currentWeights
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                banSuggestions.clear()
                banSuggestions.addAll(
                    BanRecommender.rank(
                        availableHeroes = allHeroes,
                        bannedIds       = session.allBannedHeroes.map { it.id }.toSet(),
                        pickedIds       = session.allPickedHeroes.map { it.id }.toSet(),
                        weights         = w
                    )
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings)
            }
            DraftPhase.PICK -> {
                recommendations.clear()
                recommendations.addAll(
                    DraftScorer.rankAll(
                        pool        = allHeroes,
                        alliedPicks = session.ourPickedHeroes,
                        enemyPicks  = session.enemyPickedHeroes,
                        bannedIds   = session.unavailableIds,
                        weights     = w,
                        currentTurn = session.currentTurn,
                        poolMap     = poolMap
                    ).take(10)
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings)
            }
            else -> {}
        }
    }

    // ── Slot-tracking reset ───────────────────────────────────────────────────

    fun resetDraftTracking() {
        filledEnemyBanSlots.clear()
        filledOurBanSlots.clear()
        filledEnemyPickSlots.clear()
        filledOurPickSlots.clear()
        banCatchUpDone = false
    }

    // ── Manual overlay controls (called from DraftOverlayContent callbacks) ───

    fun handleManualDraftStart(ourTeamFirst: Boolean) {
        resetDraftTracking()
        draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst = ourTeamFirst)
        draftSessionManager.startBanPhase()
        if (!isExpanded.value) isExpanded.value = true
    }

    fun handleRestartDraft() {
        resetDraftTracking()
        draftSessionManager.reset()
    }

    fun handleManualHeroSelection(hero: Hero) {
        val s = draftSessionManager.session.value
        when (s.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (s.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                val bans  = if (round == 1) s.ourBansR1 else s.ourBansR2
                val slot  = bans.indexOfFirst { it == null }
                if (slot >= 0) draftSessionManager.recordOurBan(hero, round, slot)
            }
            DraftPhase.PICK -> {
                val slot = s.ourPicks.indexOfFirst { it == null }
                if (slot >= 0) {
                    val topId = recommendations.firstOrNull()?.hero?.id
                    draftSessionManager.recordOurPick(hero, slot, hero.id == topId)
                }
            }
            else -> {}
        }
    }

    fun handleScoreDetails() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (intent != null) appContext.startActivity(intent)
    }

    fun undo() = draftSessionManager.undo()

    /**
     * Manually advance to the next draft phase, regardless of CV detection.
     * Intended as a user-triggered recovery when auto-detection misses a transition
     * (e.g. the ban phase ended but the app still shows BAN_ROUND_1).
     *
     * Valid transitions:
     *  BAN_ROUND_1 → BAN_ROUND_2 (if the ban structure has a round 2)
     *  BAN_ROUND_1 → PICK (if no round 2)
     *  BAN_ROUND_2 → PICK
     *  PICK        → TRADING
     *  TRADING     → COMPLETE
     */
    fun handleManualPhaseAdvance() {
        val session = draftSessionManager.session.value
        when (session.phase) {
            DraftPhase.BAN_ROUND_1 -> {
                if (session.banStructure.hasRound2) {
                    draftSessionManager.startBanRound2()
                } else {
                    filledEnemyPickSlots.clear()
                    filledOurPickSlots.clear()
                    draftSessionManager.startPickPhase()
                }
            }
            DraftPhase.BAN_ROUND_2 -> {
                filledEnemyPickSlots.clear()
                filledOurPickSlots.clear()
                draftSessionManager.startPickPhase()
            }
            DraftPhase.PICK    -> draftSessionManager.startTradingPhase()
            DraftPhase.TRADING -> draftSessionManager.completeDraft()
            else               -> {}
        }
    }

    // ── Session snapshot persistence ──────────────────────────────────────────

    private suspend fun saveSessionSnapshotSuspend(session: DraftSession) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_PHASE] = session.phase.name
            prefs[KEY_SESSION_RANK]  = session.rank.name
            prefs[KEY_SESSION_FIRST] = session.ourTeamFirst
        }
    }

    private suspend fun restoreSessionSnapshot() {
        val prefs     = dataStore.data.first()
        val phaseName = prefs[KEY_SESSION_PHASE] ?: return
        val phase     = runCatching { DraftPhase.valueOf(phaseName) }.getOrNull() ?: return
        if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE) return

        val rankName     = prefs[KEY_SESSION_RANK] ?: Rank.UNKNOWN.name
        val rank         = runCatching { Rank.valueOf(rankName) }.getOrElse { Rank.UNKNOWN }
        val ourTeamFirst = prefs[KEY_SESSION_FIRST] ?: true

        // H-02: Dispatchers.Main.immediate avoids an unnecessary dispatch when
        // restoreSessionSnapshot() (called from start(), itself launched on the
        // service's Main-dispatcher scope) is already running on Main — plain
        // Dispatchers.Main always posts to the message queue even when already
        // on the main thread, adding a needless frame of latency here.
        withContext(Dispatchers.Main.immediate) {
            draftSessionManager.initSession(rank, ourTeamFirst = ourTeamFirst)
            when (phase) {
                DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> draftSessionManager.startBanPhase()
                DraftPhase.PICK, DraftPhase.TRADING -> {
                    draftSessionManager.startBanPhase()
                    draftSessionManager.startPickPhase()
                }
                else -> {}
            }
        }
    }

    // ── Phase auto-transition (called by OverlayCaptureCoordinator) ───────────

    /**
     * Called by [OverlayCaptureCoordinator] when OCR explicitly reads
     * "Second Ban Phase" while the session is still in [DraftPhase.BAN_ROUND_1].
     *
     * This is more reliable than slot-count gating because:
     *  1. Round-1 bans may have been missed by the CV pipeline (animation frames,
     *     low luminance, obscured slots).
     *  2. OCR fires on the very first frame that shows "Second Ban Phase", so
     *     the overlay switches mode without waiting for a slot-fill scan to catch up.
     *
     * Guarded upstream by `anyBanRecorded` — at least one R1 ban must have been
     * observed before this is called, preventing a stale OCR result from
     * prematurely advancing on a fresh session.
     */
    fun advanceToBanRound2() {
        if (sessionValue().phase == DraftPhase.BAN_ROUND_1 &&
            sessionValue().banStructure.hasRound2) {
            draftSessionManager.startBanRound2()
        }
    }

    fun autoTransitionPhase(
        detected:        PhaseDetector.DetectedPhase,
        current:         DraftPhase,
        firstPickResult: FirstPickDetector.DetectionResult? = null
    ) {
        when {
            detected == PhaseDetector.DetectedPhase.BAN && current == DraftPhase.IDLE -> {
                val ourTeamFirst = when (firstPickResult?.firstPick) {
                    FirstPickDetector.FirstPick.OUR_TEAM   -> true
                    FirstPickDetector.FirstPick.ENEMY_TEAM -> false
                    else                                   -> true
                }
                resetDraftTracking()
                draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst = ourTeamFirst)
                draftSessionManager.startBanPhase()
            }
            detected == PhaseDetector.DetectedPhase.BAN && current == DraftPhase.BAN_ROUND_1 -> {
                val s    = draftSessionManager.session.value
                val done = s.enemyBansR1.all { it != null } && s.ourBansR1.all { it != null }
                if (done && s.banStructure.hasRound2) draftSessionManager.startBanRound2()
            }
            detected == PhaseDetector.DetectedPhase.PICK &&
            current in setOf(DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2) -> {
                filledEnemyPickSlots.clear()
                filledOurPickSlots.clear()
                draftSessionManager.startPickPhase()
            }
            detected == PhaseDetector.DetectedPhase.TRADING && current == DraftPhase.PICK ->
                draftSessionManager.startTradingPhase()
            detected == PhaseDetector.DetectedPhase.LOADING &&
            current in setOf(DraftPhase.PICK, DraftPhase.TRADING) ->
                draftSessionManager.completeDraft()
        }
    }

    // Expose session flow for the coordinator
    fun sessionValue(): DraftSession = draftSessionManager.session.value

    // ── Self-status banner setters (todo.md §7) ───────────────────────────────

    /** Called by [OverlayCaptureCoordinator] when [com.mlbb.assistant.service.ScreenCaptureManager] reports revocation. */
    fun setCaptureUnavailable(unavailable: Boolean) {
        captureUnavailable.value = unavailable
    }

    /** Called by the accessibility watchdog (see [com.mlbb.assistant.capture.PhaseDetectionConfig.WATCHDOG_INTERVAL_MS]). */
    fun setAccessibilityOff(off: Boolean) {
        accessibilityOff.value = off
    }

    /** Called once hero data has loaded. */
    fun setMetaStaleDays(days: Int?) {
        metaStaleDays.value = days
    }
}
