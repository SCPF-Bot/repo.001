package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.components.FilterPill
import com.mlbb.plus.ui.theme.MLBBPlusColors

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
        t.startsWith("S") -> MLBBPlusColors.TierS
        t.startsWith("A") -> MLBBPlusColors.TierA
        t.startsWith("B") -> MLBBPlusColors.TierB
        else -> MLBBPlusColors.GithubSecondary
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MLBBPlusColors.GithubBg)
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "ITEM EXPLORER",
            color = MLBBPlusColors.GithubText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = "Patch ${GameData.patchNotes.displayVersion} item catalog",
            color = MLBBPlusColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by item name or category", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MLBBPlusColors.GithubText,
                unfocusedTextColor = MLBBPlusColors.GithubText,
                focusedContainerColor = MLBBPlusColors.GithubCard,
                unfocusedContainerColor = MLBBPlusColors.GithubCard,
                focusedBorderColor = MLBBPlusColors.Gold,
                unfocusedBorderColor = MLBBPlusColors.GithubBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Filter by category", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val selected = cat == selectedCat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MLBBPlusColors.GoldBg else MLBBPlusColors.GithubCard)
                        .clickable { selectedCat = cat }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(cat, color = if (selected) MLBBPlusColors.Gold else MLBBPlusColors.GithubText, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            color = MLBBPlusColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Adaptive columns keep portraits readable on compact and wide screens
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(MLBBPlusColors.GithubCard)
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
                        color = MLBBPlusColors.GithubText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        text = "${item.price} Gold",
                        color = MLBBPlusColors.Gold,
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
            .background(MLBBPlusColors.GithubBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MLBBPlusColors.GithubCard)
            ) {
                AssetImage(item = item, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = item.name)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(item.name, color = MLBBPlusColors.GithubText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(item.category, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
                        .background(if (selected) MLBBPlusColors.GoldBg else MLBBPlusColors.GithubCard)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(if (tab == ItemTab.MATCHUPS) "HERO LINKS" else tab.name.replace("_", " "), color = if (selected) MLBBPlusColors.Gold else MLBBPlusColors.GithubText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            ItemTab.OVERVIEW -> {
                if (item.statsDetailed.isNotEmpty()) {
                    Text("Stats", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    item.statsDetailed.forEach { stat ->
                        Text("• ${stat.stat}: ${stat.value}", color = MLBBPlusColors.GithubText, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (relatedItems.isNotEmpty()) {
                    Text("Related Items", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                Text(rel.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            ItemTab.BUILD_PATH -> {
                Text("Build Path", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (buildPathItems.isEmpty()) {
                    Text("No build path in items.json", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
                                Text(comp.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                            if (index < buildPathItems.lastIndex) {
                                Text("→", color = MLBBPlusColors.GithubSecondary, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            ItemTab.MATCHUPS -> {
                Text("Hero Synergies", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (synergyHeroes.isEmpty()) {
                    Text("No hero synergy data for this item.", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
                                Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Effective Against", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (matchupHeroes.isEmpty()) {
                    Text("No matchup data for this item.", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
                                Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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
