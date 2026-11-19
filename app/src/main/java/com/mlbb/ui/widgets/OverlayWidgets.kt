package com.mlbb.ui.widgets

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.screens.HeroImage
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// mlbb/ui/widgets/OverlayWidgets.kt
// GitHub path: app/src/main/java/com/mlbb/ui/widgets/OverlayWidgets.kt
// ------------------------------------------------------------
// FIX for build error:
//   WidgetOverlayService.kt:59-60
//   "No parameter with name 'role' / 'roleType' found"
// Root cause: old RoamGuideWidget on GitHub did NOT declare
// role / roleType params, but WidgetOverlayService calls:
//   RoamGuideWidget(role = ..., roleType = ..., onBack = ...)
// This file now declares EXACTLY that signature:
//   @Composable fun RoamGuideWidget(role: String, roleType: String, onBack: () -> Unit)
//
// JetOverlay API matches WidgetOverlayService usage:
//   JetOverlay.initialize(application) { overlayContent { RoamGuideWidget(...) } }
//   JetOverlay.show()
//   JetOverlay.hide()
//
// IF YOU HAVE a separate JetOverlay.kt or FloatingWidget.kt in
// mlbb/ui/widgets/ — DELETE it. JetOverlay now lives ONLY here,
// otherwise you get "Duplicate class JetOverlay".
// ============================================================

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
    private var isShowing = false

    fun initialize(app: Application, block: JetOverlayScope.() -> Unit) {
        this.app = app
        val scope = JetOverlayScope().apply(block)
        scopeContent = scope.content
    }

    fun show() {
        val context = app ?: return
        if (isShowing) return
        // Must have overlay permission — MainActivity already checks this
        // before starting the service. If not granted, do nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = ComposeView(context).apply {
            // Required for Compose inside a Service window
            setViewTreeLifecycleOwner(null)
            setViewTreeSavedStateRegistryOwner(null)
            setContent { scopeContent() }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            x = 16
            y = 120
        }
        try {
            wm.addView(view, params)
            composeView = view
            isShowing = true
        } catch (e: Exception) {
            // Overlay already added / permission revoked — don't crash service
        }
    }

    fun hide() {
        try {
            composeView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { }
        composeView = null
        isShowing = false
    }
}

// --- Roam Guide Widget — the floating overlay ---

@Composable
fun RoamGuideWidget(
    role: String,
    roleType: String,
    onBack: () -> Unit
) {
    // Heroes for this role — from heroes.json via GameData
    // Roam -> lane == "Roam", Jungle -> lane == "Jungle",
    // Mid -> "Mid Lane", Gold -> "Gold Lane", EXP -> "EXP Lane"
    val laneForRole = when (role) {
        "Roam" -> "Roam"
        "Jungle" -> "Jungle"
        "Mid" -> "Mid Lane"
        "Gold" -> "Gold Lane"
        "EXP" -> "EXP Lane"
        else -> "Roam"
    }
    val heroes = GameData.liveHeroes.filter { it.lane == laneForRole }
        .filter { h ->
            // Role Type filter: Tank / Support / Assassin / etc.
            // Matches role OR secondaryRole, e.g. Marcel is Support / Tank
            roleType.isBlank() || h.role == roleType || h.secondaryRole == roleType
        }
        .sortedWith(compareByDescending { it.isMeta }.thenByDescending { it.winRate })
        .take(12)

    Box(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
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
                ) { Text("✕ Hide", color = MLBBPlusColors.GithubText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Text(
                "Top picks for $roleType $role • Patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }}",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 11.sp
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
            // Hero list — HeroImage has fallback (initial letter) if .webp missing
            if (heroes.isEmpty()) {
                Text(
                    "No heroes found for $roleType $role — check heroes.json",
                    color = MLBBPlusColors.GithubSecondary,
                    fontSize = 12.sp
                )
            } else {
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    heroes.forEach { hero ->
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
                                    "${hero.displayRole} • Tier ${hero.tier}",
                                    color = MLBBPlusColors.GithubSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                "%.0f%%".format(hero.winRate * 100),
                                color = MLBBPlusColors.Green,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Text(
                "MLBB Plus • Tap Hide to close overlay",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 10.sp
            )
        }
    }
}
