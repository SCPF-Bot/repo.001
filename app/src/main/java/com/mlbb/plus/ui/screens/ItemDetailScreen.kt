package com.mlbb.plus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlbb.plus.R
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppIcons
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

private enum class ItemDetailTab(val label: String) {
    OVERVIEW("Overview"),
    BUILD_PATH("Build Path"),
    HERO_LINKS("Hero Links")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    item: Item,
    onBack: () -> Unit = {},
    onItemClick: (Item) -> Unit = {},
    onHeroClick: (Hero) -> Unit = {}
) {
    var selectedTab by rememberSaveable(item.id) { mutableStateOf(ItemDetailTab.OVERVIEW) }

    val buildPathItems = remember(item.id) {
        val byIds = item.buildPathIds.mapNotNull { GameData.itemsById[it] }
        if (byIds.isNotEmpty()) byIds else item.buildPath.mapNotNull { comp ->
            GameData.items.firstOrNull { it.name.equals(comp.trim(), ignoreCase = true) }
        }
    }
    val buildsIntoItems = remember(item.id) {
        item.buildsInto.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" }
    }
    val goodAgainstItems = remember(item.id) {
        item.goodAgainstItemIds.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" }
    }
    val badAgainstItems = remember(item.id) {
        item.badAgainstItemIds.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" }
    }
    val relatedItems = remember(item.id) { (goodAgainstItems + badAgainstItems).distinctBy { it.id } }
    val synergyHeroes = remember(item.id) { item.goodWithHeroes.mapNotNull { GameData.heroesById[it] } }
    val matchupHeroes = remember(item.id) { item.goodAgainstHeroes.mapNotNull { GameData.heroesById[it] } }
    val badMatchupHeroes = remember(item.id) { item.badAgainstHeroes.mapNotNull { GameData.heroesById[it] } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = item.name,
            subtitle = item.category.ifBlank { "Equipment" },
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
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = AppShapes.Card,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        AssetImage(
                            item = item,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            contentDescription = item.name
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
                                text = item.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            TierBadge(item.tier)
                        }
                        if (item.category.isNotBlank()) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (item.priceType == "granted") "Granted upgrade"
                            else "Tier ${item.tier.trim().uppercase()} • ${item.price} Gold",
                            style = MaterialTheme.typography.labelLarge,
                            color = tierSemanticColor(item.tier)
                        )
                        if (item.priceNote.isNotBlank()) {
                            Text(
                                text = item.priceNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(AppShapes.Card)
            ) {
                ItemDetailTab.entries.forEach { tab ->
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
                        modifier = Modifier.testTag("item_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (selectedTab) {
                ItemDetailTab.OVERVIEW -> {
                    val hasOverviewContent = item.statsDetailed.isNotEmpty() ||
                        item.stats.isNotEmpty() ||
                        !item.passiveDescription.isNullOrBlank() ||
                        !item.activeDescription.isNullOrBlank() ||
                        relatedItems.isNotEmpty()

                    if (!hasOverviewContent) {
                        EmptyState(
                            icon = AppIcons.Apps,
                            titleRes = R.string.state_empty_title,
                            messageRes = R.string.state_empty_message
                        )
                    } else {
                        if (item.statsDetailed.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "STATS")
                                item.statsDetailed.forEach { stat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stat.stat,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stat.value,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else if (item.stats.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "STATS")
                                item.stats.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (!item.passiveDescription.isNullOrBlank()) {
                            AppCard {
                                SectionHeader(title = item.passiveName?.takeIf { it.isNotBlank() }?.uppercase() ?: "UNIQUE PASSIVE")
                                Text(
                                    text = item.passiveDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!item.activeDescription.isNullOrBlank()) {
                            AppCard {
                                SectionHeader(title = item.activeName?.takeIf { it.isNotBlank() }?.uppercase() ?: "ACTIVE SKILL")
                                Text(
                                    text = item.activeDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (relatedItems.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "RELATED ITEMS")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(relatedItems, key = { it.id }) { rel ->
                                        LinkedItemTile(item = rel, onClick = { onItemClick(rel) })
                                    }
                                }
                            }
                        }
                    }
                }

                ItemDetailTab.BUILD_PATH -> {
                    if (buildPathItems.isEmpty() && buildsIntoItems.isEmpty()) {
                        EmptyState(
                            icon = AppIcons.Apps,
                            titleRes = R.string.state_empty_title,
                            messageRes = R.string.state_empty_message
                        )
                    } else {
                        AppCard {
                            SectionHeader(title = "BUILD PATH")
                            if (buildPathItems.isEmpty()) {
                                Text(
                                    text = "Base tier item — no component prerequisites.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    buildPathItems.forEachIndexed { index, comp ->
                                        LinkedItemTile(item = comp, onClick = { onItemClick(comp) })
                                        if (index < buildPathItems.lastIndex) {
                                            Icon(
                                                imageVector = AppIcons.ArrowForward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(AppDimens.IconSizeSmall)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (buildsIntoItems.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "BUILDS INTO")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(buildsIntoItems, key = { it.id }) { target ->
                                        LinkedItemTile(item = target, onClick = { onItemClick(target) })
                                    }
                                }
                            }
                        }
                    }
                }

                ItemDetailTab.HERO_LINKS -> {
                    val hasLinks = synergyHeroes.isNotEmpty() || matchupHeroes.isNotEmpty() ||
                        badMatchupHeroes.isNotEmpty() || goodAgainstItems.isNotEmpty() || badAgainstItems.isNotEmpty()

                    if (!hasLinks) {
                        EmptyState(
                            icon = AppIcons.Person,
                            titleRes = R.string.state_empty_title,
                            messageRes = R.string.state_empty_message
                        )
                    } else {
                        AppCard {
                            SectionHeader(title = "HERO SYNERGIES")
                            if (synergyHeroes.isEmpty()) {
                                Text(
                                    text = "No hero synergy data for this item.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(synergyHeroes, key = { it.id }) { hero ->
                                        LinkedHeroTile(hero = hero, onClick = { onHeroClick(hero) })
                                    }
                                }
                            }
                        }

                        AppCard {
                            SectionHeader(title = "EFFECTIVE AGAINST")
                            if (matchupHeroes.isEmpty()) {
                                Text(
                                    text = "No matchup data for this item.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(matchupHeroes, key = { it.id }) { hero ->
                                        LinkedHeroTile(hero = hero, onClick = { onHeroClick(hero) })
                                    }
                                }
                            }
                        }

                        if (badMatchupHeroes.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "BAD AGAINST HEROES")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(badMatchupHeroes, key = { it.id }) { hero ->
                                        LinkedHeroTile(hero = hero, onClick = { onHeroClick(hero) })
                                    }
                                }
                            }
                        }

                        if (goodAgainstItems.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "GOOD AGAINST ITEMS")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(goodAgainstItems, key = { it.id }) { linked ->
                                        LinkedItemTile(item = linked, onClick = { onItemClick(linked) })
                                    }
                                }
                            }
                        }

                        if (badAgainstItems.isNotEmpty()) {
                            AppCard {
                                SectionHeader(title = "BAD AGAINST ITEMS")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                    items(badAgainstItems, key = { it.id }) { linked ->
                                        LinkedItemTile(item = linked, onClick = { onItemClick(linked) })
                                    }
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
private fun LinkedItemTile(item: Item, onClick: () -> Unit) {
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
                item = item,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                contentDescription = item.name
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
