package com.mlbb.plus.ui.widgets

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
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

// ============================================================
// widgets/JetOverlay.kt — generic overlay engine (WindowManager + ComposeView)
//
//  [1] Window is WRAP_CONTENT, TOP | CENTER_HORIZONTAL (the widget centres itself and
//      sets its own width), NOT_TOUCH_MODAL so touches outside it reach the game.
//  [2] DRAG (fixed for good): the ComposeView sits inside a small FrameLayout that
//      INTERCEPTS the gesture once the finger passes the system touch slop. The old
//      approach (OnTouchListener on the ComposeView itself) never fires when Compose
//      claims the touch — and Compose claims every touch over any clickable or
//      pointerInput modifier, i.e. almost the whole widget. Intercepting from a parent
//      works over buttons and spell cells too; a plain tap still reaches them, and a
//      drag cancels the press so nothing fires by accident. No fling.
//  [3] The dropped position is saved and restored (clamped to the screen).
//  [4] touchTick / wake(): every touch bumps a Compose state so the widget can wake
//      from its idle fade; the widget also calls wake() for alerts.
// ============================================================

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

// --- JetOverlay: WindowManager + ComposeView ---

class JetOverlayScope {
    var content: @Composable () -> Unit = {}
    fun overlayContent(content: @Composable () -> Unit) {
        this.content = content
    }
}

object JetOverlay {
    private const val PREFS = "jet_overlay_prefs"
    private const val KEY_X = "overlay_x"
    private const val KEY_Y = "overlay_y"
    private const val DEFAULT_Y_DP = 36

    private var app: Application? = null
    private var scopeContent: @Composable () -> Unit = {}
    private var overlayRoot: View? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isShowing = false

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
        // Must have overlay permission - MainActivity already checks this
        // before starting the service. If not granted, do nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // Real LifecycleOwner, not null.
        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        owner.onCreate()

        val composeView = ComposeView(context).apply {
            setContent { scopeContent() }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val dm = context.resources.displayMetrics
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            x = prefs.getInt(KEY_X, 0).coerceIn(-dm.widthPixels / 2, dm.widthPixels / 2)
            y = prefs.getInt(KEY_Y, (DEFAULT_Y_DP * dm.density).toInt())
                .coerceIn(0, dm.heightPixels / 2)
        }

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
                    prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
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

            // Receives the gesture after we intercepted it, or when nothing underneath
            // (empty padding / background) wanted it.
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> beginTouch(event)
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDragging && passedSlop(event)) isDragging = true
                        if (isDragging) moveWindow(event)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endTouch()
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
            // Tear down owner on failure, don't leak it.
            // addView throws when the overlay permission was revoked mid-flight or the
            // window type is rejected; log it so a "guide never appeared" report is
            // diagnosable (detekt SwallowedException, 2026-11-27 lint scan). Behavior
            // is unchanged: the owner is still torn down and no window is shown.
            android.util.Log.w("JetOverlay", "addView failed, overlay not shown: ${e.message}")
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
            lifecycleOwner = null
            overlayRoot = null
            windowManager = null
        }
    }

    fun hide() {
        // Idempotent - safe to call twice (onDestroy + Hide button).
        if (!isShowing && overlayRoot == null) return
        try {
            overlayRoot?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            // removeView throws if the window is already gone (double hide, or the system
            // tore it down). Swallowing is correct — hide() must stay idempotent — but it
            // was an empty block (detekt EmptyCatchBlock), which made "the overlay will not
            // disappear" undiagnosable. Log only; behavior unchanged.
            android.util.Log.w("JetOverlay", "removeView failed during hide(): ${e.message}")
        }
        overlayRoot = null
        windowManager = null
        isShowing = false
        lifecycleOwner?.let { owner ->
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
        }
        lifecycleOwner = null
    }
}
