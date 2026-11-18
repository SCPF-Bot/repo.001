package com.mlbb.assistant.presentation.overlay

// ── Merged: JetOverlay.kt + pointerInput shim ─────────────────────────────────────
// V2: window stays MATCH_PARENT + CENTER_HORIZONTAL, but content is now
// wrapped in a TopCenter Box. Roam is fillMaxWidth(0.52f) inside that Box,
// so it is exactly 52% and centered on the green line, not stretched.
// DELETED: pointerInput shim file (11 lines). Merged here — it was only used by the
// overlay package and only JetOverlay needs low-level pointer handling.

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput as composePointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.platform.ComposeView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Local shim replacing the unresolvable com.github.YazanAesmael:JetOverlay JitPack artifact.
 *
 * Implements the same API surface used by [MLBBApplication] and [OverlayService]:
 *   - [initialize] — registers the overlay composable once at app startup.
 *   - [show]       — inflates the composable into a TYPE_APPLICATION_OVERLAY window.
 *   - [hide]       — removes the window.
 *
 * Drag-to-move is provided via a simple touch listener on the root view so the
 * bubble remains repositionable without the external library.
 */
object JetOverlay {

    // M-09 fix: these fields are written under the @Synchronized show()/hide()
    // lock, but read from other call sites (e.g. the touch listener's
    // ACTION_MOVE branch runs on the UI thread and reads `windowManager`
    // outside that lock). @Volatile guarantees a reader on another thread
    // sees the latest write instead of a stale/cached value — synchronized
    // blocks alone only guarantee ordering between threads that both
    // synchronize on the same monitor.
    @Volatile private var application: Application? = null
    @Volatile private var overlayContent: (@Composable () -> Unit)? = null

    @Volatile private var windowManager: WindowManager? = null
    @Volatile private var composeView: ComposeView? = null
    @Volatile private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun initialize(app: Application, block: JetOverlayScope.() -> Unit) {
        application = app
        JetOverlayScope().also(block)
    }

    internal fun setContent(content: @Composable () -> Unit) {
        overlayContent = content
    }

    // P1 fix: synchronize show() and hide() to prevent a race between the
    // MLBBAccessibilityService path and the MainActivity path both calling
    // OverlayService.start() near-simultaneously. Without the lock, two threads
    // can both pass the `composeView != null` guard and then both call
    // WindowManager.addView(), causing IllegalStateException: "View already added".
    @Synchronized
    fun show() {
        val app = application ?: return
        val content = overlayContent ?: return
        if (composeView != null) return

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // V7 FIX: Window is WRAP_CONTENT (hugs widget) + NOT_TOUCH_MODAL.
        // V6 bug combo: MATCH_PARENT window + fillMaxHeight() in timer made the
        // window go full-screen tall (your tall vertical strip) and touches
        // outside the widget were swallowed. WRAP_CONTENT + NOT_TOUCH_MODAL
        // means: window = exactly widget size, taps outside pass to game.
        // Widget width itself is set in RoamGuideWidget (52% landscape / 92%
        // portrait, + adaptive extra). Window just centers it.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100
        }

        val owner = OverlayLifecycleOwner().also { lifecycleOwner = it }
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val view = ComposeView(app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            // V7: Window is WRAP_CONTENT, so content must NOT be fillMaxWidth —
            // that would stretch the window back to full screen width.
            // Just call content() — RoamGuideWidget sets its own width.
            setContent { content() }
        }

        // V7: Drag-to-move REMOVED for the guide. It moved the whole guide
        // window on ACTION_MOVE (your screenshot 1: widget in the middle of
        // the screen) and on a WRAP_CONTENT window it fights the centering.
        // Guide stays pinned at TOP|CENTER_HORIZONTAL, y=100. If you want
        // dragging later, add a dedicated drag handle inside RoamGuideWidget,
        // not a window-level touch listener.
        composeView = view
        runCatching {
            wm.addView(view, params)
        }.onFailure { e ->
            // Typically WindowManager.BadTokenException when SYSTEM_ALERT_WINDOW
            // permission is not yet granted.  Reset state so a subsequent show()
            // call (after permission is granted) can retry cleanly.
            android.util.Log.e("JetOverlay", "addView failed — overlay permission may be missing", e)
            composeView = null
            windowManager = null
            lifecycleOwner = null
        }
    }

    /** Screen width in px, using the modern WindowMetrics API on R+ and falling back to the deprecated Display API below it. */
    private fun screenWidthPx(app: Application, wm: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = wm.currentWindowMetrics
            metrics.bounds.width()
        } else {
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    @Synchronized
    fun hide() {
        val view = composeView ?: return
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        runCatching { windowManager?.removeView(view) }
        composeView = null
        windowManager = null
        lifecycleOwner = null
    }
}

class JetOverlayScope {
    fun overlayContent(content: @Composable () -> Unit) {
        JetOverlay.setContent(content)
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
    fun performRestore(savedState: Bundle?) = savedStateRegistryController.performRestore(savedState)
}

/**
 * Merged from pointerInput shim (was its own 11-line file).
 * Exposes Compose's pointer input modifier without changing widget behavior.
 * Kept in JetOverlay.kt because the overlay window is the only place that
 * needs low-level pointer handling — no other file imports it.
 */
internal fun Modifier.pointerInput(
    key: Any?,
    block: suspend PointerInputScope.() -> Unit
): Modifier = this.composePointerInput(key, block)
