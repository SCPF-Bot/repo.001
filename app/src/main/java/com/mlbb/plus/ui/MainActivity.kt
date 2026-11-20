package com.mlbb.plus.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.screens.*
import com.mlbb.plus.ui.theme.MLBBPlusColors
import com.mlbb.plus.ui.theme.MLBBPlusTheme

// ============================================================
// mlbb/ui/MainActivity.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/MainActivity.kt
// ------------------------------------------------------------
// FIXES (see bottom of file for full changelog):
//  [1] Package unified: com.mlbb.ui -> com.mlbb.plus.ui to match
//      GameData / screens / theme (com.mlbb.plus.*) and the
//      namespace / applicationId in build.gradle.kts (com.mlbb.plus).
//      Old split caused Manifest / R-class confusion.
//  [2] launchWidget: overlay Intent now uses Uri("package:<pkg>")
//      so it opens THIS app's overlay page, not the generic list.
//      Uses ContextCompat.startForegroundService() (required for
//      targetSdk 36 / Android 14+ foreground services).
//  [3] overlayGranted / overlayRunning are now refreshed on
//      ON_RESUME via LifecycleEventObserver, not only on tab change.
//      Before: returning from Settings still showed "not granted"
//      until you switched tabs.
//  [4] Removed duplicate GameData.load() in LaunchedEffect(Unit).
//      load() is called once in onCreate() and is idempotent.
//      Double load = double JSON parse on every recomposition path.
//  [5] SettingsScreen autoStartLastRole was hardcoded false with a
//      no-op callback. Now real state backed by SharedPreferences.
//  [6] Hero -> back navigation: added heroReturnTab so tapping a
//      hero from Home / Items returns to that tab, instead of always
//      dropping to the Heroes list and losing the Item detail.
//  [7] validTypes moved to remember{} so the map is not rebuilt
//      on every recomposition.
//  [8] NEW batch: use MLBBPlusTheme() wrapper from
//      MLBBPlusTheme.kt instead of inline MaterialTheme.
//  [9] NEW batch: onOverlayGrantedChange no longer sets the
//      Boolean directly. SettingsScreen only forwards ON as
//      a request to open system settings. Setting the Boolean
//      here would lie (Home would show READY before the user
//      grants). ON_RESUME observer re-reads the real permission.
// ============================================================

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", AppIcons.Home),
    HEROES("Heroes", AppIcons.Person),
    ITEMS("Items", AppIcons.Apps),
    PATCH("Patch", AppIcons.History),
    SETTINGS("Settings", AppIcons.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load JSONs once - before any screen renders. GameData.load()
        // is idempotent, so calling it here is enough.
        GameData.load(this)
        setContent { MLBBPlusApp(onLaunchWidget = { role, roleType -> launchWidget(role, roleType) }) }
    }

    private fun launchWidget(role: String, roleType: String) {
        if (!Settings.canDrawOverlays(this)) {
            // FIX [2a]: app-specific overlay page, not the generic list.
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        val intent = Intent(this, WidgetOverlayService::class.java).apply {
            putExtra(WidgetOverlayService.EXTRA_ROLE, role)
            putExtra(WidgetOverlayService.EXTRA_ROLE_TYPE, roleType)
        }
        // FIX [2b]: startForegroundService() is required on API 31+
        // when the target is a foreground service.
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
private fun MLBBPlusApp(onLaunchWidget: (String, String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedRole by remember { mutableStateOf("Roam") }
    var selectedRoleType by remember { mutableStateOf("Tank") }
    var selectedHero by remember { mutableStateOf<Hero?>(null) }
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    // FIX [6]: where should Back from HeroDetail go?
    var heroReturnTab by remember { mutableStateOf<Tab?>(null) }

    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var overlayRunning by remember { mutableStateOf(WidgetOverlayService.isRunning) }

    // FIX [3]: refresh overlay state when returning from Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(ctx)
                overlayRunning = WidgetOverlayService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // FIX [5]: real auto-start state, persisted.
    var autoStartLastRole by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("mlbb_plus_prefs", Context.MODE_PRIVATE)
        autoStartLastRole = prefs.getBoolean("auto_start_last_role", false)
    }

    // FIX [7] + new batch: single source of truth.
    // Old: this map duplicated HomeScreen's map. Now both use
    // RoleTypes in Models.kt, so they cannot diverge.
    val validTypes = remember { RoleTypes.byRole }
    LaunchedEffect(selectedRole) {
        val types = validTypes[selectedRole] ?: listOf("Tank")
        if (selectedRoleType !in types) selectedRoleType = types.first()
    }

    // FIX [8]: single theme wrapper, not inline darkColorScheme.
    MLBBPlusTheme {
        Scaffold(
            containerColor = MLBBPlusColors.GithubBg,
            bottomBar = {
                NavigationBar(
                    containerColor = MLBBPlusColors.Header,
                    contentColor = MLBBPlusColors.Gold,
                    tonalElevation = 0.dp
                ) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                tab = t
                                // Tapping the bottom bar is an explicit reset:
                                // clear detail state. Programmatic navigation
                                // (heroReturnTab) does NOT go through here.
                                if (t != Tab.HEROES) {
                                    selectedHero = null
                                    heroReturnTab = null
                                }
                                if (t != Tab.ITEMS) selectedItem = null
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, fontSize = 12.sp, maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = MLBBPlusColors.Gold,
                                unselectedIconColor = MLBBPlusColors.GithubSecondary,
                                unselectedTextColor = MLBBPlusColors.GithubSecondary,
                                indicatorColor = MLBBPlusColors.Gold
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(MLBBPlusColors.GithubBg)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        selectedRole = selectedRole,
                        onRoleChange = { selectedRole = it },
                        selectedRoleType = selectedRoleType,
                        onRoleTypeChange = { selectedRoleType = it },
                        overlayGranted = overlayGranted,
                        overlayRunning = overlayRunning,
                        onLaunchWidget = { onLaunchWidget(selectedRole, selectedRoleType) },
                        onOpenHeroes = { tab = Tab.HEROES },
                        onOpenItems = { tab = Tab.ITEMS },
                        onOpenPatchNotes = { tab = Tab.PATCH },
                        onHeroClick = { heroId ->
                            selectedHero = GameData.heroesById[heroId]
                            heroReturnTab = Tab.HOME
                            tab = Tab.HEROES
                        }
                    )
                    Tab.HEROES -> {
                        val hero = selectedHero
                        if (hero == null) {
                            HeroesScreen(onHeroClick = {
                                selectedHero = it
                                heroReturnTab = Tab.HEROES
                            })
                        } else {
                            HeroDetailScreen(
                                hero = hero,
                                onBack = {
                                    selectedHero = null
                                    // FIX [6]: go back to where we came from.
                                    heroReturnTab?.let { tab = it }
                                    heroReturnTab = null
                                },
                                onHeroClick = { selectedHero = it }
                            )
                        }
                    }
                    Tab.ITEMS -> {
                        val item = selectedItem
                        if (item == null) ItemsScreen(onItemClick = { selectedItem = it })
                        else ItemDetailScreen(
                            item = item,
                            onBack = { selectedItem = null },
                            onItemClick = { selectedItem = it },
                            onHeroClick = {
                                // Keep selectedItem so Back from HeroDetail
                                // can return to this ItemDetail.
                                selectedHero = it
                                heroReturnTab = Tab.ITEMS
                                tab = Tab.HEROES
                            }
                        )
                    }
                    Tab.PATCH -> PatchNotesScreen()
                    Tab.SETTINGS -> SettingsScreen(
                        overlayGranted = overlayGranted,
                        // FIX [9]: do NOT set overlayGranted = true here.
                        // SettingsScreen only calls this with true as a
                        // request to open system settings. The ON_RESUME
                        // observer above re-reads the real permission
                        // when the user returns. Setting it here would
                        // show READY before the user grants.
                        onOverlayGrantedChange = { wanted ->
                            if (wanted) {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${ctx.packageName}")
                                    )
                                )
                            }
                            // OFF is never forwarded by SettingsScreen -
                            // system permission cannot be revoked by a Switch.
                        },
                        autoStartLastRole = autoStartLastRole,
                        onAutoStartChange = { value ->
                            autoStartLastRole = value
                            ctx.getSharedPreferences("mlbb_plus_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_start_last_role", value).apply()
                        }
                    )
                }
            }
        }
    }
}
