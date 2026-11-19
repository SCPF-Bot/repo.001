package com.mlbb.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.screens.*
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// mlbb/ui/MainActivity.kt
// GitHub path: app/src/main/java/com/mlbb/ui/MainActivity.kt
// ------------------------------------------------------------
// UPDATED FOR NEW ASSETS:
//   assets/heroes.json      -> 134 heroes (Hanabi=60, Marcel=132, Dori=134 Advanced-only)
//   assets/items.json       -> 40 items (Demon Hunter Sword=1002)
//   assets/patch_notes.json -> 2.2.23 / 2.2.32 / Nov 01 2.2.30
//   assets/images/heroes/   -> hero portraits: images/heroes/{id}.webp
//   assets/images/items/    -> item portraits: images/items/{id}.webp
// GameData.load() reads all 3 JSONs ONCE here. Screens use GameData.
// ============================================================

private enum class Tab(val label: String, val emoji: String) {
    HOME("Home", "🏠"),
    HEROES("Heroes", "👤"),
    ITEMS("Items", "🎒"),
    PATCH("Patch Notes", "📄"),
    SETTINGS("Settings", "⚙️")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load JSONs once — before any screen renders
        GameData.load(this)
        setContent { MLBBPlusApp(onLaunchWidget = { role, roleType -> launchWidget(role, roleType) }) }
    }

    private fun launchWidget(role: String, roleType: String) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return
        }
        val intent = Intent(this, WidgetOverlayService::class.java).apply {
            putExtra(WidgetOverlayService.EXTRA_ROLE, role)
            putExtra(WidgetOverlayService.EXTRA_ROLE_TYPE, roleType)
        }
        startService(intent)
    }
}

@Composable
private fun MLBBPlusApp(onLaunchWidget: (String, String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedRole by remember { mutableStateOf("Roam") }
    var selectedRoleType by remember { mutableStateOf("Tank") }
    var selectedHero by remember { mutableStateOf<Hero?>(null) }
    var selectedItem by remember { mutableStateOf<Item?>(null) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { GameData.load(ctx) }

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var overlayRunning by remember { mutableStateOf(WidgetOverlayService.isRunning) }
    LaunchedEffect(tab) {
        overlayGranted = Settings.canDrawOverlays(ctx)
        overlayRunning = WidgetOverlayService.isRunning
    }

    val validTypes = mapOf(
        "Roam" to listOf("Tank", "Support"),
        "Jungle" to listOf("Assassin", "Fighter", "Tank"),
        "Mid" to listOf("Burst", "Poke", "Control"),
        "Gold" to listOf("Marksman", "Mage"),
        "EXP" to listOf("Fighter", "Tank", "Assassin")
    )
    LaunchedEffect(selectedRole) {
        val types = validTypes[selectedRole] ?: listOf("Tank")
        if (selectedRoleType !in types) selectedRoleType = types.first()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = MLBBPlusColors.Gold, background = MLBBPlusColors.GithubBg, surface = MLBBPlusColors.GithubCard)) {
        Scaffold(
            containerColor = MLBBPlusColors.GithubBg,
            bottomBar = {
                NavigationBar(containerColor = MLBBPlusColors.GithubCard, contentColor = MLBBPlusColors.Gold) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                tab = t
                                if (t != Tab.HEROES) selectedHero = null
                                if (t != Tab.ITEMS) selectedItem = null
                            },
                            icon = { Text(t.emoji, fontSize = 20.sp) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MLBBPlusColors.Gold,
                                selectedTextColor = MLBBPlusColors.Gold,
                                unselectedIconColor = MLBBPlusColors.GithubSecondary,
                                unselectedTextColor = MLBBPlusColors.GithubSecondary,
                                indicatorColor = MLBBPlusColors.Gold.copy(alpha = 0.15f)
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
                            // Uses heroes.json IDs (Hanabi=60, Marcel=132) via GameData
                            selectedHero = GameData.heroesById[heroId]
                            tab = Tab.HEROES
                        }
                    )
                    Tab.HEROES -> {
                        val hero = selectedHero
                        if (hero == null) HeroesScreen(onHeroClick = { selectedHero = it })
                        else HeroDetailScreen(hero = hero, onBack = { selectedHero = null }, onHeroClick = { selectedHero = it })
                    }
                    Tab.ITEMS -> {
                        val item = selectedItem
                        if (item == null) ItemsScreen(onItemClick = { selectedItem = it })
                        else ItemDetailScreen(item = item, onBack = { selectedItem = null }, onItemClick = { selectedItem = it }, onHeroClick = { selectedHero = it; tab = Tab.HEROES })
                    }
                    Tab.PATCH -> PatchNotesScreen()
                    Tab.SETTINGS -> SettingsScreen(overlayGranted = overlayGranted, onOverlayGrantedChange = { overlayGranted = it }, autoStartLastRole = false, onAutoStartChange = {})
                }
            }
        }
    }
}
