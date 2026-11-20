package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppHeader
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.components.TierBadge
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.MLBBPlusColors

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
    val roles = remember { RoleTypes.roles }
    val roleTypes = remember(selectedRole) { RoleTypes.typesFor(selectedRole) }
    val patch = GameData.patchNotes.displayVersion

    Box(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AppHeader(
                title = "MLBB PLUS",
                subtitle = "Roam smarter. Win more.",
                icon = AppIcons.Shield
            )

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                PatchBanner(patch = patch, heroCount = GameData.liveHeroes.size, onClick = onOpenPatchNotes)

                RoamPanel(
                    selectedRole = selectedRole,
                    selectedRoleType = selectedRoleType,
                    roles = roles,
                    roleTypes = roleTypes,
                    onRoleChange = onRoleChange,
                    onRoleTypeChange = onRoleTypeChange,
                    overlayGranted = overlayGranted,
                    overlayRunning = overlayRunning
                )

                SectionLabel("QUICK ACTIONS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("Hero Explorer", AppIcons.Person, MLBBPlusColors.Blue, Modifier.weight(1f), onOpenHeroes)
                    QuickActionCard("Item Explorer", AppIcons.Apps, MLBBPlusColors.Teal, Modifier.weight(1f), onOpenItems)
                    QuickActionCard("Patch Notes", AppIcons.History, MLBBPlusColors.GithubSecondary, Modifier.weight(1f), onOpenPatchNotes)
                }

                SectionLabel("TOP META HEROES")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameData.topMetaHeroes.forEach { hero ->
                        MetaHeroCard(hero = hero, onClick = { onHeroClick(hero.id) })
                    }
                }

                // Room so the floating launch button never covers the last card.
                Spacer(Modifier.height(84.dp))
            }
        }

        LaunchButton(
            onClick = onLaunchWidget,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun PatchBanner(patch: String, heroCount: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(MLBBPlusColors.Gold.copy(alpha = 0.14f), MLBBPlusColors.Gold.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Fire, contentDescription = null, tint = MLBBPlusColors.Gold, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("CURRENT META", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp)
            Text(
                "Patch $patch \u2022 $heroCount heroes",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = MLBBPlusColors.Gold)
    }
}

@Composable
private fun RoamPanel(
    selectedRole: String,
    selectedRoleType: String,
    roles: List<String>,
    roleTypes: List<String>,
    onRoleChange: (String) -> Unit,
    onRoleTypeChange: (String) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.30f), shape)
    ) {
        // Gold top edge, like the "Your Insights" card in the reference.
        Box(Modifier.fillMaxWidth().height(2.dp).background(MLBBPlusColors.Gold.copy(alpha = 0.85f)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Shield, contentDescription = null, tint = MLBBPlusColors.Gold, modifier = Modifier.size(20.dp))
                Text(
                    "$selectedRole Guide",
                    color = MLBBPlusColors.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(
                    text = if (overlayGranted) "READY" else "NEEDS PERMISSION",
                    color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange
                )
            }
            Text(
                "Optimized routes, vision control, and engage combos for $selectedRoleType heroes.",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ROLE", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    DropdownBox(value = selectedRole, options = roles, onSelect = onRoleChange)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TYPE", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    DropdownBox(value = selectedRoleType, options = roleTypes, onSelect = onRoleTypeChange)
                }
            }
            Text(
                if (overlayGranted) "\u2713 Overlay permission granted" else "\u25CB Overlay permission needed",
                color = if (overlayGranted) MLBBPlusColors.Green else MLBBPlusColors.Orange,
                fontSize = 12.sp
            )
            Text(
                if (overlayRunning) "\u25CF Overlay is running" else "\u25CB Overlay is not running",
                color = if (overlayRunning) MLBBPlusColors.Green else MLBBPlusColors.GithubSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

@Composable
private fun DropdownBox(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MLBBPlusColors.GithubSurface)
                .border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(12.dp))
                .semantics { role = Role.DropdownList }
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(value, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("\u25BE", color = MLBBPlusColors.Gold, fontSize = 14.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MLBBPlusColors.GithubCard)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = MLBBPlusColors.GithubText) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f))))
            .border(1.dp, tint.copy(alpha = 0.40f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (tint == MLBBPlusColors.GithubSecondary) MLBBPlusColors.GithubText else tint, modifier = Modifier.size(24.dp))
        }
        Text(label, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
    }
}

@Composable
private fun MetaHeroCard(hero: Hero, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.GithubBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Box {
            HeroImage(
                hero = hero,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
            TierBadge(hero.tier, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(8.dp))
        Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // winRate is 0..1 (Models.kt) -> *100 is correct.
        Text("%.0f%% win".format(hero.winRate * 100), color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun LaunchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MLBBPlusColors.Gold)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
        Text("Launch Widget", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}
