package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppStatusBadge
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.components.SectionHeader
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.PatchData
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes
import com.mlbb.plus.ui.theme.AppTheme

private enum class PatchTab(val label: String) {
    CURRENT("Current"),
    ADVANCED("Advanced"),
    NOV01("Nov 01")
}

@Composable
internal fun patchBadgeColor(type: String): Color {
    val ext = AppTheme.colors
    return when (type.trim().lowercase()) {
        "buff" -> ext.patchBuff
        "nerf" -> ext.patchNerf
        "revamp" -> ext.patchRevamp
        "adjust" -> ext.patchAdjust
        "reversion" -> ext.patchReversion
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun patchBadgeIcon(type: String): ImageVector = when (type.trim().lowercase()) {
    "buff" -> AppIcons.TrendingUp
    "nerf" -> AppIcons.TrendingDown
    "revamp" -> AppIcons.Revamp
    "adjust", "reversion" -> AppIcons.Tune
    else -> AppIcons.Info
}

@Composable
fun PatchNotesScreen(onHeroClick: (Int) -> Unit = {}, onItemClick: (Int) -> Unit = {}) {
    val notes = GameData.patchNotes
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = "Patch Notes",
            subtitle = buildString {
                if (notes.updated.isNotBlank()) append("Updated ${notes.updated} • ")
                append("Patch ${notes.shortVersion}")
                if (notes.originalServer.season.isNotBlank()) append(" • ${notes.originalServer.season}")
            },
            leadingIcon = AppIcons.History
        )
        PatchNotesContent(
            onHeroClick = onHeroClick,
            onItemClick = onItemClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PatchNotesContent(
    onHeroClick: (Int) -> Unit,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes = GameData.patchNotes
    val tabs = remember(notes) {
        buildList {
            add(PatchTab.CURRENT)
            if (notes.advancedServer.hasContent) add(PatchTab.ADVANCED)
            if (notes.nov01Advanced.hasContent || notes.nov01OriginalEvent.isNotEmpty()) add(PatchTab.NOV01)
        }
    }
    var requested by rememberSaveable { mutableStateOf(PatchTab.CURRENT) }
    val tab = if (requested in tabs) requested else PatchTab.CURRENT
    val data: PatchData = when (tab) {
        PatchTab.ADVANCED -> notes.advancedServer
        PatchTab.NOV01 -> notes.nov01Advanced
        PatchTab.CURRENT -> notes.originalServer
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.Space16),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.MaxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)
        ) {
            if (tabs.size > 1) {
                PrimaryTabRow(
                    selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(AppShapes.Card)
                ) {
                    tabs.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { requested = t },
                            text = { Text(t.label, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.testTag("patch_tab_${t.name.lowercase()}")
                        )
                    }
                }
            }

            if (tab == PatchTab.NOV01) {
                val body = buildString {
                    append("Live patch on Nov 01 was still ${notes.nov01LivePatch.ifBlank { notes.shortVersion }}.")
                    if (notes.nov01OriginalEvent.isNotEmpty()) {
                        append("\n")
                        append(notes.nov01OriginalEvent.joinToString("\n• ", prefix = "• "))
                    } else {
                        append("\nNo event details in patch_notes.json")
                    }
                }
                PatchCard(
                    title = "Nov 01 — Original Server: Event ONLY (no balance patch)",
                    body = body
                )
            }

            val cardTitle = listOfNotNull(
                data.title.ifBlank { "Patch ${data.version.ifBlank { notes.shortVersion }}" },
                data.server.takeIf { it.isNotBlank() },
                data.date.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            PatchCard(
                title = cardTitle,
                body = data.summary.ifBlank { "Patch details are supplied by the heroes.json patch manifest." }
            )

            if (data.warning.isNotBlank()) {
                PatchCard(
                    title = "Testing Only",
                    body = data.warning,
                    badgeLabel = "NOTICE",
                    badgeColor = AppTheme.colors.warning,
                    badgeIcon = AppIcons.Warning
                )
            }

            if (data.highlights.isNotEmpty()) {
                PatchCard(
                    title = "Highlights",
                    body = data.highlights.joinToString("\n• ", prefix = "• ")
                )
            }

            if (data.heroChanges.isNotEmpty()) {
                SectionHeader(title = "HERO CHANGES (${data.heroChanges.size})")
                data.heroChanges.forEach { ch ->
                    val typeLabel = ch.type.trim().uppercase().ifBlank { "CHANGE" }
                    val known = GameData.heroesById[ch.heroId]
                    val body = buildString {
                        if (ch.title.isNotBlank() && !ch.title.trim().equals(ch.type.trim(), ignoreCase = true)) {
                            append("${ch.title}\n")
                        }
                        if (ch.changes.isNotEmpty()) {
                            append(ch.changes.joinToString("\n• ", prefix = "• "))
                        } else {
                            append("No additional change details supplied.")
                        }
                        if (ch.skillsAffected.isNotEmpty()) {
                            append("\n\nSkills: ${ch.skillsAffected.joinToString(", ")}")
                        }
                        if (ch.impact.isNotBlank()) {
                            append("\nImpact: ${ch.impact}")
                        }
                    }
                    val headline = buildString {
                        append(ch.heroName.ifBlank { "Hero" })
                        if (ch.tierChange.isNotBlank()) append(" • ${ch.tierChange.replace("->", "→")}")
                        if (ch.isMeta) append(" • META")
                    }
                    PatchCard(
                        title = headline,
                        body = body,
                        badgeLabel = typeLabel,
                        badgeColor = patchBadgeColor(ch.type),
                        badgeIcon = patchBadgeIcon(ch.type),
                        linkHint = if (known != null) "Tap to inspect hero" else null,
                        onClick = if (known != null) ({ onHeroClick(known.id) }) else null,
                        modifier = Modifier.testTag("patch_hero_${ch.heroId}")
                    )
                }
            }

            if (data.itemChanges.isNotEmpty()) {
                SectionHeader(title = "ITEM CHANGES (${data.itemChanges.size})")
                data.itemChanges.forEach { ic ->
                    val typeLabel = ic.type.trim().uppercase().ifBlank { "CHANGE" }
                    val known = GameData.itemsById[ic.itemId]
                    val body = if (ic.changes.isNotEmpty()) {
                        ic.changes.joinToString("\n• ", prefix = "• ")
                    } else {
                        "No additional change details supplied."
                    }
                    PatchCard(
                        title = ic.itemName.ifBlank { "Item" },
                        body = body,
                        badgeLabel = typeLabel,
                        badgeColor = patchBadgeColor(ic.type),
                        badgeIcon = patchBadgeIcon(ic.type),
                        linkHint = if (known != null) "Tap to inspect item" else null,
                        onClick = if (known != null) ({ onItemClick(known.id) }) else null,
                        modifier = Modifier.testTag("patch_item_${ic.itemId}")
                    )
                }
            }

            if (data.battlefieldChanges.isNotEmpty()) {
                PatchCard(
                    title = "Battlefield",
                    body = data.battlefieldChanges.joinToString("\n• ", prefix = "• ")
                )
            }
            if (data.systemChanges.isNotEmpty()) {
                PatchCard(
                    title = "System",
                    body = data.systemChanges.joinToString("\n• ", prefix = "• ")
                )
            }
            if (data.equipmentChanges.isNotEmpty()) {
                PatchCard(
                    title = "Equipment Changes",
                    body = data.equipmentChanges.joinToString("\n• ", prefix = "• ")
                )
            }
            if (data.metaNotes.isNotBlank()) {
                PatchCard(title = "Meta Notes", body = data.metaNotes)
            }

            if (tab == PatchTab.CURRENT) {
                val metaByLane = GameData.liveHeroes.filter { it.isMeta }.groupBy { it.lane }
                if (metaByLane.isNotEmpty()) {
                    val body = metaByLane.entries.joinToString("\n") { (lane, heroes) ->
                        "$lane: " + heroes.sortedByDescending { it.winRate }.joinToString(", ") { h ->
                            if (h.hasLiveStats) "${h.name} ${"%.1f%%".format(h.winRate * 100)}" else h.name
                        }
                    }
                    PatchCard(title = "Meta heroes by lane (${notes.shortVersion})", body = body)
                }

                val coverage = GameData.guides.values
                if (coverage.isNotEmpty()) {
                    AppCard {
                        Text(
                            text = "Overlay guide coverage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        coverage.forEach { g ->
                            val guidePatch = g.patch.substringBefore("-")
                            val older = guidePatch.isNotBlank() && guidePatch != notes.shortVersion
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4)
                            ) {
                                Text(
                                    text = "${g.role}: ${g.types.size} types • patch ${g.patch.ifBlank { "?" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                AppStatusBadge(
                                    text = if (older) "OLDER THAN ${notes.shortVersion}" else "CURRENT",
                                    color = if (older) AppTheme.colors.warning else AppTheme.colors.success,
                                    leadingIcon = if (older) AppIcons.Warning else AppIcons.CheckCircle
                                )
                            }
                        }
                    }
                }

                if (data.roamGuideLink.isNotBlank()) {
                    PatchCard(title = "Roam Guide", body = data.roamGuideLink)
                }
            }

            val footer = listOfNotNull(
                data.source.takeIf { it.isNotBlank() },
                data.previousPatch.takeIf { it.isNotBlank() }?.let { "Previous patch: $it" }
            )
            if (footer.isNotEmpty()) {
                Text(
                    text = footer.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatchCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
    badgeColor: Color? = null,
    badgeIcon: ImageVector? = null,
    linkHint: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (!badgeLabel.isNullOrBlank() && badgeColor != null) {
                AppStatusBadge(
                    text = badgeLabel,
                    color = badgeColor,
                    leadingIcon = badgeIcon
                )
            }
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!linkHint.isNullOrBlank() && onClick != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = linkHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimens.IconSizeSmall)
                )
            }
        }
    }
}
