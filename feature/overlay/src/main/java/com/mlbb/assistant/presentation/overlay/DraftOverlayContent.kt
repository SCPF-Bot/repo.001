package com.mlbb.assistant.presentation.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme

/**
 * Root Composable registered with JetOverlay as the overlay content.
 *
 * JetOverlay owns the [android.view.WindowManager] window, lifecycle, and drag
 * behaviour. This function is responsible only for *what* to render inside that
 * window — either a minimised [FloatingBubble] or the full [MiniWidget].
 *
 * State is read from [OverlayStateHolder], wired via [OverlayContentBridge].
 * The [DraftSessionManager] session is collected as Compose state so recomposition
 * is driven by the same StateFlow the rest of the app observes.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Why [OverlayContentBridge] instead of a CompositionLocal or hiltViewModel()?
 *
 * [JetOverlay.initialize] is called in [com.mlbb.assistant.MLBBApplication.onCreate]
 * before any Hilt component is available. The overlayContent lambda is registered
 * at that point but CALLED only when [JetOverlay.show] runs from
 * [OverlayService.onCreate], where Hilt injection has already completed. The
 * bridge is populated in the gap between injection and show(), so it is always
 * non-null by the time this composable executes.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun DraftOverlayContent() {
    val holder = OverlayContentBridge.holder ?: return
    val dsm    = OverlayContentBridge.draftSessionManager ?: return

    val session by dsm.session.collectAsState()

    MLBBAssistantTheme {
        when {
            holder.isRoamGuideExpanded.value -> {
                RoamGuideWidget(
                    onBack = { holder.isRoamGuideExpanded.value = false }
                )
            }
            holder.isExpanded.value -> {
                MiniWidget(
                    session         = session,
                    recommendations = holder.recommendations.toList(),
                    banSuggestions  = holder.banSuggestions.toList(),
                    isBanTurn       = holder.isBanTurn.value,
                    enemyWarnings   = holder.enemyWarnings.toList(),
                    onMinimize      = { holder.isExpanded.value = false },
                    onClose         = { OverlayContentBridge.stopServiceCallback?.invoke() },
                    onUndo          = { holder.undo() },
                    onScoreDetails  = { holder.handleScoreDetails() },
                    onRestartDraft  = { holder.handleRestartDraft() },
                    onHeroSelected  = { hero -> holder.handleManualHeroSelection(hero) },
                    onStartDraft    = { ourTeamFirst -> holder.handleManualDraftStart(ourTeamFirst) },
                    onNextPhase     = { holder.handleManualPhaseAdvance() },
                    captureUnavailable = holder.captureUnavailable.value,
                    accessibilityOff   = holder.accessibilityOff.value,
                    metaStaleDays      = holder.metaStaleDays.value
                )
            }
            else -> {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    FloatingBubble(
                        session = session,
                        onTap   = { holder.isExpanded.value = true }
                    )
                    RoamGuideBubble(
                        onTap = { holder.isRoamGuideExpanded.value = true }
                    )
                }
            }
        }
    }
}

/**
 * Singleton bridge between Hilt-injected [OverlayService] dependencies and the
 * JetOverlay overlay composable, which is registered before Hilt is available.
 *
 * Set in [OverlayService.onCreate]; cleared in [OverlayService.onDestroy].
 * All fields are [@Volatile] for safe publication across threads.
 */
object OverlayContentBridge {
    @Volatile var holder:              OverlayStateHolder?  = null
    @Volatile var draftSessionManager: DraftSessionManager? = null
    @Volatile var stopServiceCallback: (() -> Unit)?        = null
}
