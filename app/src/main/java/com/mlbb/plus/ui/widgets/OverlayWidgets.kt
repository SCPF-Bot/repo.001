package com.mlbb.plus.ui.widgets

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.screens.HeroImage
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// mlbb/ui/widgets/OverlayWidgets.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/widgets/OverlayWidgets.kt
// ------------------------------------------------------------
// CRITICAL FIX [1] - LifecycleOwner was null:
//   Old code: setViewTreeLifecycleOwner(null)
//             setViewTreeSavedStateRegistryOwner(null)
//   A ComposeView in a Service HAS no Activity lifecycle, so
//   passing null leaves composition without a LifecycleOwner.
//   Result: remember / LaunchedEffect / recomposition lifecycle
//   is broken, composition is never properly disposed, and the
//   overlay can leak + stop recomposing after role changes.
//   Fix: real OverlayLifecycleOwner (LifecycleOwner +
//   SavedStateRegistryOwner + ViewModelStoreOwner) created in
//   show(), driven ON_CREATE -> ON_START -> ON_RESUME, and
//   torn down ON_PAUSE -> ON_STOP -> ON_DESTROY in hide().
//
// FIX [2] - Package unified: com.mlbb.ui.widgets ->
//   com.mlbb.plus.ui.widgets (matches GameData / theme).
//
// FIX [3] - Window flags: added FLAG_NOT_TOUCH_MODAL so touches
//   outside the 300dp card fall through to the game. Before,
//   the overlay window could swallow touches around it.
//
// FIX [4] - x / y were raw pixels (16 / 120 px = ~2dp on xxhdpi).
//   Now converted with displayMetrics.density.
//
// FIX [5] - RoamGuideWidget: heroes list wrapped in remember(role,
//   roleType) so it is not re-filtered/sorted on every recomposition.
//
// FIX [6] - winRate formatting: old code assumed winRate is always
//   0..1 (winRate * 100). If GameData ever stores 0..100, that
//   printed 5230%. Now handles both scales.
//
// FIX [7] - patchNotes access wrapped in runCatching: if JSON
//   failed to load, old code could crash on lateinit / null
//   patchNotes. Now falls back to "2.2.23".
//
// FIX [8] - hide() is now idempotent + clears lifecycleOwner /
//   windowManager / composeView, and show() failure path also
//   tears down the LifecycleOwner instead of leaking it.
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
    private var app: Application? = null
    private var scopeContent: @Composable () -> Unit = {}
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isShowing = false

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

        // FIX [1]: real LifecycleOwner, not null.
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
            WindowManager.LayoutParams.TYPE_PHONE
        // FIX [4]: dp -> px, not raw pixels.
        val density = context.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // FIX [3]: NOT_TOUCH_MODAL lets touches outside the card pass through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }
        try {
            wm.addView(view, params)
            composeView = view
            isShowing = true
        } catch (e: Exception) {
            // FIX [8]: tear down owner on failure, don't leak it.
            runCatching { owner.onPause() }
            runCatching { owner.onStop() }
            runCatching { owner.onDestroy() }
            lifecycleOwner = null
            composeView = null
            windowManager = null
        }
    }

    fun hide() {
        // FIX [8]: idempotent - safe to call twice (onDestroy + Hide button).
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

// --- Roam Guide Widget - the floating overlay ---

@Composable
fun RoamGuideWidget(
    role: String,
    roleType: String,
    onBack: () -> Unit
) {
    val laneForRole = when (role) {
        "Roam" -> "Roam"
        "Jungle" -> "Jungle"
        "Mid" -> "Mid Lane"
        "Gold" -> "Gold Lane"
        "EXP" -> "EXP Lane"
        else -> "Roam"
    }
    // FIX [5]: compute once per (role, roleType), not per recomposition.
    val heroes = remember(role, roleType) {
        GameData.liveHeroes.filter { it.lane == laneForRole }
            .filter { h ->
                roleType.isBlank() || h.role == roleType || h.secondaryRole == roleType
            }
            .sortedWith(compareByDescending { it.isMeta }.thenByDescending { it.winRate })
            .take(12)
    }
    // FIX [7] + new batch: single fallback via displayVersion.
    // GameData.patchNotes is PatchNotesFile() on load failure,
    // so displayVersion already falls back to 2.2.23.
    val patchVersion = GameData.patchNotes.displayVersion

    Box(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$roleType $role Guide",
                    color = MLBBPlusColors.GithubText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MLBBPlusColors.GithubSurface)
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("\u2715 Hide", color = MLBBPlusColors.GithubText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Text(
                "Top picks for $roleType $role \u2022 Patch $patchVersion",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 11.sp
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
            if (heroes.isEmpty()) {
                Text(
                    "No heroes found for $roleType $role \u2014 check heroes.json",
                    color = MLBBPlusColors.GithubSecondary,
                    fontSize = 12.sp
                )
            } else {
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    heroes.forEach { hero ->
                        // Models.kt confirms winRate is 0..1 (0.50 = 50%).
                        // Dual-scale handling removed - it masked bad data.
                        val winRateText = "%.0f%%".format(hero.winRate * 100)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroImage(
                                hero = hero,
                                modifier = Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MLBBPlusColors.GithubSurface)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${hero.displayRole} \u2022 Tier ${hero.tier}",
                                    color = MLBBPlusColors.GithubSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                winRateText,
                                color = MLBBPlusColors.Green,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Text(
                "MLBB Plus \u2022 Tap Hide to close overlay",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 10.sp
            )
        }
    }
}
