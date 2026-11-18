package com.mlbb.assistant.presentation.overlay

// ── Merged: JetOverlay.kt + pointerInput shim ─────────────────────────────────────
// V3: window is WRAP_CONTENT + CENTER_HORIZONTAL + FLAG_NOT_TOUCH_MODAL.
// Window hugs the 52% widget, so green areas beside it are outside the
// window and fall through to MLBB. Roam defines width as absolute
// displayMetrics * 0.52f (not fillMaxWidth(0.52f)).
// DELETED: pointerInput shim file (11 lines). Merged here — it was only used by the
// overlay package and only JetOverlay needs low-level pointer handling.

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

    private const val FLING_MIN_VELOCITY_PX_PER_SEC = 800f

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

        // FIX V3 (touch passthrough): MATCH_PARENT made the window cover the
        // whole top bar. Even transparent sides ate taps — MLBB never got them.
        // Window is now WRAP_CONTENT wide, so it hugs the 52% widget. The green
        // areas beside the widget are OUTSIDE the window, so with
        // FLAG_NOT_TOUCH_MODAL they fall through to MLBB.
        // Gravity CENTER_HORIZONTAL still centers the WRAP_CONTENT window.
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
            // FIX V2: window is MATCH_PARENT, so content() must be centered.
            // Without this Box, a 52% widget would stick to the left edge and
            // look like it spans the full width (empty space on the right).
            setContent {
                // FIX V3: NO fillMaxWidth() here. Inside a WRAP_CONTENT window,
                // fillMaxWidth() would stretch the window back to full width
                // and block the green areas again. Plain Box hugs the widget.
                Box(
                    modifier = Modifier,
                    contentAlignment = Alignment.TopCenter
                ) { content() }
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        // UX-07: fling-to-edge. A VelocityTracker accumulates ACTION_MOVE
        // samples between ACTION_DOWN and ACTION_UP; on release, a fast
        // horizontal fling snaps the bubble to the nearest screen edge in the
        // direction of the fling instead of leaving it wherever the finger
        // happened to lift, matching the "docked bubble" behavior users expect
        // from floating overlays (e.g. Messenger chat heads).
        var velocityTracker: VelocityTracker? = null

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // FIX: fling-to-edge snapped the whole GUIDE to the screen edge.
                    // That's for the bubble only, not the guide. Guide stays where dragged.
                    velocityTracker?.recycle()
                    velocityTracker = null
                    false
                }
                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    false
                }
                else -> false
            }
        }

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
