package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.PatchData
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// plus/ui/screens/PatchNotesScreen.kt  ->  FIXED
// (was bundled with SettingsScreen in one file - split)
// ------------------------------------------------------------
// FIXES:
//  [1] Stringly-typed tab. Old: var server: String = "Current",
//      with "Current" / "Advanced" / "Nov01" strings. A typo
//      like "current" silently falls through to Original.
//      Fix: enum PatchTab { CURRENT, ADVANCED, NOV01 }.
//  [2] Hardcoded date + empty version. Old:
//      "Updated ${notes.updated.ifBlank { "2026-11-20" }} - ${notes.version}".
//      Hardcoded date goes stale, and if version is blank the
//      line ends with a dangling bullet. Fix: use displayVersion,
//      omit the date when updated is blank.
//  [3] Nov 01 card showed a dangling bullet when there were no
//      event details. Old: body ended with "\n- " + joinToString
//      on an empty list. Fix: only append events when non-empty,
//      else show "No event details in patch_notes.json".
//  [4] Dead duplicate META NOTES block. Old: second block checked
//      server == "Current" && data.metaNotes.isBlank() &&
//      originalServer.metaNotes.isNotBlank(). But when server is
//      Current, data IS originalServer, so both cannot be true.
//      Fix: removed dead block.
//  [5] CRITICAL - hardcoded Roam Meta card. Old: "Tank Roam
//      remains S tier... Marcel (132) is S-tier Roam - 57.7%
//      win rate." Hardcoded win rate + hardcoded patch 2.2.23.
//      It goes stale the moment heroes.json changes, and it
//      contradicts JSON if Marcel's winRate changes. Fix:
//      Roam Meta card is now built from GameData.liveHeroes
//      (top 3 Roam heroes by winRate, real winRate * 100).
//      If no Roam heroes, the card is omitted.
//  [6] Blank type / blank changes made dangling dashes / bullets.
//      Old: title "... - ${type}" with type = "" gave a trailing
//      dash. Body "• " + joinToString on empty changes gave a
//      lone bullet. Fix: type blank -> "CHANGE", changes empty
//      -> "No details in patch_notes.json".
//  [7] Tab boxes had no accessibility role / selected state.
//      Fix: semantics Role.Tab + selected.
//  [8] Patch fallback duplicated. Fix: displayVersion /
//      advancedDisplayVersion from Models.kt.
// ============================================================

private enum class PatchTab(val label: String) {
    CURRENT("Current"),
    ADVANCED("Advanced"),
    NOV01("Nov 01")
}

private fun patchBadgeColor(type: String): Color {
    return when (type.trim().lowercase()) {
        "buff" -> MLBBPlusColors.PatchBuff
        "nerf" -> MLBBPlusColors.PatchNerf
        "revamp" -> MLBBPlusColors.PatchRevamp
        "adjust" -> MLBBPlusColors.PatchAdjust
        "reversion" -> MLBBPlusColors.PatchReversion
        else -> MLBBPlusColors.GithubSecondary
    }
}

@Composable
fun PatchNotesScreen() {
    // FIX [1]: enum, not strings.
    var tab by remember { mutableStateOf(PatchTab.CURRENT) }
    val notes = GameData.patchNotes
    val data: PatchData = when (tab) {
        PatchTab.ADVANCED -> notes.advancedServer
        PatchTab.NOV01 -> notes.nov01Advanced
        PatchTab.CURRENT -> notes.originalServer
    }
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PATCH NOTES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        // FIX [2]: no hardcoded date, no dangling bullet.
        val header = buildString {
            if (notes.updated.isNotBlank()) append("Updated ${notes.updated} \u2022 ")
            append("Patch ${notes.displayVersion}")
        }
        Text(header, color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(100.dp)).padding(4.dp)) {
            PatchTab.entries.forEach { t ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(if (tab == t) MLBBPlusColors.Gold else MLBBPlusColors.GithubCard)
                        // FIX [7]: TalkBack tab role + selected.
                        .semantics { role = Role.Tab; selected = (tab == t) }
                        .clickable { tab = t }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t.label, color = if (tab == t) Color.Black else MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (tab == PatchTab.NOV01) {
            // FIX [3]: no dangling bullet on empty events.
            val body = buildString {
                append("Live patch on Nov 01 was still ${notes.nov01LivePatch.ifBlank { notes.displayVersion }}.")
                if (notes.nov01OriginalEvent.isNotEmpty()) {
                    append("\n")
                    append(notes.nov01OriginalEvent.joinToString("\n\u2022 ", prefix = "\u2022 "))
                } else {
                    append("\nNo event details in patch_notes.json")
                }
            }
            PatchCard(title = "Nov 01 \u2014 Original Server: Event ONLY (no balance patch)", body = body)
        }
        PatchCard(title = "${data.title.ifBlank { "Patch ${data.version.ifBlank { notes.displayVersion }}" }} \u2022 ${data.server} \u2022 ${data.date}", body = data.summary.ifBlank { "No summary in patch_notes.json" })
        if (data.warning.isNotBlank()) PatchCard(title = "\u26A0 Testing Only", body = data.warning)
        if (data.highlights.isNotEmpty()) PatchCard(title = "HIGHLIGHTS", body = data.highlights.joinToString("\n\u2022 ", prefix = "\u2022 "))
        if (data.heroChanges.isNotEmpty()) {
            Text("HERO CHANGES (${data.heroChanges.size})", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.heroChanges.forEach { ch ->
                // FIX [6]: blank type -> CHANGE, blank changes -> fallback text.
                val typeLabel = ch.type.trim().uppercase().ifBlank { "CHANGE" }
                val body = buildString {
                    if (ch.changes.isNotEmpty()) append(ch.changes.joinToString("\n\u2022 ", prefix = "\u2022 "))
                    else append("No details in patch_notes.json")
                    if (ch.skillsAffected.isNotEmpty()) append("\n\nSkills: ${ch.skillsAffected.joinToString(", ")}")
                    if (ch.impact.isNotBlank()) append("\nImpact: ${ch.impact}")
                }
                PatchCard(
                    title = "${ch.heroName.ifBlank { "Hero" }} #${ch.heroId} \u2014 $typeLabel${if (ch.tierChange.isNotBlank()) " \u2022 ${ch.tierChange}" else ""}",
                    body = body,
                    badgeColor = patchBadgeColor(ch.type)
                )
            }
        }
        if (data.itemChanges.isNotEmpty()) {
            Text("ITEM CHANGES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.itemChanges.forEach { ic ->
                val typeLabel = ic.type.trim().uppercase().ifBlank { "CHANGE" }
                val body = if (ic.changes.isNotEmpty()) ic.changes.joinToString("\n\u2022 ", prefix = "\u2022 ") else "No details in patch_notes.json"
                PatchCard(title = "${ic.itemName.ifBlank { "Item" }} #${ic.itemId} \u2014 $typeLabel", body = body)
            }
        }
        if (data.battlefieldChanges.isNotEmpty()) PatchCard(title = "BATTLEFIELD", body = data.battlefieldChanges.joinToString("\n\u2022 ", prefix = "\u2022 "))
        if (data.systemChanges.isNotEmpty()) PatchCard(title = "SYSTEM", body = data.systemChanges.joinToString("\n\u2022 ", prefix = "\u2022 "))
        // FIX [4]: dead duplicate block removed - one META NOTES card only.
        if (data.metaNotes.isNotBlank()) PatchCard(title = "META NOTES", body = data.metaNotes)
        // FIX [5]: Roam Meta from live JSON, not hardcoded 57.7%.
        if (tab == PatchTab.CURRENT) {
            val roamTop = GameData.liveHeroes.filter { it.lane == "Roam" }
                .sortedByDescending { it.winRate }.take(3)
            if (roamTop.isNotEmpty()) {
                val body = roamTop.joinToString("\n\u2022 ", prefix = "\u2022 ") { hero ->
                    // winRate is 0..1 (Models.kt) -> *100 is correct.
                    "${hero.name} - ${"%.1f%%".format(hero.winRate * 100)} win rate"
                }
                PatchCard(title = "Roam Meta (${notes.displayVersion})", body = body)
            }
        }
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
