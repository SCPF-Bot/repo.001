package com.mlbb.plus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlbb.plus.R
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppStatusBadge
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.components.EmptyState
import com.mlbb.plus.ui.components.SectionHeader
import com.mlbb.plus.ui.components.TierBadge
import com.mlbb.plus.ui.components.tierSemanticColor
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes

private enum class HeroDetailTab(val label: String) {
    OVERVIEW("Overview"),
    COUNTERS("Counters"),
    SYNERGIES("Synergies")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeroDetailScreen(
    hero: Hero,
    onBack: () -> Unit = {},
    onHeroClick: (Hero) -> Unit = {},
    onItemClick: (Item) -> Unit = {}
) {
    var selectedTab by rememberSaveable(hero.id) { mutableStateOf(HeroDetailTab.OVERVIEW) }

    val counters = remember(hero.id) { hero.goodAgainst.mapNotNull { GameData.heroesById[it] } }
    val badMatchups = remember(hero.id) { hero.badAgainst.mapNotNull { GameData.heroesById[it] } }
    val synergies = remember(hero.id) { hero.goodWith.mapNotNull { GameData.heroesById[it] } }
    val coreBuild = remember(hero.id, hero.coreItems) {
        hero.coreItems.filter { it.itemId != 0 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = hero.name,
            subtitle = hero.title.ifBlank { hero.displayRole },
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = AppDimens.MaxContentWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.Space16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)
        ) {
            // Hero Header Card
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                ) {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = AppShapes.Card,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        AssetImage(
                            hero = hero,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            contentDescription = hero.name
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                        ) {
                            Text(
                                text = hero.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            TierBadge(hero.tier)
                        }
                        if (hero.title.isNotBlank()) {
                            Text(
                                text = hero.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${hero.displayRole} • ${hero.lane} • ${"%.1f".format(hero.winRate * 100)}% WR",
                            style = MaterialTheme.typography.labelLarge,
                            color = tierSemanticColor(hero.tier)
                        )
                        if (hero.tierUpdated.isNotBlank()) {
                            Text(
                                text = "Meta snapshot ${hero.tierUpdated}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hero.hasLiveStats) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space6)
                    ) {
                        AppStatusBadge(
                            text = "WIN ${"%.1f".format(hero.winRate * 100)}%",
                            color = MaterialTheme.colorScheme.primary
                        )
                        AppStatusBadge(
                            text = "PICK ${"%.1f".format(hero.pickRate * 100)}%",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        AppStatusBadge(
                            text = "BAN ${"%.1f".format(hero.banRate * 100)}%",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Material 3 Primary Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(AppShapes.Card)
            ) {
                HeroDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("hero_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (selectedTab) {
                HeroDetailTab.OVERVIEW -> {
                    val loreText = hero.loreFirstParagraph.ifBlank { hero.lore }
                    if (loreText.isNotBlank()) {
                        AppCard {
                            SectionHeader(title = "HERO LORE")
                            Text(
                                text = loreText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Source: Mobile Legends Wiki (Fandom)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (hero.specialty.isNotEmpty()) {
                        AppCard {
                            SectionHeader(title = "SPECIALTY")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                            ) {
                                hero.specialty.forEach { tag ->
                                    AppStatusBadge(text = tag, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    if (hero.recommendedSpells.isNotEmpty()) {
                        AppCard {
                            SectionHeader(title = "RECOMMENDED SPELLS")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                            ) {
                                hero.recommendedSpells.forEach { spell ->
                                    AppStatusBadge(text = spell, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }

                    if (coreBuild.isNotEmpty()) {
                        SectionHeader(title = "CORE BUILD")
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                            coreBuild.forEach { build ->
                                val item = GameData.itemsById[build.itemId]
                                    ?: GameData.items.firstOrNull { it.name.equals(build.name.trim(), ignoreCase = true) }
                                if (item != null) {
                                    AppCard(
                                        onClick = { onItemClick(item) },
                                        contentPadding = PaddingValues(AppDimens.Space12),
                                        modifier = Modifier.testTag("hero_core_item_${item.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(44.dp),
                                                shape = AppShapes.Chip,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                AssetImage(
                                                    item = item,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    contentDescription = item.name
                                                )
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (item.priceType == "granted") "Granted upgrade" else "${item.price} Gold",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Icon(
                                                imageVector = AppIcons.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HeroDetailTab.COUNTERS -> {
                    if (hero.matchupDataSource.isNotBlank()) {
                        AppCard {
                            Text(
                                text = listOfNotNull(
                                    hero.matchupDataSource.takeIf { it.isNotBlank() },
                                    hero.matchupDataRank.takeIf { it.isNotBlank() },
                                    hero.matchupDataWindow.takeIf { it.isNotBlank() }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AppCard {
                        SectionHeader(title = "WORKS AGAINST")
                        if (counters.isEmpty()) {
                            Text(
                                text = "No counter data available in heroes.json.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                items(counters, key = { it.id }) { target ->
                                    LinkedHeroTile(hero = target, onClick = { onHeroClick(target) })
                                }
                            }
                        }
                    }

                    AppCard {
                        SectionHeader(title = "BAD AGAINST")
                        if (badMatchups.isEmpty()) {
                            Text(
                                text = "No bad-matchup data available in heroes.json.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                items(badMatchups, key = { it.id }) { target ->
                                    LinkedHeroTile(hero = target, onClick = { onHeroClick(target) })
                                }
                            }
                        }
                    }
                }

                HeroDetailTab.SYNERGIES -> {
                    val partnerFallback = hero.bestPartners.mapNotNull { GameData.heroesById[it] }
                    val partners = if (synergies.isNotEmpty()) synergies else partnerFallback
                    if (partners.isEmpty()) {
                        EmptyState(
                            icon = AppIcons.Person,
                            titleRes = R.string.state_empty_title,
                            messageRes = R.string.state_empty_message
                        )
                    } else {
                        AppCard {
                            SectionHeader(title = "BEST PARTNERS")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                items(partners, key = { it.id }) { partner ->
                                    LinkedHeroTile(hero = partner, onClick = { onHeroClick(partner) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LinkedHeroTile(hero: Hero, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .heightIn(min = AppDimens.TouchTargetMin)
            .clip(AppShapes.Card)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(AppDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = AppShapes.Card,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            AssetImage(
                hero = hero,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                contentDescription = hero.name
            )
        }
        Text(
            text = hero.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
