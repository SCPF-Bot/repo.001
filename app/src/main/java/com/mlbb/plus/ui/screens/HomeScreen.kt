package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// plus/ui/screens/HomeScreen.kt  ->  FIXED
// ------------------------------------------------------------
// FIXES:
//  [1] Duplicate Role -> RoleType map. Old: HomeScreen had its own
//      roleTypesByRole map, MainActivity had validTypes map.
//      Two sources of truth - if they diverge, dropdown shows a
//      type MainActivity immediately corrects away (flicker).
//      Fix: both now use RoleTypes in Models.kt.
//  [2] LazyRow inside verticalScroll Column with no height.
//      LazyRow in a vertically scrolling Column can measure with
//      infinite height and clip / mis-measure. For 8 items a
//      LazyRow is overkill. Fix: Row + horizontalScroll with
//      explicit content - no nested lazy measurement.
//  [3] roles / roleTypes lists rebuilt every recomposition.
//      Fix: remember + RoleTypes.
//  [4] Patch fallback duplicated. Fix: displayVersion.
//  [5] DropdownBox clickable Box had no semantics role.
//      TalkBack read it as generic. Fix: semantics Role.DropdownList.
// ============================================================

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
    // FIX [1] + [3]: single source, remembered.
    val roles = remember { RoleTypes.roles }
    val roleTypes = remember(selectedRole) { RoleTypes.typesFor(selectedRole) }

    Column(
        Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MLBB Plus", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            Text("Overlay tools for MLBB. \u2022 ${GameData.liveHeroes.size} heroes \u2022 Patch ${GameData.patchNotes.displayVersion}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MLBBPlusColors.GithubCard)
                    .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("\uD83D\uDEE1", fontSize = 18.sp)
                            Text("$selectedRole Guide \u2022 $selectedRoleType $selectedRole", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.GithubSurface).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(if (overlayGranted) "READY" else "NEEDS PERMISSION", color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Optimized routes, vision control, and engage combos for $selectedRoleType heroes.", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
                    Text(if (overlayGranted) "\u2713 Overlay permission: granted" else "\u25CB Overlay permission: needed", color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange, fontSize = 12.sp)
                    Text(if (overlayRunning) "\u25CF Overlay is running" else "\u25CB Overlay is not running", color = if (overlayRunning) MLBBPlusColors.Green else MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Role", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRole, options = roles, onSelect = onRoleChange)
                Text("Role Type", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                DropdownBox(value = selectedRoleType, options = roleTypes, onSelect = onRoleTypeChange)
            }

            SectionHeader("QUICK ACTIONS")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Hero Explorer", "\uD83D\uDC64", Modifier.weight(1f), onOpenHeroes)
                QuickActionCard("Items", "\uD83C\uDF92", Modifier.weight(1f), onOpenItems)
                QuickActionCard("Patch Notes", "\uD83D\uDCC4", Modifier.weight(1f), onOpenPatchNotes)
            }

            SectionHeader("TOP META HEROES \u2022 Patch ${GameData.patchNotes.displayVersion}")
            // FIX [2]: Row + horizontalScroll, not LazyRow in verticalScroll.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameData.topMetaHeroes.forEach { hero ->
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
                        // winRate is 0..1 (Models.kt) -> *100 is correct.
                        Text("%.0f%% win".format(hero.winRate * 100), color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
                    }
                }
            }

            Button(
                onClick = onLaunchWidget,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MLBBPlusColors.Gold, contentColor = Color.Black),
                shape = RoundedCornerShape(100.dp)
            ) { Text("\u25B6 Launch Widget", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
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
                // FIX [5]: TalkBack role.
                .semantics { role = Role.DropdownList }
                .clickable { expanded = true }.padding(horizontal = 20.dp, vertical = 12.dp)
        ) { Text("$value \u25BE", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
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
