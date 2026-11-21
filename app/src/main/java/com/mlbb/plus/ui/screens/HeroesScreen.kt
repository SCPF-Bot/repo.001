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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.mlbb.plus.ui.components.FilterPill
import com.mlbb.plus.ui.theme.MLBBPlusColors

private enum class HeroTab { OVERVIEW, COUNTERS, SYNERGIES }
private enum class HeroSort { RECOMMENDED, WIN_RATE, NAME }

private fun tierColor(tier: String): Color {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> MLBBPlusColors.TierS
        t.startsWith("A") -> MLBBPlusColors.TierA
        t.startsWith("B") -> MLBBPlusColors.TierB
        else -> MLBBPlusColors.GithubSecondary
    }
}

private fun normalizedTier(tier: String): String {
    val t = tier.trim().uppercase()
    return when {
        t.startsWith("S") -> "S"
        t.startsWith("A") -> "A"
        t.startsWith("B") -> "B"
        else -> t
    }
}

private fun tierRank(tier: String): Int {
    return when (normalizedTier(tier)) {
        "S" -> 0
        "A" -> 1
        "B" -> 2
        else -> 3
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
    val tiers = remember { listOf("All", "S", "A", "B") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MLBBPlusColors.GithubBg)
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "HERO EXPLORER",
            color = MLBBPlusColors.GithubText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = "Patch ${GameData.patchNotes.displayVersion} • ${filtered.size} available heroes",
            color = MLBBPlusColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by hero, title, or role", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp) },
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

        Text("Filter by role", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            roles.forEach { role ->
                val selected = role == selectedRole
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MLBBPlusColors.GoldBg else MLBBPlusColors.GithubCard)
                        .clickable { selectedRole = role }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(role, color = if (selected) MLBBPlusColors.Gold else MLBBPlusColors.GithubText, fontSize = 13.sp)
                }
            }
        }

        Text("Filter by tier", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tiers.forEach { tier ->
                val label = if (tier == "All") "All Tiers" else "Tier $tier"
                val selected = tier == selectedTier
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MLBBPlusColors.GoldBg else MLBBPlusColors.GithubCard)
                        .clickable { selectedTier = tier }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (selected) MLBBPlusColors.Gold else MLBBPlusColors.GithubText, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                HeroSort.RECOMMENDED to "Recommended",
                HeroSort.WIN_RATE to "Win rate",
                HeroSort.NAME to "Name"
            ).forEach { (option, label) ->
                FilterPill(label, sort == option, onClick = { sort = option })
            }
        }

        Text(
            text = "${filtered.size} of ${allHeroes.size} heroes",
            color = MLBBPlusColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Adaptive columns keep hero portraits readable across window sizes
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            .background(MLBBPlusColors.GithubCard)
                    ) {
                        AssetImage(
                            hero = hero,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            contentDescription = hero.name
                        )
                        // Tier badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(tierColor(hero.tier))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = normalizedTier(hero.tier),
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = hero.name,
                        color = MLBBPlusColors.GithubText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp)
                    )
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
        hero.counters.mapNotNull { GameData.heroesById[it] }
    }
    val synergies = remember(hero.id) {
        hero.synergies.mapNotNull { GameData.heroesById[it] }
    }
    val coreBuild = remember(hero.id, hero.coreItems) {
        hero.coreItems.filter { buildItem -> buildItem.itemId != 0 }
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
            ) {
                AssetImage(hero = hero, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = hero.name)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(hero.name, color = MLBBPlusColors.GithubText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(hero.title, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${hero.displayRole} • ${hero.lane} • Tier ${normalizedTier(hero.tier)} • ${(hero.winRate * 100).let { "%.1f".format(it) }}% WR",
                    color = tierColor(hero.tier),
                    fontSize = 12.sp
                )
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
                        .background(if (selected) MLBBPlusColors.GoldBg else MLBBPlusColors.GithubCard)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(tab.name, color = if (selected) MLBBPlusColors.Gold else MLBBPlusColors.GithubText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            HeroTab.OVERVIEW -> {
                if (hero.specialty.isNotEmpty()) {
                    Text("Specialty", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                    .background(MLBBPlusColors.GithubCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(s, color = MLBBPlusColors.GithubText, fontSize = 12.sp) }
                        }
                    }
                }
                if (hero.spells.isNotEmpty()) {
                    Text("Spells", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hero.spells.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MLBBPlusColors.GithubCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(s, color = MLBBPlusColors.GithubText, fontSize = 12.sp) }
                        }
                    }
                }
                if (coreBuild.isNotEmpty()) {
                    Text("Core Build", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    coreBuild.forEach { build ->
                        val item = GameData.itemsById[build.itemId]
                            ?: GameData.items.firstOrNull { it.name.equals(build.name.trim(), ignoreCase = true) }
                        if (item != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MLBBPlusColors.GithubCard)
                                    .clickable { onItemClick(item) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                                    AssetImage(item = item, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, contentDescription = item.name)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(item.name, color = MLBBPlusColors.GithubText, fontSize = 14.sp)
                                    Text("${item.price} Gold", color = MLBBPlusColors.Gold, fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            HeroTab.COUNTERS -> {
                Text("Works Against", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (counters.isEmpty()) {
                    Text("No counter data in heroes.json", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
                                Text(counter.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            HeroTab.SYNERGIES -> {
                Text("Best Partners", color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                Text(partner.name, color = MLBBPlusColors.GithubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else if (hero.bestPartners.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hero.bestPartners.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MLBBPlusColors.GithubCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(name, color = MLBBPlusColors.GithubText, fontSize = 12.sp) }
                        }
                    }
                } else {
                    Text("No synergy data in heroes.json", color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
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
