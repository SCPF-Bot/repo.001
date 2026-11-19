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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.theme.MLBBPlusColors

// NEW HOME — MLBB Plus
// FIX: No Material Icons import — same Release failure. QuickActions now use emoji String.
// - Centered MLBB Plus header (per your spec)
// - Upper card: Roam Guide • Tank Roam, READY + Overlay permission: granted, Overlay is running DIRECTLY below
// - Middle: centered Role + Role Type dropdown boxes (not pills)
// - Quick Actions + Top Meta Heroes (real portraits from assets/)
// - Launch Widget button at bottom, above nav (nothing below it)
// - No ViewModel / Hilt — state comes from MainActivity

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
        // ── Centered header ──
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MLBB Plus", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            Text("Overlay tools for MLBB. • ${GameData.liveHeroes.size} heroes • Patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // ── Upper card ──
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
                    Text("Optimized routes, vision control, and engage combos for $selectedRoleType heroes.", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
                    Text(if (overlayGranted) "✓ Overlay permission: granted" else "○ Overlay permission: needed", color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange, fontSize = 12.sp)
                    Text(if (overlayRunning) "● Overlay is running" else "○ Overlay is not running", color = if (overlayRunning) MLBBPlusColors.Green else MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                }
            }

            // ── Middle: centered dropdown boxes ──
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Role", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRole, options = roles, onSelect = onRoleChange)
                Text("Role Type", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRoleType, options = roleTypes, onSelect = onRoleTypeChange)
            }

            // ── Quick Actions ──
            SectionHeader("QUICK ACTIONS")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Hero Explorer", "👤", Modifier.weight(1f), onOpenHeroes)
                QuickActionCard("Items", "🎒", Modifier.weight(1f), onOpenItems)
                QuickActionCard("Patch Notes", "📄", Modifier.weight(1f), onOpenPatchNotes)
            }

            // ── Top Meta Heroes — MAXIMIZED: from heroes.json isMeta + winRate (Marcel, Hanabi, etc.) ──
            SectionHeader("TOP META HEROES • Patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }}")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GameData.topMetaHeroes) { hero ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MLBBPlusColors.GithubCard)
                            .border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(12.dp))
                            .clickable { onHeroClick(hero.id) }.padding(8.dp).width(76.dp)
                    ) {
                        HeroImage(
                            hero = hero,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(MLBBPlusColors.GithubSurface)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%.0f%% win".format(hero.winRate * 100), color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Launch Widget — last thing on Home ──
            Button(
                onClick = onLaunchWidget,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MLBBPlusColors.Gold, contentColor = Color.Black),
                shape = RoundedCornerShape(100.dp)
            ) { Text("▶ Launch Widget", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Spacer(Modifier.height(8.dp))
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
private fun QuickActionCard(label: String, emoji: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 18.sp)
            }
            Text(label, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
