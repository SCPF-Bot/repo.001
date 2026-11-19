package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.PatchData
import com.mlbb.plus.ui.theme.MLBBPlusColors

// PATCH NOTES + SETTINGS — MAXIMIZED for patch_notes.json
// Uses MLBBPlusColors.Patch* from plus/ui/theme/MLBBPlusTheme.kt
// Before: hardcoded "Patch 1.9.12" placeholder. Now: real data.
// Tabs: Current (Original 2.2.23, 11 Nov) | Advanced (2.2.32, 12 Nov) | Nov 01 (2.2.30 Advanced + Original event)
// Each patch shows: highlights, heroChanges (with type badge + hero ID), itemChanges, battlefield, system, metaNotes, warning.
// Dori (134) appears here as Advanced-only — not in live heroes.

@Composable
fun PatchNotesScreen() {
    var server by remember { mutableStateOf("Current") } // Current | Advanced | Nov01
    val notes = GameData.patchNotes
    val data: PatchData = when (server) {
        "Advanced" -> notes.advancedServer
        "Nov01" -> notes.nov01Advanced
        else -> notes.originalServer
    }
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PATCH NOTES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        Text("Updated ${notes.updated.ifBlank { "2026-11-20" }} • ${notes.version}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(100.dp)).padding(4.dp)) {
            listOf("Current", "Advanced", "Nov01").forEach { s ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).background(if (server == s) MLBBPlusColors.Gold else MLBBPlusColors.GithubCard).clickable { server = s }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(when (s) { "Current" -> "Current"; "Advanced" -> "Advanced"; else -> "Nov 01" }, color = if (server == s) Color.Black else MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (server == "Nov01") {
            PatchCard(title = "Nov 01 — Original Server: Event ONLY (no balance patch)", body = "Live patch on Nov 01 was still ${notes.nov01LivePatch.ifBlank { "2.2.16a" }}.\n${notes.nov01OriginalEvent.joinToString("\n• ", prefix = "• ")}")
        }
        PatchCard(title = "${data.title.ifBlank { "Patch ${data.version}" }} • ${data.server} • ${data.date}", body = data.summary.ifBlank { "No summary in patch_notes.json" })
        if (data.warning.isNotBlank()) PatchCard(title = "⚠ Testing Only", body = data.warning)
        if (data.highlights.isNotEmpty()) PatchCard(title = "HIGHLIGHTS", body = data.highlights.joinToString("\n• ", prefix = "• "))
        // Hero Changes — maximized: type badge, hero ID, tierChange, impact
        if (data.heroChanges.isNotEmpty()) {
            Text("HERO CHANGES (${data.heroChanges.size})", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.heroChanges.forEach { ch ->
                val badgeColor = when (ch.type.lowercase()) { "buff" -> MLBBPlusColors.PatchBuff; "nerf" -> MLBBPlusColors.PatchNerf; "revamp" -> MLBBPlusColors.PatchRevamp; "adjust" -> MLBBPlusColors.PatchAdjust; "reversion" -> MLBBPlusColors.PatchReversion; else -> MLBBPlusColors.GithubSecondary }
                PatchCard(title = "${ch.heroName} #${ch.heroId} — ${ch.type.uppercase()}${if (ch.tierChange.isNotBlank()) " • ${ch.tierChange}" else ""}", body = buildString {
                    append(ch.changes.joinToString("\n• ", prefix = "• "))
                    if (ch.skillsAffected.isNotEmpty()) append("\n\nSkills: ${ch.skillsAffected.joinToString(", ")}")
                    if (ch.impact.isNotBlank()) append("\nImpact: ${ch.impact}")
                }, badgeColor = badgeColor)
            }
        }
        if (data.itemChanges.isNotEmpty()) {
            Text("ITEM CHANGES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.itemChanges.forEach { ic -> PatchCard(title = "${ic.itemName} #${ic.itemId} — ${ic.type}", body = ic.changes.joinToString("\n• ", prefix = "• ")) }
        }
        if (data.battlefieldChanges.isNotEmpty()) PatchCard(title = "BATTLEFIELD", body = data.battlefieldChanges.joinToString("\n• ", prefix = "• "))
        if (data.systemChanges.isNotEmpty()) PatchCard(title = "SYSTEM", body = data.systemChanges.joinToString("\n• ", prefix = "• "))
        if (data.metaNotes.isNotBlank()) PatchCard(title = "META NOTES", body = data.metaNotes)
        // Roam meta from originalServer metaNotes fallback
        if (server == "Current" && data.metaNotes.isBlank() && notes.originalServer.metaNotes.isNotBlank()) PatchCard(title = "META NOTES", body = notes.originalServer.metaNotes)
        PatchCard(title = "Roam Meta (2.2.23)", body = "Tank Roam remains S tier (Tigreal, Khufra, Grock). Support Roam rising (Estes, Angela, Mathilda). Marcel (132) is S-tier Roam — 57.7% win rate.")
    }
}

@Composable
private fun PatchCard(title: String, body: String, badgeColor: Color? = null) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (badgeColor != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor))
                Text(title, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(body, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun SettingsScreen(overlayGranted: Boolean, onOverlayGrantedChange: (Boolean) -> Unit, autoStartLastRole: Boolean, onAutoStartChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SETTINGS", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        SettingsRow(title = "Overlay permission", subtitle = if (overlayGranted) "Granted" else "Not granted", checked = overlayGranted, onChecked = onOverlayGrantedChange)
        SettingsRow(title = "Auto-start last role", subtitle = "Launch widget automatically on app open", checked = autoStartLastRole, onChecked = onAutoStartChange)
        Text("Data: heroes.json ${GameData.liveHeroes.size} live + ${GameData.advancedHeroes.size} Advanced • items.json ${GameData.items.size} • patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }} / Advanced ${GameData.patchNotes.advancedServer.version.ifBlank { "2.2.32" }}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        Text("MLBB Plus v1.0 — GitHub Dark + Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(14.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(title, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(subtitle, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = MLBBPlusColors.Gold, checkedTrackColor = MLBBPlusColors.Gold.copy(alpha = 0.3f)))
    }
}
