package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.FilterPill
import com.mlbb.plus.ui.components.ScreenTitleBar
import com.mlbb.plus.ui.components.SearchField
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// plus/ui/screens/ItemsScreen.kt  ->  FIXED
// ------------------------------------------------------------
// FIXES:
//  [1] CRITICAL - Detail tab stuck across items (same bug as Heroes).
//      Old: remember { OVERVIEW } in ItemDetailScreen. Tap a related
//      item (build path / counters) -> same composable, new item,
//      old tab kept. Fix: remember(item.id).
//  [2] Tier sort was wrong: compareByDescending { tier == "S" }
//      thenBy price mixed A and B together by price. Fix: tier
//      rank S=0, A=1, B=2 (and anything else last), then price.
//  [3] Relation tabs showed only first 5, no way to see rest.
//      Old: itemsA.take(5) in a plain Row. Fix: LazyRow with ALL
//      items, key = id, no take(5).
//  [4] Build path Row overflow: 3 components + arrows overflow a
//      320dp phone and get clipped. Fix: horizontalScroll Row.
//  [5] Build path lookup by exact name: GameData.items.find {
//      it.name == comp }. A typo / case / whitespace difference
//      makes the tap do nothing. Fix: equals(..., ignoreCase=true)
//      + trim(), firstOrNull.
//  [6] filtered list rebuilt on every recomposition.
//      Fix: remember(query, selectedCat).
//  [7] Patch fallback duplicated. Fix: displayVersion.
// ============================================================

private fun tierRank(tier: String): Int = when (tier.uppercase().trim()) {
    "S" -> 0
    "A" -> 1
    "B" -> 2
    else -> 3
}

@Composable
fun ItemsScreen(onItemClick: (Item) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var guideOnly by remember { mutableStateOf(false) }
    val patchItemIds = remember(GameData.patchNotes) { GameData.patchNotes.originalServer.itemChanges.map { it.itemId }.toSet() }
    val cats = listOf<String?>(null, "Attack", "Magic", "Defense", "Movement", "Roam", "Jungle")
    // FIX [6]: recompute only when inputs change.
    val filtered = remember(query, selectedCat, guideOnly, GameData.items) {
        GameData.items.filter {
            (!guideOnly || GameData.guideRefsForItem(it.name).isNotEmpty()) &&
                (selectedCat == null || it.category == selectedCat) && (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }.sortedWith(compareBy<Item> { tierRank(it.tier) }.thenBy { it.price })
    }
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        ScreenTitleBar("ITEM EXPLORER", AppIcons.Apps)
        Text("${filtered.size} items \u2022 Patch ${GameData.patchNotes.displayVersion} \u2022 IDs 1000-6000", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        SearchField(query = query, onQueryChange = { query = it }, placeholder = "Search items\u2026")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            items(cats) { cat ->
                FilterPill(label = cat ?: "All", selected = selectedCat == cat, onClick = { selectedCat = cat })
            }
            // Items that the role guides (jungle / mid / gold / exp *_guide.json) list as core items.
            if (GameData.guides.values.any { g -> g.types.any { it.coreItems.isNotEmpty() } }) item {
                FilterPill(label = "In role guides", selected = guideOnly, onClick = { guideOnly = !guideOnly })
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No items match these filters", color = MLBBPlusColors.GithubSecondary) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    val cardShape = RoundedCornerShape(16.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(cardShape).background(MLBBPlusColors.GithubCard)
                            .border(1.dp, MLBBPlusColors.GithubBorder.copy(alpha = 0.7f), cardShape)
                            .clickable { onItemClick(item) }.padding(8.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            ItemImage(item = item, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MLBBPlusColors.GithubSurface))
                            if (item.id in patchItemIds) {
                                Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Orange).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("PATCH", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MLBBPlusColors.GithubText.copy(alpha = 0.85f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("${item.price} Gold", color = MLBBPlusColors.Gold.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ItemDetailScreen(item: Item, onBack: () -> Unit, onItemClick: (Item) -> Unit, onHeroClick: (Hero) -> Unit) {
    // FIX [1]: reset tab when item changes.
    var activeTab by remember(item.id) { mutableStateOf(DetailTab.OVERVIEW) }
    val itemsById = GameData.itemsById
    val heroesById = GameData.heroesById
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            ItemImage(item = item, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MLBBPlusColors.GithubBg.copy(alpha = 0.3f), MLBBPlusColors.GithubBg))))
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).size(44.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = MLBBPlusColors.Gold) }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(item.name, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("${item.category} \u2022 Tier ${item.tier} \u2022 #${item.id} \u2022 ${item.price} Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
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
                DetailTab.OVERVIEW -> ItemOverviewTab(item, onItemClick, onHeroClick)
                DetailTab.COUNTERS -> ItemRelationTab("ITEMS THAT COUNTER ${item.name.uppercase()}", item.counteredBy.mapNotNull { itemsById[it] }, "${item.name.uppercase()} COUNTERS", item.counters.mapNotNull { itemsById[it] }, onItemClick)
                DetailTab.SYNERGIES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("BEST PARTNERS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        val partners = item.synergiesHeroes.mapNotNull { heroesById[it] }
                        if (partners.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(partners, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) } }
                        } else if (item.synergiesHeroes.isEmpty()) {
                            Text("No hero synergy data for this item yet", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
                        Text("WORKS BEST AGAINST", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        val against = item.worksAgainstHeroes.mapNotNull { heroesById[it] }
                        if (against.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(against, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) } }
                        } else if (item.worksAgainstHeroes.isEmpty()) {
                            Text("No \"works against\" data for this item yet", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemOverviewTab(item: Item, onItemClick: (Item) -> Unit, onHeroClick: (Hero) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- This patch (patch_notes.json itemChanges) ----
        GameData.patchChangeForItem(item.id)?.let { change ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MLBBPlusColors.Orange.copy(alpha = 0.10f))
                    .border(1.dp, MLBBPlusColors.Orange.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("PATCH ${GameData.patchNotes.displayVersion} \u2022 ${change.type.uppercase().ifBlank { "CHANGE" }}", color = MLBBPlusColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                change.changes.forEach { Text("\u2022 $it", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
            }
        }
        // ---- Role guides that list this item as a core item ----
        val guideRefs = GameData.guideRefsForItem(item.name)
        if (guideRefs.isNotEmpty()) {
            Text("CORE IN OVERLAY GUIDES", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                guideRefs.forEach { ref ->
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.12f)).border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("${ref.role} \u2022 ${ref.typeLabel}", color = MLBBPlusColors.Gold, fontSize = 12.sp)
                    }
                }
            }
        }
        // ---- Heroes whose core build (heroes.json) includes it ----
        val coreOn = GameData.heroesUsingItem(item.id)
        if (coreOn.isNotEmpty()) {
            Text("CORE ON HEROES", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(coreOn, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) } }
        }
        Text("FULL DESCRIPTION", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(item.description.ifBlank { "No description" }, color = MLBBPlusColors.GithubText, fontSize = 13.sp)
        if (item.passiveName != null) { Text("PASSIVE \u2014 ${item.passiveName}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.passiveDescription ?: "", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        if (item.activeName != null) { Text("ACTIVE \u2014 ${item.activeName}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.activeDescription ?: "", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        Text("STATS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        item.stats.forEach { Text("\u2022 $it", color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        if (item.statsDetailed.isNotEmpty()) item.statsDetailed.forEach { Text("${it.stat}: ${it.value}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        Text("Price: ${item.price} Gold \u2022 Sell: ${item.sellPrice} Gold", color = MLBBPlusColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (item.recommendedFor.isNotEmpty()) { Text("RECOMMENDED FOR", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(item.recommendedFor.joinToString(", "), color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        Text("LORE", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(item.lore ?: "No lore for this item", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Text("BUILD PATH", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (item.buildPath.isEmpty()) { Text("No build path", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) } else {
            // FIX [4]: horizontal scroll, not clipped Row.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item.buildPath.forEachIndexed { i, comp ->
                    if (i > 0) Text("\u2192", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)).clickable {
                        // FIX [5]: case-insensitive + trim, not exact ==.
                        GameData.items.firstOrNull { it.name.equals(comp.trim(), ignoreCase = true) }?.let { onItemClick(it) }
                    }.padding(horizontal = 10.dp, vertical = 8.dp)) { Text(comp, color = MLBBPlusColors.GithubText, fontSize = 12.sp) }
                }
                Text("\u2192", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.15f)).border(1.dp, MLBBPlusColors.Gold, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) { Text(item.name, color = MLBBPlusColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Text("Combine ${item.buildPath.joinToString(" + ")} to craft \u2022 Final: ${item.price} Gold", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
        }
        if (item.tags.isNotEmpty()) Text("Tags: ${item.tags.joinToString(", ")}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ItemRelationTab(sectionA: String, itemsA: List<Item>, sectionB: String, itemsB: List<Item>, onItemClick: (Item) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(sectionA, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        // FIX [3]: LazyRow with ALL items, not take(5) in a Row.
        if (itemsA.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(itemsA, key = { it.id }) { it -> ItemPortraitSmall(it, onItemClick) } }
        } else {
            Text("No counter data for this item yet", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
        Text(sectionB, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (itemsB.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(itemsB, key = { it.id }) { it -> ItemPortraitSmall(it, onItemClick) } }
        } else {
            Text("No counter data for this item yet", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ItemPortraitSmall(item: Item, onClick: (Item) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(item) }) {
        ItemImage(item = item, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)))
        Text(item.name, color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
