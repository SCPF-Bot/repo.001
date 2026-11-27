package com.mlbb.plus.ui.widgets

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Generic floating-window host: a WindowManager overlay with a ComposeView inside.
 *
 * - The window is WRAP_CONTENT, anchored TOP | CENTER_HORIZONTAL. The content centres
 *   itself and sets its own width. NOT_TOUCH_MODAL lets touches outside it reach the game.
 * - Dragging: the ComposeView sits inside a small FrameLayout that intercepts the gesture
 *   once the finger passes the system touch slop. Compose claims touches over any
 *   clickable or pointerInput, so an OnTouchListener on the ComposeView never fires.
 *   A plain tap still reaches the controls, and a drag cancels the press. There is no fling.
 * - The dropped position is saved and restored, clamped to the screen.
 * - [touchTick] / [wake]: every touch bumps a Compose state so content can wake from its
 *   idle fade. Content also calls wake() for alerts.
 */

// --- Service LifecycleOwner for ComposeView in a Service ---

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun onStart() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
    fun onResume() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun onPause() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
    fun onStop() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/**
 * Clamps a saved bubble position to the current screen, in pixels. [x] is the offset from the
 * horizontal centre and [y] the offset from the top. Used on show() and after every rotation,
 * so a position saved in portrait cannot leave the bubble off-screen in landscape.
 */
internal fun clampedOffset(x: Int, y: Int, screenWidthPx: Int, screenHeightPx: Int): Pair<Int, Int> =
    x.coerceIn(-screenWidthPx / 2, screenWidthPx / 2) to y.coerceIn(0, screenHeightPx / 2)

// --- JetOverlay: WindowManager + ComposeView ---

class JetOverlayScope {
    var content: @Composable () -> Unit = {}
    fun overlayContent(content: @Composable () -> Unit) {
        this.content = content
    }
}

// StaticFieldLeak is suppressed deliberately: the View and WindowManager here are created from
// the Application context (never an Activity) and cleared in hide(), so nothing outlives the
// overlay.
@SuppressLint("StaticFieldLeak")
object JetOverlay {
    private const val TAG = "JetOverlay"

    private const val PREFS = "jet_overlay_prefs"
    private const val KEY_X = "overlay_x"
    private const val KEY_Y = "overlay_y"
    private const val DEFAULT_Y_DP = 36

    private var app: Application? = null
    private var scopeContent: @Composable () -> Unit = {}
    private var overlayRoot: View? = null
    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isShowing = false

    /**
     * True in landscape. The floating guide opens only in landscape, so the content reads this to
     * choose between the bubble and the guide. Set in show() and updated on rotation.
     */
    var isLandscape by mutableStateOf(false)
        private set

    // Bumped on every touch of the overlay window (and by wake()). The widget
    // observes it with snapshotFlow to wake from its idle fade.
    internal val touchTick = mutableLongStateOf(0L)

    /** Wake the widget from its idle fade (used for objective alerts). */
    internal fun wake() {
        touchTick.longValue += 1
    }

    fun initialize(app: Application, block: JetOverlayScope.() -> Unit) {
        this.app = app
        val scope = JetOverlayScope().apply(block)
        scopeContent = scope.content
    }

    fun show() {
        val context = app ?: return
        if (isShowing) return
        // MainActivity checks the permission before starting the service. If it was revoked
        // since then, log why nothing appeared rather than failing silently.
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "overlay permission missing, overlay not shown")
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // Real LifecycleOwner, not null.
        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        owner.onCreate()

        val composeView = ComposeView(context).apply {
            setContent { scopeContent() }
        }

        // minSdk is 29, so TYPE_APPLICATION_OVERLAY (API 26+) is always available.
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val dm = context.resources.displayMetrics
        val prefs = ScreenProfiles.prefs(context)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // Automatic: this launch's screen size is recorded for its orientation.
        ScreenProfiles.record(prefs, dm.widthPixels, dm.heightPixels, landscape)
        isLandscape = landscape
        val (startX, startY) = restoredBubble(context, prefs, landscape, dm.widthPixels, dm.heightPixels)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_TOUCH_MODAL: touches outside the widget pass through to the game.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // x = offset from screen centre, y = offset from top (px).
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = startX
            y = startY
        }
        windowParams = params

        // ── Drag: a parent FrameLayout that takes over the gesture past the touch slop ──
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        val root = object : FrameLayout(context) {
            private fun beginTouch(ev: MotionEvent) {
                touchTick.longValue += 1
                initialX = params.x
                initialY = params.y
                downRawX = ev.rawX
                downRawY = ev.rawY
                isDragging = false
            }

            private fun passedSlop(ev: MotionEvent): Boolean =
                kotlin.math.abs(ev.rawX - downRawX) > touchSlop ||
                    kotlin.math.abs(ev.rawY - downRawY) > touchSlop

            private fun endTouch() {
                touchTick.longValue += 1
                if (isDragging) {
                    // Saved under the orientation the drag happened in.
                    val screen = context.resources.displayMetrics
                    ScreenProfiles.saveBubble(
                        prefs, isLandscape, params.x, params.y, screen.widthPixels, screen.heightPixels
                    )
                }
                isDragging = false
            }

            private fun moveWindow(ev: MotionEvent) {
                params.x = initialX + (ev.rawX - downRawX).toInt()
                params.y = initialY + (ev.rawY - downRawY).toInt()
                runCatching { wm.updateViewLayout(this, params) }
            }

            // Called for every event while a child (Compose) is handling the gesture.
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> beginTouch(ev)
                    MotionEvent.ACTION_MOVE -> if (!isDragging && passedSlop(ev)) isDragging = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!isDragging) endTouch()
                }
                // true = steal the gesture: the child gets ACTION_CANCEL (press is dropped,
                // nothing fires) and the rest of the drag arrives in onTouchEvent.
                return isDragging
            }

            // Declared so the click contract is explicit; taps are reported from onTouchEvent.
            override fun performClick(): Boolean = super.performClick()

            // Receives the gesture after we intercepted it, or when nothing underneath
            // (empty padding / background) wanted it.
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> beginTouch(event)
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDragging && passedSlop(event)) isDragging = true
                        if (isDragging) moveWindow(event)
                    }
                    MotionEvent.ACTION_UP -> {
                        // A tap that was not dragged is a click, reported for accessibility services.
                        if (!isDragging) performClick()
                        endTouch()
                    }
                    MotionEvent.ACTION_CANCEL -> endTouch()
                }
                return true
            }
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Compose finds its owners from the ROOT view of the window, so set them on both.
        listOf<View>(root, composeView).forEach { v ->
            v.setViewTreeLifecycleOwner(owner)
            v.setViewTreeSavedStateRegistryOwner(owner)
            v.setViewTreeViewModelStoreOwner(owner)
        }
        owner.onStart()
        owner.onResume()

        try {
            wm.addView(root, params)
            overlayRoot = root
            isShowing = true
        } catch (e: Exception) {
            // addView throws when the overlay permission was revoked mid-flight or the window
            // type is rejected. Log it, then tear the owner down so nothing leaks.
            Log.w(TAG, "addView failed, overlay not shown: ${e.message}")
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
            lifecycleOwner = null
            overlayRoot = null
            windowManager = null
            windowParams = null
        }
    }

    /**
     * Called from the service on every rotation. Records the new orientation's screen size,
     * switches the content (bubble or guide), and moves the bubble to that orientation's own
     * saved spot. If that orientation has no saved spot yet, the bubble stays where it is.
     */
    fun onConfigurationChanged(newConfig: Configuration) {
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        isLandscape = landscape
        val context = app ?: return
        val dm = context.resources.displayMetrics
        val prefs = ScreenProfiles.prefs(context)
        ScreenProfiles.record(prefs, dm.widthPixels, dm.heightPixels, landscape)

        val root = overlayRoot ?: return
        val params = windowParams ?: return
        val wm = windowManager ?: return
        val saved = ScreenProfiles.savedBubble(prefs, landscape, dm.widthPixels, dm.heightPixels)
        val (x, y) = if (saved != null) {
            clampedOffset(saved.first, saved.second, dm.widthPixels, dm.heightPixels)
        } else {
            // First time in this orientation: keep the bubble where the user left it.
            clampedOffset(params.x, params.y, dm.widthPixels, dm.heightPixels).also { (cx, cy) ->
                ScreenProfiles.saveBubble(prefs, landscape, cx, cy, dm.widthPixels, dm.heightPixels)
            }
        }
        if (x == params.x && y == params.y) return
        params.x = x
        params.y = y
        runCatching { wm.updateViewLayout(root, params) }
    }

    /**
     * Bubble position for [landscape]: its own saved spot if there is one, otherwise the single
     * position saved before per-orientation positions existed, otherwise the default.
     */
    private fun restoredBubble(
        context: Context,
        prefs: SharedPreferences,
        landscape: Boolean,
        widthPx: Int,
        heightPx: Int
    ): Pair<Int, Int> {
        ScreenProfiles.savedBubble(prefs, landscape, widthPx, heightPx)?.let { (x, y) ->
            return clampedOffset(x, y, widthPx, heightPx)
        }
        val legacy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaultY = (DEFAULT_Y_DP * context.resources.displayMetrics.density).toInt()
        return clampedOffset(legacy.getInt(KEY_X, 0), legacy.getInt(KEY_Y, defaultY), widthPx, heightPx)
    }

    fun hide() {
        // Idempotent - safe to call twice (onDestroy + Hide button).
        if (!isShowing && overlayRoot == null) return
        try {
            overlayRoot?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            // removeView throws if the window is already gone (double hide, or the system tore
            // it down). Swallowing is correct because hide() must stay idempotent. Log it.
            Log.w(TAG, "removeView failed during hide(): ${e.message}")
        }
        overlayRoot = null
        windowManager = null
        windowParams = null
        isShowing = false
        lifecycleOwner?.let { owner ->
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
        }
        lifecycleOwner = null
    }
}
