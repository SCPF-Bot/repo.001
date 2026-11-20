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
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.ScreenTitleBar
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

// internal: HomeScreen / HeroesScreen use the same buff / nerf / revamp colors.
internal fun patchBadgeColor(type: String): Color {
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
fun PatchNotesScreen(onHeroClick: (Int) -> Unit = {}, onItemClick: (Int) -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        ScreenTitleBar("PATCH NOTES", AppIcons.History)
        Box(Modifier.weight(1f)) { PatchNotesContent(onHeroClick, onItemClick) }
    }
}

@Composable
private fun PatchNotesContent(onHeroClick: (Int) -> Unit, onItemClick: (Int) -> Unit) {
    val notes = GameData.patchNotes
    // patch_notes.json currently ships ONE block (Original Server, flat layout). Advanced / Nov 01
    // tabs only appear when the file actually has data for them - they used to render empty cards.
    val tabs = remember(notes) {
        buildList {
            add(PatchTab.CURRENT)
            if (notes.advancedServer.hasContent) add(PatchTab.ADVANCED)
            if (notes.nov01Advanced.hasContent || notes.nov01OriginalEvent.isNotEmpty()) add(PatchTab.NOV01)
        }
    }
    var requested by remember { mutableStateOf(PatchTab.CURRENT) }
    val tab = if (requested in tabs) requested else PatchTab.CURRENT
    val data: PatchData = when (tab) {
        PatchTab.ADVANCED -> notes.advancedServer
        PatchTab.NOV01 -> notes.nov01Advanced
        PatchTab.CURRENT -> notes.originalServer
    }
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val header = buildString {
            if (notes.updated.isNotBlank()) append("Updated ${notes.updated} \u2022 ")
            append("Patch ${notes.displayVersion}")
            if (notes.originalServer.season.isNotBlank()) append(" \u2022 ${notes.originalServer.season}")
        }
        Text(header, color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        if (tabs.size > 1) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(100.dp)).padding(4.dp)) {
                tabs.forEach { t ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                            .background(if (tab == t) MLBBPlusColors.Gold else MLBBPlusColors.GithubCard)
                            .semantics { role = Role.Tab; selected = (tab == t) }
                            .clickable { requested = t }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.label, color = if (tab == t) Color.Black else MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        if (tab == PatchTab.NOV01) {
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
        val cardTitle = listOf(
            data.title.ifBlank { "Patch ${data.version.ifBlank { notes.displayVersion }}" },
            data.server.takeIf { it.isNotBlank() },
            data.date.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(" \u2022 ")
        PatchCard(title = cardTitle, body = data.summary.ifBlank { "No summary in patch_notes.json" })
        if (data.warning.isNotBlank()) PatchCard(title = "\u26A0 Testing Only", body = data.warning)
        if (data.highlights.isNotEmpty()) PatchCard(title = "HIGHLIGHTS", body = data.highlights.joinToString("\n\u2022 ", prefix = "\u2022 "))
        if (data.heroChanges.isNotEmpty()) {
            Text("HERO CHANGES (${data.heroChanges.size})", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.heroChanges.forEach { ch ->
                val typeLabel = ch.type.trim().uppercase().ifBlank { "CHANGE" }
                val known = GameData.heroesById[ch.heroId]
                val body = buildString {
                    // "Revamp - Ride Kupa" says more than the bare type; skip titles that just repeat it.
                    if (ch.title.isNotBlank() && !ch.title.trim().equals(ch.type.trim(), ignoreCase = true)) append("${ch.title}\n")
                    if (ch.changes.isNotEmpty()) append(ch.changes.joinToString("\n\u2022 ", prefix = "\u2022 "))
                    else append("No details in patch_notes.json")
                    if (ch.skillsAffected.isNotEmpty()) append("\n\nSkills: ${ch.skillsAffected.joinToString(", ")}")
                    if (ch.impact.isNotBlank()) append("\nImpact: ${ch.impact}")
                    if (known != null) append("\n\nTap for hero details \u2192")
                }
                PatchCard(
                    title = "${ch.heroName.ifBlank { "Hero" }} \u2014 $typeLabel${if (ch.tierChange.isNotBlank()) " \u2022 ${ch.tierChange.replace("->", "\u2192")}" else ""}${if (ch.isMeta) " \u2022 META" else ""}",
                    body = body,
                    badgeColor = patchBadgeColor(ch.type),
                    onClick = if (known != null) ({ onHeroClick(known.id) }) else null
                )
            }
        }
        if (data.itemChanges.isNotEmpty()) {
            Text("ITEM CHANGES", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
            data.itemChanges.forEach { ic ->
                val typeLabel = ic.type.trim().uppercase().ifBlank { "CHANGE" }
                val known = GameData.itemsById[ic.itemId]
                val body = (if (ic.changes.isNotEmpty()) ic.changes.joinToString("\n\u2022 ", prefix = "\u2022 ") else "No details in patch_notes.json") +
                    (if (known != null) "\n\nTap for item details \u2192" else "")
                PatchCard(
                    title = "${ic.itemName.ifBlank { "Item" }} \u2014 $typeLabel",
                    body = body,
                    badgeColor = patchBadgeColor(ic.type),
                    onClick = if (known != null) ({ onItemClick(known.id) }) else null
                )
            }
        }
        if (data.battlefieldChanges.isNotEmpty()) PatchCard(title = "BATTLEFIELD", body = data.battlefieldChanges.joinToString("\n\u2022 ", prefix = "\u2022 "))
        if (data.systemChanges.isNotEmpty()) PatchCard(title = "SYSTEM", body = data.systemChanges.joinToString("\n\u2022 ", prefix = "\u2022 "))
        if (data.metaNotes.isNotBlank()) PatchCard(title = "META NOTES", body = data.metaNotes)
        if (tab == PatchTab.CURRENT) {
            // Meta by lane, from heroes.json (isMeta + tracked win rate) instead of a hardcoded Roam card.
            val metaByLane = GameData.liveHeroes.filter { it.isMeta }.groupBy { it.lane }
            if (metaByLane.isNotEmpty()) {
                val body = metaByLane.entries.joinToString("\n") { (lane, heroes) ->
                    "$lane: " + heroes.sortedByDescending { it.winRate }.joinToString(", ") { h ->
                        if (h.hasLiveStats) "${h.name} ${"%.1f%%".format(h.winRate * 100)}" else h.name
                    }
                }
                PatchCard(title = "Meta heroes by lane (${notes.displayVersion})", body = body)
            }
            // Which guide files are written for which patch.
            val coverage = GameData.guides.values
            if (coverage.isNotEmpty()) {
                val body = coverage.joinToString("\n") { g ->
                    val older = g.patch.isNotBlank() && g.patch != notes.displayVersion
                    "${g.role}: ${g.types.size} types \u2022 patch ${g.patch.ifBlank { "?" }}" + if (older) " \u26A0 older than ${notes.displayVersion}" else " \u2713"
                }
                PatchCard(title = "Overlay guide coverage", body = body)
            }
            if (data.roamGuideLink.isNotBlank()) PatchCard(title = "ROAM GUIDE", body = data.roamGuideLink)
        }
        val footer = listOf(data.source, data.previousPatch.takeIf { it.isNotBlank() }?.let { "Previous patch: $it" })
            .filterNotNull().filter { it.isNotBlank() }
        if (footer.isNotEmpty()) Text(footer.joinToString("\n"), color = MLBBPlusColors.GithubSecondary.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun PatchCard(title: String, body: String, badgeColor: Color? = null, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (badgeColor != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor))
                Text(title, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(body, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
        }
    }
}
