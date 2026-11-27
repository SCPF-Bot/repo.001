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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.mlbb.plus.ui.components.TierBadge
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.theme.AppUiColors

private enum class HeroTab { OVERVIEW, COUNTERS, SYNERGIES }
private enum class HeroSort { RECOMMENDED, WIN_RATE, NAME }

private fun tierColor(tier: String): Color {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> AppUiColors.TierS
        t.startsWith("A") -> AppUiColors.TierA
        t.startsWith("B") -> AppUiColors.TierB
        else -> AppUiColors.GithubSecondary
    }
}

private fun normalizedTier(tier: String): String {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S+") -> "S+"
        t.startsWith("S") -> "S"
        t.startsWith("A") -> "A"
        t.startsWith("B") -> "B"
        else -> t
    }
}

private fun tierRank(tier: String): Int {
    return when (normalizedTier(tier)) {
        "S+" -> 0
        "S" -> 1
        "A" -> 2
        "B" -> 3
        else -> 4
    }
}

@Composable
fun HeroesScreen(
    onHeroClick: (Hero) -> Unit = {},
    onItemClick: (com.mlbb.plus.ui.model.Item) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("All") }
    var selectedTier by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(HeroSort.RECOMMENDED) }

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
                normalizedTier(hero.tier) == selectedTier
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

    // Short patch — displayVersion can carry a data stamp ("2.2.23-tier-2026-11-23"),
    // which wrapped the subtitle to 2 lines. shortVersion -> "2.2.23".
    val shortPatch = GameData.patchNotes.shortVersion

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        AppHeader("HERO EXPLORER", "Patch $shortPatch • ${filtered.size} heroes", AppIcons.Person)
        Column(
            Modifier.fillMaxSize().widthIn(max = 960.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ONE BAR — search | Filter by [role] [tier] | Sort by [sort]
            // Single 52dp container from UiKit.kt (ui/components).
            // The Row INSIDE scrolls horizontally, so on a 360dp phone the bar
            // is wider than the screen but nothing is clipped.
            // Was: 160dp SearchField + 3 CompactDropdowns + ✕ Box in a bare Row.
            val hasHeroFilters = query.isNotBlank() || selectedRole != "All" ||
                selectedTier != "All" || sort != HeroSort.RECOMMENDED
            val sortLabels = mapOf(
                HeroSort.RECOMMENDED to "Recommended",
                HeroSort.WIN_RATE to "Win rate",
                HeroSort.NAME to "Name"
            )
            OneBarFilter {
                    CompactSearchBox(query, { query = it }, "Search heroes")
                    BarDivider()
                    FilterBarLabel("Filter by")
                    CompactDropdown(
                        display = selectedRole,
                        options = roles,
                        displayFor = { it },
                        onSelect = { selectedRole = it }
                    )
                    CompactDropdown(
                        display = selectedTier,
                        options = tiers,
                        displayFor = { it },
                        onSelect = { selectedTier = it }
                    )
                    if (hasHeroFilters) {
                        TextButton(
                            onClick = {
                                query = ""
                                selectedRole = "All"
                                selectedTier = "All"
                                sort = HeroSort.RECOMMENDED
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
                FilterBarCount("${filtered.size} of ${allHeroes.size} heroes")
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
            items(filtered, key = { it.id }) { hero ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeroClick(hero) },
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
                            hero = hero,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            contentDescription = hero.name
                        )
                        TierBadge(hero.tier, Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                    Text(
                        text = hero.name,
                        color = AppUiColors.GithubText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        }
    }
}

@Composable
fun HeroDetailScreen(
    hero: Hero,
    onBack: () -> Unit = {},
    onHeroClick: (Hero) -> Unit = {},
    onItemClick: (com.mlbb.plus.ui.model.Item) -> Unit = {}
) {
    // Reset tab when hero changes
    var selectedTab by remember(hero.id) { mutableStateOf(HeroTab.OVERVIEW) }

    val counters = remember(hero.id) {
        hero.goodAgainst.mapNotNull { GameData.heroesById[it] }
    }
    val badMatchups = remember(hero.id) { hero.badAgainst.mapNotNull { GameData.heroesById[it] } }
    val synergies = remember(hero.id) {
        hero.goodWith.mapNotNull { GameData.heroesById[it] }
    }
    val coreBuild = remember(hero.id, hero.coreItems) {
        hero.coreItems.filter { buildItem -> buildItem.itemId != 0 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppUiColors.GithubBg)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Redesign fix: onBack was accepted but never rendered —
        // users had to discover system Back. Visible back bar now.
        DetailBackBar(label = hero.name, onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AssetImage(hero = hero, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = hero.name)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(hero.name, color = AppUiColors.GithubText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(hero.title, color = AppUiColors.GithubSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${hero.displayRole} • ${hero.lane} • Tier ${normalizedTier(hero.tier)} • ${(hero.winRate * 100).let { "%.1f".format(it) }}% WR",
                    color = tierColor(hero.tier),
                    fontSize = 12.sp
                )
                if (hero.tierUpdated.isNotBlank()) {
                    Text("Meta snapshot ${hero.tierUpdated}", color = AppUiColors.GithubSecondary, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            HeroTab.values().forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .semantics { role = Role.Tab; this.selected = selected }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AppUiColors.GoldBg else AppUiColors.GithubCard)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(tab.name, color = if (selected) AppUiColors.Gold else AppUiColors.GithubText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            HeroTab.OVERVIEW -> {
                val loreText = hero.loreFirstParagraph.ifBlank { hero.lore }
                if (loreText.isNotBlank()) {
                    SubHeading("HERO LORE")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(loreText, color = AppUiColors.GithubText, fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Source: Mobile Legends Wiki (Fandom)", color = AppUiColors.GithubSecondary, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (hero.specialty.isNotEmpty()) {
                    SubHeading("SPECIALTY")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hero.specialty.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppUiColors.GithubCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(s, color = AppUiColors.GithubText, fontSize = 12.sp) }
                        }
                    }
                }
                if (hero.recommendedSpells.isNotEmpty()) {
                    SubHeading("SPELLS")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hero.recommendedSpells.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppUiColors.GithubCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(s, color = AppUiColors.GithubText, fontSize = 12.sp) }
                        }
                    }
                }
                if (coreBuild.isNotEmpty()) {
                    SubHeading("CORE BUILD")
                    Spacer(modifier = Modifier.height(8.dp))
                    coreBuild.forEach { build ->
                        val item = GameData.itemsById[build.itemId]
                            ?: GameData.items.firstOrNull { it.name.equals(build.name.trim(), ignoreCase = true) }
                        if (item != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppUiColors.GithubCard)
                                    .clickable { onItemClick(item) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                                    AssetImage(item = item, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = item.name)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(item.name, color = AppUiColors.GithubText, fontSize = 14.sp)
                                    Text("${item.price} Gold", color = AppUiColors.Gold, fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            HeroTab.COUNTERS -> {
                SubHeading("WORKS AGAINST")
                Spacer(modifier = Modifier.height(8.dp))
                if (counters.isEmpty()) {
                    Text("No counter data in heroes.json", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(counters, key = { it.id }) { counter ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { onHeroClick(counter) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(hero = counter, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = counter.name)
                                }
                                Text(counter.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                SubHeading("BAD AGAINST")
                Spacer(modifier = Modifier.height(8.dp))
                if (badMatchups.isEmpty()) {
                    Text("No bad-matchup data in heroes.json", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(badMatchups, key = { it.id }) { counter ->
                            Column(modifier = Modifier.width(56.dp).clickable { onHeroClick(counter) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(hero = counter, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = counter.name)
                                }
                                Text(counter.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            HeroTab.SYNERGIES -> {
                SubHeading("BEST PARTNERS")
                Spacer(modifier = Modifier.height(8.dp))
                if (synergies.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(synergies, key = { it.id }) { partner ->
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { onHeroClick(partner) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                                    AssetImage(hero = partner, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = partner.name)
                                }
                                Text(partner.name, color = AppUiColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else if (hero.bestPartners.isNotEmpty()) {
                    val partnerNames = hero.bestPartners.mapNotNull { partnerId ->
                        GameData.heroesById[partnerId]?.name
                    }
                    if (partnerNames.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            partnerNames.forEach { partnerName ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppUiColors.GithubCard)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) { Text(partnerName, color = AppUiColors.GithubText, fontSize = 12.sp) }
                            }
                        }
                    } else {
                        Text("No synergy data in heroes.json", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                    }
                } else {
                    Text("No synergy data in heroes.json", color = AppUiColors.GithubSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}


// Keep old name as alias if other files call it
@Composable
fun HeroesScreenContent(onHeroClick: (Hero) -> Unit = {}) {
    HeroesScreen(onHeroClick = onHeroClick)
}
