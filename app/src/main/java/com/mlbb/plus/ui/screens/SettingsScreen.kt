package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.ScreenTitleBar
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.theme.AppUiColors

// ============================================================
// plus/ui/screens/SettingsScreen.kt  ->  FIXED
// (was bundled with PatchNotesScreen in one file - split)
// ------------------------------------------------------------
// FIXES:
//  [1] CRITICAL - overlay Switch lied. Old: Switch called
//      onOverlayGrantedChange(it) and MainActivity did
//      overlayGranted = it. Flipping the Switch to ON set the
//      Boolean to true even though the system permission was
//      still denied, so Home showed READY incorrectly. Flipping
//      to OFF set it false even though the system permission
//      was still granted. A Switch cannot grant or revoke
//      SYSTEM_ALERT_WINDOW. Fix: this file now ignores OFF
//      (calls nothing) and only forwards ON as a request to
//      open system settings. MainActivity (fixed in this batch)
//      now opens ACTION_MANAGE_OVERLAY_PERMISSION on ON and
//      ignores OFF, then re-reads the real permission on tab
//      change. Subtitles explain where to turn it off.
//  [2] Patch fallback duplicated. Old: "2.2.23" / "2.2.32"
//      hardcoded in the data line. Fix: displayVersion /
//      advancedDisplayVersion from Models.kt.
//  [3] Auto-start toggle was a no-op in MainActivity
//      (passed false + {}). This file keeps the same signature
//      so MainActivity does not break, but MainActivity in
//      this batch now persists it to SharedPreferences.
//      Do NOT pass {} in new code.
// ============================================================

@Composable
fun SettingsScreen(overlayGranted: Boolean, onOverlayGrantedChange: (Boolean) -> Unit, autoStartLastRole: Boolean, onAutoStartChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        ScreenTitleBar("SETTINGS", AppIcons.Settings)
        SettingsContent(overlayGranted, onOverlayGrantedChange, autoStartLastRole, onAutoStartChange)
    }
}

@Composable
private fun SettingsContent(overlayGranted: Boolean, onOverlayGrantedChange: (Boolean) -> Unit, autoStartLastRole: Boolean, onAutoStartChange: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AppUiColors.GithubBg)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("FLOATING GUIDE")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(AppUiColors.GithubCard)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background((if (overlayGranted) AppUiColors.Green else AppUiColors.Orange).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(if (overlayGranted) "✓" else "!", color = if (overlayGranted) AppUiColors.Green else AppUiColors.Orange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (overlayGranted) "Permission is enabled" else "Permission is needed", color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (overlayGranted) "The floating guide is allowed to appear over other apps."
                        else "Android needs your permission before the guide can appear over Mobile Legends.",
                        color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
            Button(
                onClick = { onOverlayGrantedChange(true) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppUiColors.Gold, contentColor = Color(0xFF171717)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (overlayGranted) "Manage Android permission" else "Open Android settings", fontWeight = FontWeight.Bold)
            }
            Text(
                "On the next screen, find MLBB Plus and enable ‘Display over other apps’. You can return here afterward.",
                color = AppUiColors.GithubSecondary, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        SectionLabel("STARTUP")
        SettingsRow(
            title = "Start the guide when the app opens",
            subtitle = "Automatically show your last selected position and playstyle when you open MLBB Plus. Requires overlay permission.",
            checked = autoStartLastRole,
            onChecked = onAutoStartChange
        )

        DataFilesSection()
        Text("MLBB Plus v1.0", color = AppUiColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .heightIn(min = 76.dp).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = AppUiColors.Gold, checkedTrackColor = AppUiColors.Gold.copy(alpha = 0.3f))
        )
    }
}

/**
 * What the app actually loaded from assets/, file by file. A guide that is older than the current
 * patch, a pick that is missing from heroes.json, a bad JSON file - all visible without logcat.
 */
@Composable
private fun DataFilesSection() {
    val notes = GameData.patchNotes
    val livePatch = notes.displayVersion
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppUiColors.GithubCard)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
                .semantics { role = Role.Button }.clickable { expanded = !expanded }
                .heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Data status & troubleshooting", color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Advanced details about the app's local guide files", color = AppUiColors.GithubSecondary, fontSize = 11.sp)
            }
            Text(if (expanded) "−" else "+", color = AppUiColors.Gold, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fullProfiles = GameData.heroes.count { it.hasFullProfile }
        DataRow(
            "heroes.json",
            "${GameData.liveHeroes.size} live \u2022 ${GameData.advancedHeroes.size} advanced \u2022 $fullProfiles with full profile (skills + build)",
            if (GameData.heroes.isEmpty()) "MISSING" else if (fullProfiles < GameData.heroes.size) "PARTIAL" else "OK",
            if (GameData.heroes.isEmpty()) AppUiColors.Red else if (fullProfiles < GameData.heroes.size) AppUiColors.Orange else AppUiColors.Green
        )
        DataRow(
            "items.json",
            "${GameData.items.size} items",
            if (GameData.items.isEmpty()) "MISSING" else "OK",
            if (GameData.items.isEmpty()) AppUiColors.Red else AppUiColors.Green
        )
        val adv = if (notes.advancedServer.hasContent) " \u2022 Advanced ${notes.advancedDisplayVersion}" else " \u2022 no Advanced / Nov 01 data in file"
        DataRow(
            "patch_notes.json",
            "Patch $livePatch \u2022 ${notes.originalServer.heroChanges.size} hero / ${notes.originalServer.itemChanges.size} item changes$adv",
            if (notes.originalServer.hasContent) "OK" else "MISSING",
            if (notes.originalServer.hasContent) AppUiColors.Green else AppUiColors.Red
        )
        com.mlbb.plus.ui.model.RoleTypes.roles.forEach { role ->
            val g = GameData.guides[role]
            if (g == null) {
                DataRow("${role.lowercase()}_guide.json", "Not found or invalid \u2014 overlay runs without a guide", "MISSING", AppUiColors.Red)
            } else {
                val older = g.patch.isNotBlank() && g.patch != livePatch
                DataRow(
                    g.assetName,
                    "${g.types.size} types \u2022 ${g.stepCount} steps \u2022 patch ${g.patch.ifBlank { "?" }}" +
                        (if (g.updated.isNotBlank()) " \u2022 ${g.updated}" else "") +
                        (if (older) " \u2022 older than $livePatch" else ""),
                    if (older) "OLDER" else "OK",
                    if (older) AppUiColors.Orange else AppUiColors.Green
                )
            }
        }
        val missingPicks = GameData.unlinkedGuidePicks
        val missingItems = GameData.unlinkedGuideItems
        if (missingPicks.isNotEmpty()) {
            Text("Guide picks not in heroes.json: ${missingPicks.joinToString(", ")}", color = AppUiColors.Orange, fontSize = 12.sp)
        }
        if (missingItems.isNotEmpty()) {
            Text("Guide core items not in items.json: ${missingItems.joinToString(", ")}", color = AppUiColors.Orange, fontSize = 12.sp)
        }
        }
    }
}

@Composable
private fun DataRow(name: String, detail: String, status: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = AppUiColors.GithubSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}
