package com.mlbb.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.screens.*
import com.mlbb.plus.ui.theme.MLBBPlusColors

// MLBB Plus — MainActivity with bottom nav hub
// Tabs: Home, Heroes, Items, Patch Notes, Settings
// Home holds Role + Role Type state, passes it to WidgetOverlayService via Intent extras
// Heroes / Items detail is shown INSIDE the tab (back = clear selection, not exit app)

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    HEROES("Heroes", Icons.Rounded.Person),
    ITEMS("Items", Icons.Rounded.Inventory2),
    PATCH("Patch Notes", Icons.Rounded.Description),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MLBBPlusApp(onLaunchWidget = { role, roleType -> launchWidget(role, roleType) }) }
    }

    private fun launchWidget(role: String, roleType: String) {
        // If overlay permission not granted, open system overlay settings first
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return
        }
        val intent = Intent(this, WidgetOverlayService::class.java).apply {
            putExtra("ROLE", role)
            putExtra("ROLE_TYPE", roleType)
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
    var overlayGranted by remember { mutableStateOf(false) }
    var overlayRunning by remember { mutableStateOf(false) }

    // Keep Role Type valid when Role changes
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
                                // Clear detail when switching tabs
                                if (t != Tab.HEROES) selectedHero = null
                                if (t != Tab.ITEMS) selectedItem = null
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
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
                            selectedHero = com.mlbb.plus.ui.model.SampleData.heroesById[heroId]
                            tab = Tab.HEROES
                        }
                    )
                    Tab.HEROES -> {
                        val hero = selectedHero
                        if (hero == null) {
                            HeroesScreen(onHeroClick = { selectedHero = it })
                        } else {
                            HeroDetailScreen(hero = hero, onBack = { selectedHero = null }, onHeroClick = { selectedHero = it })
                        }
                    }
                    Tab.ITEMS -> {
                        val item = selectedItem
                        if (item == null) {
                            ItemsScreen(onItemClick = { selectedItem = it })
                        } else {
                            ItemDetailScreen(
                                item = item,
                                onBack = { selectedItem = null },
                                onItemClick = { selectedItem = it },
                                onHeroClick = {
                                    selectedHero = it
                                    tab = Tab.HEROES
                                }
                            )
                        }
                    }
                    Tab.PATCH -> PatchNotesScreen()
                    Tab.SETTINGS -> SettingsScreen(
                        overlayGranted = overlayGranted,
                        onOverlayGrantedChange = { overlayGranted = it },
                        autoStartLastRole = false,
                        onAutoStartChange = {}
                    )
                }
            }
        }
    }
}
