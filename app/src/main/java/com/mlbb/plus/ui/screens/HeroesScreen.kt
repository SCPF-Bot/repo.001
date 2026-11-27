package com.mlbb.plus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlbb.plus.R
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppSearchBar
import com.mlbb.plus.ui.components.AppTextButton
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.components.EmptyState
import com.mlbb.plus.ui.components.TierBadge
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes

private enum class HeroSort(val label: String) {
    RECOMMENDED("Recommended"),
    WIN_RATE("Win rate"),
    NAME("Name")
}

internal fun normalizedHeroTier(tier: String): String {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S+") -> "S+"
        t.startsWith("S") -> "S"
        t.startsWith("A") -> "A"
        t.startsWith("B") -> "B"
        else -> t
    }
}

private fun tierRank(tier: String): Int = when (normalizedHeroTier(tier)) {
    "S+" -> 0
    "S" -> 1
    "A" -> 2
    "B" -> 3
    else -> 4
}

@Composable
fun HeroesScreen(
    onHeroClick: (Hero) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("All") }
    var selectedTier by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(HeroSort.RECOMMENDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val allHeroes = remember { GameData.liveHeroes }

    val roles = remember(allHeroes) {
        val heroRoles = allHeroes.flatMap { listOfNotNull(it.role, it.secondaryRole) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        listOf("All") + heroRoles
    }
    val tiers = remember { listOf("All", "S+", "S", "A", "B", "C") }

    val filtered = remember(query, selectedRole, selectedTier, sort, allHeroes) {
        val q = query.trim().lowercase()
        val base = allHeroes.filter { hero ->
            val matchesQuery = q.isBlank() ||
                hero.name.lowercase().contains(q) ||
                hero.title.lowercase().contains(q) ||
                hero.role.lowercase().contains(q)
            val matchesRole = selectedRole == "All" ||
                hero.role.equals(selectedRole, ignoreCase = true) ||
                hero.secondaryRole?.equals(selectedRole, ignoreCase = true) == true
            val matchesTier = selectedTier == "All" ||
                normalizedHeroTier(hero.tier) == selectedTier
            matchesQuery && matchesRole && matchesTier
        }
        when (sort) {
            HeroSort.WIN_RATE -> base.sortedByDescending { it.winRate }
            HeroSort.NAME -> base.sortedBy { it.name }
            HeroSort.RECOMMENDED -> base.sortedWith(
                compareBy({ tierRank(it.tier) }, { -it.winRate })
            )
        }
    }

    val hasFilters = query.isNotBlank() || selectedRole != "All" ||
        selectedTier != "All" || sort != HeroSort.RECOMMENDED

    fun clearFilters() {
        query = ""
        selectedRole = "All"
        selectedTier = "All"
        sort = HeroSort.RECOMMENDED
    }

    val shortPatch = GameData.patchNotes.shortVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = "Hero Explorer",
            subtitle = "Patch $shortPatch • ${filtered.size} heroes",
            leadingIcon = AppIcons.Person
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = AppDimens.MaxContentWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
        ) {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholderRes = R.string.search_heroes_hint,
                modifier = Modifier.testTag("heroes_search_input")
            )

            // Role FilterChips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                roles.forEach { role ->
                    val selected = selectedRole == role
                    FilterChip(
                        selected = selected,
                        onClick = { selectedRole = role },
                        label = { Text(role, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier
                            .heightIn(min = AppDimens.TouchTargetMin)
                            .testTag("hero_role_chip_${role.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Tier FilterChips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tiers.forEach { tier ->
                    val selected = selectedTier == tier
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTier = tier },
                        label = {
                            Text(
                                text = if (tier == "All") "All Tiers" else "Tier $tier",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = AppDimens.TouchTargetMin)
                            .testTag("hero_tier_chip_${tier.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Count + Sort + Clear row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filtered.size} of ${allHeroes.size} heroes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4)
                ) {
                    if (hasFilters) {
                        AppTextButton(
                            onClick = ::clearFilters,
                            modifier = Modifier.testTag("heroes_clear_filters")
                        ) {
                            Text(
                                text = stringResource(R.string.action_clear),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        AppTextButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("heroes_sort_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppDimens.IconSizeSmall)
                            )
                            Text(
                                text = " ${sort.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            HeroSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sort == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sort == option) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sort = option
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = AppIcons.Person,
                        titleRes = R.string.state_no_results_title,
                        messageRes = R.string.state_no_heroes_message,
                        actionLabelRes = if (hasFilters) R.string.action_clear_filters else null,
                        onAction = if (hasFilters) ::clearFilters else null
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    contentPadding = PaddingValues(vertical = AppDimens.Space4),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space10),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filtered, key = { it.id }) { hero ->
                        HeroGridTile(hero = hero, onClick = { onHeroClick(hero) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroGridTile(hero: Hero, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(AppDimens.Space2)
            .testTag("hero_card_${hero.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = AppShapes.Card,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(Modifier.fillMaxSize()) {
                AssetImage(
                    hero = hero,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    contentDescription = hero.name
                )
                TierBadge(
                    tier = hero.tier,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(AppDimens.Space4)
                )
            }
        }
        Text(
            text = hero.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppDimens.Space4)
        )
    }
}

@Composable
fun HeroesScreenContent(onHeroClick: (Hero) -> Unit = {}) {
    HeroesScreen(onHeroClick = onHeroClick)
}
