package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ITEMS — MAXIMIZED for items.json (40 items, IDs 1001-6001)
// Categories now include Jungle (6000) — old code only had 5 cats, missed Jungle.
// Detail shows EVERY JSON field: sellPrice, statsDetailed, passive, active, lore, buildPath, recommendedFor, tags.
// Counters/synergies resolve via GameData.itemsById / heroesById (real IDs, e.g. DHS 1002 counters Blade Armor 3001).

@Composable
fun ItemsScreen(onItemClick: (Item) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    val cats = listOf<String?>(null, "Attack", "Magic", "Defense", "Movement", "Roam", "Jungle")
    val filtered = GameData.items.filter {
        (selectedCat == null || it.category == selectedCat) && (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }.sortedWith(compareByDescending<Item> { it.tier == "S" }.thenBy { it.price })
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🎒", fontSize = 16.sp)
                Text("ITEM EXPLORER", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
            }
        }
        Text("${filtered.size} items • Patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }} • IDs 1000-6000", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search items... (e.g. Demon Hunter Sword)", color = MLBBPlusColors.GithubSecondary) },
            leadingIcon = { Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Text("✕", color = MLBBPlusColors.GithubSecondary, fontSize = 14.sp) } },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MLBBPlusColors.Gold.copy(alpha = 0.7f), unfocusedBorderColor = MLBBPlusColors.GithubBorder, focusedTextColor = MLBBPlusColors.GithubText, unfocusedTextColor = MLBBPlusColors.GithubText, cursorColor = MLBBPlusColors.Gold),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            items(cats) { cat ->
                FilterChip(selected = selectedCat == cat, onClick = { selectedCat = cat }, label = { Text(cat ?: "All", fontWeight = if (selectedCat == cat) FontWeight.Bold else FontWeight.Normal) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MLBBPlusColors.Gold, selectedLabelColor = Color.Black), border = FilterChipDefaults.filterChipBorder(true, selectedCat == cat, borderColor = MLBBPlusColors.GithubBorder, selectedBorderColor = MLBBPlusColors.Gold))
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No items found — check items.json", color = MLBBPlusColors.GithubSecondary) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MLBBPlusColors.GithubCard).clickable { onItemClick(item) }.padding(4.dp)) {
                        ItemImage(item = item, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface))
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MLBBPlusColors.GithubSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                        Text("#${item.id} • ${item.price} Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemDetailScreen(item: Item, onBack: () -> Unit, onItemClick: (Item) -> Unit, onHeroClick: (Hero) -> Unit) {
    var activeTab by remember { mutableStateOf(DetailTab.OVERVIEW) }
    val itemsById = GameData.itemsById
    val heroesById = GameData.heroesById
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            ItemImage(item = item, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MLBBPlusColors.GithubBg.copy(alpha = 0.3f), MLBBPlusColors.GithubBg))))
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).clickable { onBack() }.clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("← Back", color = MLBBPlusColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(item.name, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("${item.category} • Tier ${item.tier} • #${item.id} • ${item.price} Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().background(MLBBPlusColors.GithubCard).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val labels = listOf("Price", "Sell", "Category")
            val values = listOf("${item.price}", "${item.sellPrice}", item.category)
            values.forEachIndexed { i, v -> if (i > 0) Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v, color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(labels[i], color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp) } }
        }
        PrimaryTabRow(selectedTabIndex = activeTab.ordinal, containerColor = MLBBPlusColors.GithubCard, contentColor = MLBBPlusColors.Gold) {
            DetailTab.entries.forEach { tab -> Tab(selected = activeTab == tab, onClick = { activeTab = tab }, text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (activeTab) {
                DetailTab.OVERVIEW -> ItemOverviewTab(item, onItemClick)
                DetailTab.COUNTERS -> ItemRelationTab("ITEMS THAT COUNTER ${item.name.uppercase()}", item.counteredBy.mapNotNull { itemsById[it] }, "${item.name.uppercase()} COUNTERS", item.counters.mapNotNull { itemsById[it] }, onItemClick)
                DetailTab.SYNERGIES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("BEST PARTNERS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.synergiesHeroes.mapNotNull { heroesById[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
                        if (item.synergiesHeroes.isEmpty()) Text("No data — fill synergiesHeroes in items.json", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
                        Text("WORKS BEST AGAINST", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.worksAgainstHeroes.mapNotNull { heroesById[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
                        if (item.worksAgainstHeroes.isEmpty()) Text("No data — fill worksAgainstHeroes in items.json", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemOverviewTab(item: Item, onItemClick: (Item) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FULL DESCRIPTION", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(item.description.ifBlank { "No description" }, color = MLBBPlusColors.GithubText, fontSize = 13.sp)
        if (item.passiveName != null) { Text("PASSIVE — ${item.passiveName}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.passiveDescription ?: "", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        if (item.activeName != null) { Text("ACTIVE — ${item.activeName}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.activeDescription ?: "", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        Text("STATS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        item.stats.forEach { Text("• $it", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        if (item.statsDetailed.isNotEmpty()) item.statsDetailed.forEach { Text("${it.stat}: ${it.value}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        Text("Price: ${item.price} Gold • Sell: ${item.sellPrice} Gold", color = MLBBPlusColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (item.recommendedFor.isNotEmpty()) { Text("RECOMMENDED FOR", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.recommendedFor.joinToString(", "), color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        Text("LORE", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(item.lore ?: "No lore for this item", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Text("BUILD PATH", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (item.buildPath.isEmpty()) { Text("No build path", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item.buildPath.forEachIndexed { i, comp -> if (i > 0) Text("→", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)).clickable { GameData.items.find { it.name == comp }?.let { onItemClick(it) } }.padding(horizontal = 10.dp, vertical = 8.dp)) { Text(comp, color = MLBBPlusColors.GithubText, fontSize = 12.sp) } }
                Text("→", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.15f)).border(1.dp, MLBBPlusColors.Gold, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) { Text(item.name, color = MLBBPlusColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Text("Combine ${item.buildPath.joinToString(" + ")} to craft • Total: ${item.price} Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        }
        if (item.tags.isNotEmpty()) Text("Tags: ${item.tags.joinToString(", ")}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ItemRelationTab(sectionA: String, itemsA: List<Item>, sectionB: String, itemsB: List<Item>, onItemClick: (Item) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(sectionA, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsA.take(5).forEach { it -> ItemPortraitSmall(it, onItemClick) } }
        if (itemsA.isEmpty()) Text("No data", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
        Text(sectionB, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsB.take(5).forEach { it -> ItemPortraitSmall(it, onItemClick) } }
        if (itemsB.isEmpty()) Text("No data", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ItemPortraitSmall(item: Item, onClick: (Item) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(item) }) {
        ItemImage(item = item, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)))
        Text(item.name, color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
