package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.SampleData
import com.mlbb.plus.ui.theme.MLBBPlusColors

// MLBB Plus Home — converted from old HomeScreen.kt (com.mlbb.assistant.presentation.home)
// CHANGES:
// - Package: com.mlbb.plus.ui.screens (was com.mlbb.assistant.presentation.home)
// - Theme: GitHub Dark + Gold (#0D1117 / #161B22 / #30363D / #E3B341) — no gradients, flat matte
// - Header: LEFT-aligned like Assistant reference (was gradient header)
// - Top card: Roam Guide • Tank Roam replaces CURRENT META + InsightsCard
// - NEW: Role / Role Type dropdowns in middle (centered boxes, not pills)
// - Quick Actions: Hero Explorer / Items / Patch Notes (was Explorer / Meta Board / Draft History)
// - Bottom: Launch Widget gold button (was Start Draft FAB bottom-end)
// - No Hilt ViewModel — Home is now pure UI + callbacks, state hoisted by MainActivity

@Composable
fun HomeScreen(
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    selectedRoleType: String,
    onRoleTypeChange: (String) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onLaunchWidget: () -> Unit,
    onOpenHeroes: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    onHeroClick: (Int) -> Unit,
) {
    val roles = listOf("Roam", "Jungle", "Mid", "Gold", "EXP")
    val roleTypesByRole = mapOf(
        "Roam" to listOf("Tank", "Support"),
        "Jungle" to listOf("Assassin", "Fighter", "Tank"),
        "Mid" to listOf("Burst", "Poke", "Control"),
        "Gold" to listOf("Marksman", "Mage"),
        "EXP" to listOf("Fighter", "Tank", "Assassin")
    )
    val roleTypes = roleTypesByRole[selectedRole] ?: listOf("Tank")

    Column(
        Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState())
    ) {
        // ── Header — LEFT aligned, like Assistant reference ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(MLBBPlusColors.Gold.copy(alpha = 0.15f))
                    .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("M", color = MLBBPlusColors.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            Column {
                Text("MLBB Plus", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
                Text("Overlay tools for MLBB.", color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp, letterSpacing = 0.3.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // ── Upper Card — Roam Guide • Tank Roam — like CURRENT META card ──
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MLBBPlusColors.GithubCard)
                    .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🛡", fontSize = 18.sp)
                            Text("$selectedRole Guide • $selectedRoleType $selectedRole", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.GithubSurface).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(if (overlayGranted) "READY" else "NEEDS PERMISSION", color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Optimized roam routes, vision control, and engage combos for $selectedRoleType heroes.", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
                    // Status lines — Overlay is running DIRECTLY below permission, inside this card
                    Text(if (overlayGranted) "✓ Overlay permission: granted" else "○ Overlay permission: needed", color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange, fontSize = 12.sp)
                    Text(if (overlayRunning) "● Overlay is running" else "○ Overlay is not running", color = if (overlayRunning) MLBBPlusColors.Green else MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                }
            }

            // ── Middle — Centered dropdown boxes, NOT pills ──
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Role", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRole, options = roles, onSelect = onRoleChange)
                Text("Role Type", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRoleType, options = roleTypes, onSelect = onRoleTypeChange)
            }

            // ── Quick Actions ──
            SectionHeader("QUICK ACTIONS")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Hero Explorer", Icons.Rounded.Person, Modifier.weight(1f), onOpenHeroes)
                QuickActionCard("Items", Icons.Rounded.Inventory2, Modifier.weight(1f), onOpenItems)
                QuickActionCard("Patch Notes", Icons.Rounded.Description, Modifier.weight(1f), onOpenPatchNotes)
            }

            // ── Top Meta Heroes ──
            SectionHeader("TOP META HEROES")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(SampleData.heroes.take(6)) { hero ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MLBBPlusColors.GithubCard)
                            .border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(12.dp))
                            .clickable { onHeroClick(hero.id) }.padding(8.dp).width(76.dp)
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(MLBBPlusColors.GithubSurface), contentAlignment = Alignment.Center) {
                            Text(hero.name.first().toString(), color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%.0f%% win".format(hero.winRate * 100), color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Launch Widget — pinned at bottom of scroll, nothing below it ──
            Button(
                onClick = onLaunchWidget,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MLBBPlusColors.Gold, contentColor = Color.Black),
                shape = RoundedCornerShape(100.dp)
            ) { Text("▶ Launch Widget", fontWeight = FontWeight.Bold, fontSize = 15.sp) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DropdownBox(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MLBBPlusColors.GithubCard)
                .border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(12.dp))
                .clickable { expanded = true }.padding(horizontal = 20.dp, vertical = 12.dp)
        ) { Text("$value ▾", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MLBBPlusColors.GithubCard)) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt, color = MLBBPlusColors.GithubText) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(MLBBPlusColors.Gold))
        Text(title, color = MLBBPlusColors.GithubSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun QuickActionCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MLBBPlusColors.Gold, modifier = Modifier.size(18.dp))
            }
            Text(label, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
