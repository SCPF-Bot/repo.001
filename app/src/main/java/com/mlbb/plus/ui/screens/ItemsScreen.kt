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
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes

private enum class ItemSort(val label: String) {
    RECOMMENDED("Recommended"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low")
}

private fun itemTierRank(tier: String): Int {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> 0
        t.startsWith("A") -> 1
        t.startsWith("B") -> 2
        else -> 3
    }
}

@Composable
fun ItemsScreen(
    onItemClick: (Item) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCat by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(ItemSort.RECOMMENDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val activeItems = remember { GameData.items.filter { it.status == "active" } }
    val categories = remember(activeItems) {
        val cats = activeItems.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All") + cats
    }

    val filtered = remember(query, selectedCat, sort, activeItems) {
        val q = query.trim().lowercase()
        val base = activeItems.filter { item ->
            val matchesQuery = q.isBlank() ||
                item.name.lowercase().contains(q) ||
                item.category.lowercase().contains(q)
            val matchesCat = selectedCat == "All" ||
                item.category.equals(selectedCat, ignoreCase = true)
            matchesQuery && matchesCat
        }
        when (sort) {
            ItemSort.PRICE_LOW -> base.sortedWith(compareBy({ it.priceType == "granted" }, { it.price }))
            ItemSort.PRICE_HIGH -> base.sortedWith(compareBy({ it.priceType == "granted" }, { -it.price }))
            ItemSort.RECOMMENDED -> base.sortedWith(
                compareBy({ itemTierRank(it.tier) }, { it.priceType == "granted" }, { it.price })
            )
        }
    }

    val hasFilters = query.isNotBlank() || selectedCat != "All" || sort != ItemSort.RECOMMENDED
    fun clearFilters() {
        query = ""
        selectedCat = "All"
        sort = ItemSort.RECOMMENDED
    }

    val shortPatch = GameData.patchNotes.shortVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = "Item Explorer",
            subtitle = "Patch $shortPatch • ${filtered.size} items",
            leadingIcon = AppIcons.Apps
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
                placeholderRes = R.string.search_items_hint,
                modifier = Modifier.testTag("items_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { cat ->
                    val selected = selectedCat == cat
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCat = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier
                            .heightIn(min = AppDimens.TouchTargetMin)
                            .testTag("item_category_chip_${cat.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filtered.size} of ${activeItems.size} items",
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
                            modifier = Modifier.testTag("items_clear_filters")
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
                            modifier = Modifier.testTag("items_sort_button")
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
                            ItemSort.entries.forEach { option ->
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
                        icon = AppIcons.Apps,
                        titleRes = R.string.state_no_results_title,
                        messageRes = R.string.state_no_items_message,
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
                    items(filtered, key = { it.id }) { item ->
                        ItemGridTile(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemGridTile(item: Item, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(AppDimens.Space2)
            .testTag("item_card_${item.id}"),
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppDimens.Space4)
        )
        Text(
            text = if (item.priceType == "granted") "Granted" else "${item.price}g",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ItemsScreenContent(onItemClick: (Item) -> Unit = {}) {
    ItemsScreen(onItemClick = onItemClick)
}
