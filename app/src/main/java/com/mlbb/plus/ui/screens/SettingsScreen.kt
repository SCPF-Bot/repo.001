package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.theme.MLBBPlusColors

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
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SETTINGS", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        // FIX [1]: OFF is ignored - system permission cannot be
        // revoked from a Switch. ON is a request to open settings.
        SettingsRow(
            title = "Overlay permission",
            subtitle = if (overlayGranted) "Granted \u2022 turn off in system settings" else "Not granted \u2022 turn on to open system settings",
            checked = overlayGranted,
            onChecked = { wanted -> if (wanted) onOverlayGrantedChange(true) }
        )
        SettingsRow(title = "Auto-start last role", subtitle = "Launch widget automatically on app open", checked = autoStartLastRole, onChecked = onAutoStartChange)
        // FIX [2]: single fallback via Models.kt.
        Text("Data: heroes.json ${GameData.liveHeroes.size} live + ${GameData.advancedHeroes.size} Advanced \u2022 items.json ${GameData.items.size} \u2022 patch ${GameData.patchNotes.displayVersion} / Advanced ${GameData.patchNotes.advancedDisplayVersion}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        Text("MLBB Plus v1.0 \u2014 GitHub Dark + Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(14.dp))
            .semantics { role = Role.Switch }
            .clickable { onChecked(!checked) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column { Text(title, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(subtitle, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = MLBBPlusColors.Gold, checkedTrackColor = MLBBPlusColors.Gold.copy(alpha = 0.3f)))
    }
}
