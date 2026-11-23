package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppHeader
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.components.SearchField
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.components.FilterPill
import com.mlbb.plus.ui.theme.AppUiColors

private enum class ItemTab { OVERVIEW, BUILD_PATH, MATCHUPS }
private enum class ItemSort { RECOMMENDED, PRICE_LOW, PRICE_HIGH }

private fun tierRank(tier: String): Int {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> 0
        t.startsWith("A") -> 1
        t.startsWith("B") -> 2
        else -> 3
    }
}

private fun tierColor(tier: String): Color {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> AppUiColors.TierS
        t.startsWith("A") -> AppUiColors.TierA
        t.startsWith("B") -> AppUiColors.TierB
        else -> AppUiColors.GithubSecondary
    }
}

@Composable
fun ItemsScreen(
    onItemClick: (Item) -> Unit = {},
    onHeroClick: (Hero) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCat by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(ItemSort.RECOMMENDED) }

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
            ItemSort.PRICE_LOW -> base.sortedBy { it.price }
            ItemSort.PRICE_HIGH -> base.sortedByDescending { it.price }
            ItemSort.RECOMMENDED -> base.sortedWith(compareBy({ tierRank(it.tier) }, { it.price }))
        }
    }

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        AppHeader("ITEM EXPLORER", "Patch ${GameData.patchNotes.displayVersion} • ${filtered.size} active items", AppIcons.Apps)
        Column(
            Modifier.fillMaxSize().widthIn(max = 960.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("FIND AN ITEM")
            SearchField(query, { query = it }, "Search by item name or category")
        SectionLabel("FILTER BY CATEGORY")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                FilterPill(cat, cat == selectedCat, onClick = { selectedCat = cat })
            }
        }

        SectionLabel("SORT ITEMS", Modifier.padding(top = 14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ItemSort.RECOMMENDED to "Recommended",
                ItemSort.PRICE_LOW to "Price: low to high",
                ItemSort.PRICE_HIGH to "Price: high to low"
            ).forEach { (option, label) ->
                FilterPill(label, sort == option, onClick = { sort = option })
            }
        }

        Text(
            text = "${filtered.size} of ${activeItems.size} active items",
            color = AppUiColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )

        // Adaptive columns keep item portraits readable on compact and wide screens
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(filtered, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppUiColors.GithubCard)
                            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
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
                        color = AppUiColors.GithubText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        text = "${item.price} Gold",
                        color = AppUiColors.Gold,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }
    }
}

@Composable
fun ItemDetailScreen(
    item: Item,
    onBack: () -> Unit = {},
    onItemClick: (Item) -> Unit = {},
    onHeroClick: (Hero) -> Unit = {}
) {
    // Reset tab when item changes
    var selectedTab by remember(item.id) { mutableStateOf(ItemTab.OVERVIEW) }

    val buildPathItems = remember(item.id) {
        item.buildPath.mapNotNull { comp ->
            GameData.items.firstOrNull { it.name.equals(comp.trim(), ignoreCase = true) }
        }
    }
    val relatedItems = remember(item.id) {
        (item.counters + item.counteredBy).distinct()
            .filter { it != item.id }
            .mapNotNull { GameData.itemsById[it] }
            .filter { it.status == "active" }
    }
    val synergyHeroes = remember(item.id) {
        item.synergiesHeroes.mapNotNull { GameData.heroesById[it] }
    }
    val matchupHeroes = remember(item.id) {
        item.worksAgainstHeroes.mapNotNull { GameData.heroesById[it] }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppUiColors.GithubBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppUiColors.GithubCard)
            ) {
                AssetImage(item = item, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = item.name)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(item.name, color = AppUiColors.GithubText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(item.category, color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                Text(
                    text = "Tier ${item.tier.trim().uppercase()} • ${item.price} Gold",
                    color = tierColor(item.tier),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ItemTab.values().forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .semantics { role = Role.Tab; this.selected = selected }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AppUiColors.GoldBg else AppUiColors.GithubCard)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(if (tab == ItemTab.MATCHUPS) "HERO LINKS" else tab.name.replace("_", " "), color = if (selected) AppUiColors.Gold else AppUiColors.GithubText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            ItemTab.OVERVIEW -> {
                if (item.statsDetailed.isNotEmpty()) {
                    Text("Stats", color = AppUiColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    item.statsDetailed.forEach { stat ->
                        Text("• ${stat.stat}: ${stat.value}", color = AppUiColors.GithubText, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (relatedItems.isNotEmpty()) {
                    Text("Related Items", color = AppUiColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(relatedItems, key = { it.id }) { rel ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { onItemClick(rel) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(item = rel, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = rel.name)
                                }
                                Text(rel.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            ItemTab.BUILD_PATH -> {
                Text("Build Path", color = AppUiColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (buildPathItems.isEmpty()) {
                    Text("No build path in items.json", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        buildPathItems.forEachIndexed { index, comp ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { onItemClick(comp) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(item = comp, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = comp.name)
                                }
                                Text(comp.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                            if (index < buildPathItems.lastIndex) {
                                Text("→", color = AppUiColors.GithubSecondary, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            ItemTab.MATCHUPS -> {
                Text("Hero Synergies", color = AppUiColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (synergyHeroes.isEmpty()) {
                    Text("No hero synergy data for this item.", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(synergyHeroes, key = { it.id }) { hero ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { onHeroClick(hero) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(hero = hero, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = hero.name)
                                }
                                Text(hero.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Effective Against", color = AppUiColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (matchupHeroes.isEmpty()) {
                    Text("No matchup data for this item.", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(matchupHeroes, key = { it.id }) { hero ->
                            Column(
                                modifier = Modifier.width(56.dp).clickable { onHeroClick(hero) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(hero = hero, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = hero.name)
                                }
                                Text(hero.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemsScreenContent(onItemClick: (Item) -> Unit = {}) {
    ItemsScreen(onItemClick = onItemClick)
}
