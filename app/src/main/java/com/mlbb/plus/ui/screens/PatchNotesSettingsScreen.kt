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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.theme.MLBBPlusColors

// Patch Notes — Current Server vs Advanced Server toggle, GitHub dark + gold
@Composable
fun PatchNotesScreen() {
    var server by remember { mutableStateOf("Current") } // Current | Advanced
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PATCH NOTES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(100.dp)).padding(4.dp)) {
            listOf("Current", "Advanced").forEach { s ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(if (server == s) MLBBPlusColors.Gold else MLBBPlusColors.GithubCard)
                        .clickable { server = s }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (s == "Current") "Current Server" else "Advanced Server", color = if (server == s) androidx.compose.ui.graphics.Color.Black else MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        // Placeholder cards — replace with real patch data from your JSON / API
        PatchCard(title = if (server == "Current") "Patch 1.9.12 — Current Server" else "Patch 1.9.13 — Advanced Server", body = "Hanabi: base damage slightly reduced.\nDemon Hunter Sword: lifesteal unchanged.\nRoam gold: +5 per assist.")
        PatchCard(title = "Roam Meta", body = "Tank Roam remains S tier. Support Roam rising with Estes / Rafaela.")
    }
}

@Composable
private fun PatchCard(title: String, body: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
        }
    }
}

// Settings — GitHub dark + gold, resolves old auto-close vs new hub conflict
// Auto-start last role = if ON, launching app auto-starts last Role/Type widget (old behavior). If OFF, Home stays open.
@Composable
fun SettingsScreen(
    overlayGranted: Boolean,
    onOverlayGrantedChange: (Boolean) -> Unit,
    autoStartLastRole: Boolean,
    onAutoStartChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SETTINGS", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        SettingsRow(title = "Overlay permission", subtitle = if (overlayGranted) "Granted" else "Not granted", checked = overlayGranted, onChecked = onOverlayGrantedChange)
        SettingsRow(title = "Auto-start last role", subtitle = "Launch widget automatically on app open", checked = autoStartLastRole, onChecked = onAutoStartChange)
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
