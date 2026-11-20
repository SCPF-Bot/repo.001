package com.mlbb.plus.ui.widgets

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
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
// Split out of OverlayWidgets.kt so the engine and the widget UI live in the
// two files docs/overview.md already describes.
//
// Everything from the previous engine is kept (real LifecycleOwner, overlay-
// permission check, idempotent hide(), failure teardown). Added for the
// low-distraction widget:
//  [1] Window is WRAP_CONTENT, TOP | CENTER_HORIZONTAL (the widget centres
//      itself on screen and sets its own width), NOT_TOUCH_MODAL so touches
//      outside the widget reach the game. (Was TOP | END, pinned, no drag.)
//  [2] Drag-to-move with the system touch slop (ViewConfiguration), no fling.
//      A tap stays a tap; only a real drag moves the window.
//  [3] Dropped position is saved and restored (clamped to the screen).
//  [4] touchTick / wake(): every touch bumps a Compose state so the widget can
//      un-fade from its idle fade; the widget also calls wake() for alerts.
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
    private var composeView: ComposeView? = null
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

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent { scopeContent() }
        }
        owner.onStart()
        owner.onResume()

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

        // Drag with tap threshold, no fling. DOWN records the start; a MOVE past the
        // system touch slop becomes a drag (window follows the finger, event consumed);
        // a tap is NOT consumed so the Compose buttons still receive it.
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchTick.longValue += 1
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        touchTick.longValue += 1
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchTick.longValue += 1
                    val wasDragging = isDragging
                    isDragging = false
                    if (wasDragging) {
                        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                    }
                    wasDragging
                }
                else -> false
            }
        }

        try {
            wm.addView(view, params)
            composeView = view
            isShowing = true
        } catch (e: Exception) {
            // Tear down owner on failure, don't leak it.
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
            lifecycleOwner = null
            composeView = null
            windowManager = null
        }
    }

    fun hide() {
        // Idempotent - safe to call twice (onDestroy + Hide button).
        if (!isShowing && composeView == null) return
        try {
            composeView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { }
        composeView = null
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
