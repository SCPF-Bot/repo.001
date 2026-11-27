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
import androidx.compose.material3.TextButton
import com.mlbb.plus.ui.components.AppHeader
import com.mlbb.plus.ui.components.BarDivider
import com.mlbb.plus.ui.components.DetailBackBar
import com.mlbb.plus.ui.components.CompactDropdown
import com.mlbb.plus.ui.components.CompactSearchBox
import com.mlbb.plus.ui.components.FilterBarCount
import com.mlbb.plus.ui.components.FilterBarLabel
import com.mlbb.plus.ui.components.OneBarFilter
import com.mlbb.plus.ui.components.SubHeading
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
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
            ItemSort.PRICE_LOW -> base.sortedWith(compareBy({ it.priceType == "granted" }, { it.price }))
            ItemSort.PRICE_HIGH -> base.sortedWith(compareBy({ it.priceType == "granted" }, { -it.price }))
            ItemSort.RECOMMENDED -> base.sortedWith(compareBy({ tierRank(it.tier) }, { it.priceType == "granted" }, { it.price }))
        }
    }

    // Short patch — same rule as Heroes/Home: displayVersion can carry a data stamp
    // ("2.2.23-tier-2026-11-23"). shortVersion -> "2.2.23".
    val shortPatch = GameData.patchNotes.shortVersion

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        AppHeader("ITEM EXPLORER", "Patch $shortPatch • ${filtered.size} items", AppIcons.Apps)
        Column(
            Modifier.fillMaxSize().widthIn(max = 960.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ONE BAR — search | Filter by [category] | Sort by [sort]
            // Single 52dp container from UiKit.kt (ui/components).
            // The Row INSIDE scrolls horizontally, so on a 360dp phone the bar
            // is wider than the screen but nothing is clipped.
            // Was: 160dp SearchField + 2 CompactDropdowns + ✕ Box in a bare Row.
            val hasItemFilters = query.isNotBlank() || selectedCat != "All" ||
                sort != ItemSort.RECOMMENDED
            val sortLabels = mapOf(
                ItemSort.RECOMMENDED to "Recommended",
                ItemSort.PRICE_LOW to "Price: low to high",
                ItemSort.PRICE_HIGH to "Price: high to low"
            )
            OneBarFilter {
                    CompactSearchBox(query, { query = it }, "Search items")
                    BarDivider()
                    FilterBarLabel("Filter by")
                    CompactDropdown(
                        display = selectedCat,
                        options = categories,
                        displayFor = { it },
                        onSelect = { selectedCat = it }
                    )
                    if (hasItemFilters) {
                        TextButton(
                            onClick = {
                                query = ""
                                selectedCat = "All"
                                sort = ItemSort.RECOMMENDED
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Clear", color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterBarCount("${filtered.size} of ${activeItems.size} items")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterBarLabel("Sort by")
                    CompactDropdown(
                        display = sortLabels[sort] ?: "Recommended",
                        options = sortLabels.values.toList(),
                        displayFor = { it },
                        compact = true,
                        onSelect = { label ->
                            sort = sortLabels.entries.first { it.value == label }.key
                        }
                    )
                }
            }

        // PHONE-PROOF GRID — GridCells.Fixed(5), NEVER Adaptive.
        // Adaptive gave 3 columns -> ~102dp portraits (screenshot).
        // Fixed(5) -> ~62dp portraits on a 360dp phone. weight(1f) gives
        // the grid ALL remaining height after the one-bar filter.
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            .background(AppUiColors.GithubCard)
                            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(8.dp))
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = if (item.priceType == "granted") "Granted" else "${item.price} Gold",
                        color = AppUiColors.Gold,
                        fontSize = 8.sp,
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
        val byIds = item.buildPathIds.mapNotNull { GameData.itemsById[it] }
        if (byIds.isNotEmpty()) byIds else item.buildPath.mapNotNull { comp ->
            GameData.items.firstOrNull { it.name.equals(comp.trim(), ignoreCase = true) }
        }
    }
    val buildsIntoItems = remember(item.id) { item.buildsInto.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" } }
    val goodAgainstItems = remember(item.id) { item.goodAgainstItemIds.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" } }
    val badAgainstItems = remember(item.id) { item.badAgainstItemIds.mapNotNull { GameData.itemsById[it] }.filter { it.status == "active" } }
    val relatedItems = remember(item.id) { (goodAgainstItems + badAgainstItems).distinctBy { it.id } }
    val synergyHeroes = remember(item.id) {
        item.goodWithHeroes.mapNotNull { GameData.heroesById[it] }
    }
    val matchupHeroes = remember(item.id) {
        item.goodAgainstHeroes.mapNotNull { GameData.heroesById[it] }
    }
    val badMatchupHeroes = remember(item.id) { item.badAgainstHeroes.mapNotNull { GameData.heroesById[it] } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppUiColors.GithubBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Redesign fix: onBack was accepted but never rendered —
        // users had to discover system Back. Visible back bar now.
        DetailBackBar(label = item.name, onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
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
                    text = if (item.priceType == "granted") "Granted upgrade" else "Tier ${item.tier.trim().uppercase()} • ${item.price} Gold",
                    color = tierColor(item.tier),
                    fontSize = 12.sp
                )
                if (item.priceNote.isNotBlank()) Text(item.priceNote, color = AppUiColors.GithubSecondary, fontSize = 10.sp)
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
                    SubHeading("STATS")
                    Spacer(modifier = Modifier.height(8.dp))
                    item.statsDetailed.forEach { stat ->
                        Text("• ${stat.stat}: ${stat.value}", color = AppUiColors.GithubText, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (relatedItems.isNotEmpty()) {
                    SubHeading("RELATED ITEMS")
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
                SubHeading("BUILD PATH")
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
                if (buildsIntoItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SubHeading("BUILDS INTO")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(buildsIntoItems, key = { it.id }) { target ->
                            Column(modifier = Modifier.width(64.dp).clickable { onItemClick(target) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(item = target, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = target.name)
                                }
                                Text(target.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            ItemTab.MATCHUPS -> {
                SubHeading("HERO SYNERGIES")
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
                SubHeading("EFFECTIVE AGAINST")
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
                if (badMatchupHeroes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SubHeading("BAD AGAINST HEROES")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(badMatchupHeroes, key = { it.id }) { hero ->
                            Column(modifier = Modifier.width(56.dp).clickable { onHeroClick(hero) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))) { AssetImage(hero = hero, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = hero.name) }
                                Text(hero.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                if (goodAgainstItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SubHeading("GOOD AGAINST ITEMS")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(goodAgainstItems, key = { it.id }) { linked ->
                            Column(modifier = Modifier.width(60.dp).clickable { onItemClick(linked) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))) { AssetImage(item = linked, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = linked.name) }
                                Text(linked.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                if (badAgainstItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SubHeading("BAD AGAINST ITEMS")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(badAgainstItems, key = { it.id }) { linked ->
                            Column(modifier = Modifier.width(60.dp).clickable { onItemClick(linked) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))) { AssetImage(item = linked, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = linked.name) }
                                Text(linked.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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
