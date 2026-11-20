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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.FilterPill
import com.mlbb.plus.ui.components.ScreenTitleBar
import com.mlbb.plus.ui.components.SearchField
import com.mlbb.plus.ui.components.StatDivider
import com.mlbb.plus.ui.components.SubHeading
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.MLBBPlusColors

// Hero Explorer + Hero detail, restyled to the gold/navy look.
// Logic (filters, tabs, relations, fallbacks) is unchanged from the previous version.

enum class DetailTab { OVERVIEW, COUNTERS, SYNERGIES }

private fun tierColor(tier: String): Color {
    val t = tier.uppercase().trim()
    return when {
        t.startsWith("S") -> MLBBPlusColors.TierS
        t.startsWith("A") -> MLBBPlusColors.TierA
        t.isBlank() -> MLBBPlusColors.TierA
        else -> MLBBPlusColors.TierB
    }
}

@Composable
fun HeroesScreen(onHeroClick: (Hero) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var selectedTier by remember { mutableStateOf<String?>(null) }
    var patchOnly by remember { mutableStateOf(false) }
    val hasAdvanced = GameData.advancedHeroes.isNotEmpty()
    val patchChangeIds = remember(GameData.patchNotes) { GameData.patchNotes.originalServer.heroChanges.map { it.heroId }.toSet() }
    val allHeroes = if (showAdvanced) GameData.heroes else GameData.liveHeroes
    val roles = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Assassin", "Support")
    val tiers = listOf<String?>(null, "S", "A", "B")
    val filtered = remember(query, selectedRole, selectedTier, showAdvanced, patchOnly, allHeroes) {
        allHeroes.filter {
            (!patchOnly || it.id in patchChangeIds) &&
                (selectedRole == null || it.role == selectedRole || it.secondaryRole == selectedRole) &&
                (selectedTier == null || it.tier.uppercase().trim() == selectedTier) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true))
        }.sortedWith(compareByDescending<Hero> { it.isMeta }.thenByDescending { it.winRate })
    }

    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        ScreenTitleBar("HERO EXPLORER", AppIcons.Person)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filtered.size} heroes \u2022 Patch ${GameData.patchNotes.displayVersion}",
                color = MLBBPlusColors.GithubSecondary,
                fontSize = 12.sp
            )
            // heroes.json has no Advanced-server-only heroes right now, so the switch would do nothing.
            if (hasAdvanced) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Advanced", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                Switch(
                    checked = showAdvanced,
                    onCheckedChange = { showAdvanced = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = MLBBPlusColors.Gold,
                        uncheckedThumbColor = MLBBPlusColors.GithubSecondary,
                        uncheckedTrackColor = MLBBPlusColors.GithubSurface,
                        uncheckedBorderColor = MLBBPlusColors.GithubBorder
                    )
                )
            }
        }

        SearchField(query = query, onQueryChange = { query = it }, placeholder = "Search heroes\u2026")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            items(roles) { role ->
                FilterPill(label = role ?: "All", selected = selectedRole == role, onClick = { selectedRole = role })
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            items(tiers) { t ->
                FilterPill(label = if (t == null) "All Tiers" else "Tier $t", selected = selectedTier == t, onClick = { selectedTier = t })
            }
            if (patchChangeIds.isNotEmpty()) item {
                FilterPill(label = "Patch ${GameData.patchNotes.displayVersion} changes", selected = patchOnly, onClick = { patchOnly = !patchOnly })
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No heroes match these filters", color = MLBBPlusColors.GithubSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { hero ->
                    HeroGridCard(hero = hero, onClick = { onHeroClick(hero) })
                }
            }
        }
    }
}

@Composable
private fun HeroGridCard(hero: Hero, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(MLBBPlusColors.GithubCard)
            .border(1.dp, MLBBPlusColors.GithubBorder.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            HeroImage(
                hero = hero,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, tierColor(hero.tier).copy(alpha = 0.35f), CircleShape)
            )
            if (!hero.isLive) {
                Box(
                    Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Orange)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("ADV", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            GameData.patchChangeForHero(hero.id)?.let { change ->
                val c = patchBadgeColor(change.type)
                Box(
                    Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(6.dp)).background(c)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text(when (change.type.trim().lowercase()) { "revamp" -> "REV"; "adjust" -> "ADJ"; "" -> "NEW"; else -> change.type.trim().take(4).uppercase() }, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            if (hero.isOP) {
                Box(
                    Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Gold)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("OP", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            hero.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MLBBPlusColors.GithubText.copy(alpha = 0.85f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
        )
    }
}

@Composable
fun HeroDetailScreen(hero: Hero, onBack: () -> Unit, onHeroClick: (Hero) -> Unit) {
    var activeTab by remember(hero.id) { mutableStateOf(DetailTab.OVERVIEW) }
    val related = GameData.heroesById
    val itemsById = GameData.itemsById

    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        // ---- Banner ----
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            HeroImage(hero = hero, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            MLBBPlusColors.GithubBg.copy(alpha = 0.55f),
                            MLBBPlusColors.GithubBg
                        )
                    )
                )
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = MLBBPlusColors.Gold)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("${hero.tier} Tier", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Text(hero.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hero.displayRole, color = MLBBPlusColors.GithubSecondary, fontSize = 15.sp)
                if (!hero.isLive) {
                    Text(
                        "\u26A0 ADVANCED SERVER ONLY",
                        color = MLBBPlusColors.Orange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ---- Stats ----
        // heroes.json uses 50% / 3% / 3% / 0% as "no data yet" placeholders for most heroes;
        // show a dash for those instead of passing them off as real numbers.
        val tracked = hero.hasLiveStats
        Row(
            Modifier.fillMaxWidth().background(MLBBPlusColors.GithubCard).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // winRate / pickRate / banRate / patchTrend are 0..1 (Models.kt) -> *100 is correct.
            HeroStatItem(Modifier.weight(1f), "Win Rate", if (tracked) "%.0f%%".format(hero.winRate * 100) else "\u2014", MLBBPlusColors.Green)
            StatDivider()
            HeroStatItem(Modifier.weight(1f), "Pick Rate", if (tracked) "%.0f%%".format(hero.pickRate * 100) else "\u2014", MLBBPlusColors.Blue)
            StatDivider()
            HeroStatItem(Modifier.weight(1f), "Ban Rate", if (tracked) "%.0f%%".format(hero.banRate * 100) else "\u2014", MLBBPlusColors.Red)
            StatDivider()
            HeroStatItem(
                Modifier.weight(1f),
                "Trend",
                if (tracked) (if (hero.patchTrend >= 0) "+" else "") + "%.0f%%".format(hero.patchTrend * 100) else "\u2014",
                if (hero.patchTrend >= 0) MLBBPlusColors.Green else MLBBPlusColors.Red
            )
        }

        // ---- Tabs ----
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MLBBPlusColors.Header,
            contentColor = MLBBPlusColors.Gold
        ) {
            DetailTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    selectedContentColor = MLBBPlusColors.Gold,
                    unselectedContentColor = MLBBPlusColors.Gold.copy(alpha = 0.8f),
                    text = {
                        Text(
                            tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 15.sp,
                            fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                DetailTab.OVERVIEW -> HeroOverviewTab(hero, itemsById)
                DetailTab.COUNTERS -> HeroRelationTab(
                    "Heroes that BEAT ${hero.name}", hero.counteredBy,
                    "${hero.name} BEATS", hero.counters,
                    related, onHeroClick
                )
                DetailTab.SYNERGIES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SubHeading("BEST PARTNERS")
                        val partners = hero.synergies.mapNotNull { related[it] }
                        if (partners.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(partners, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) }
                            }
                        } else if (hero.bestPartners.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                hero.bestPartners.forEach { HeroChip(it) }
                            }
                        } else {
                            NoData("No synergy data for this hero yet")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatItem(modifier: Modifier, label: String, value: String, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
        Text(label, color = MLBBPlusColors.GithubSecondary.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun NoData(text: String) {
    Text(text, color = MLBBPlusColors.GithubSecondary.copy(alpha = 0.7f), fontSize = 13.sp)
}

@Composable
private fun HeroOverviewTab(hero: Hero, itemsById: Map<Int, Item>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- This patch (patch_notes.json) ----
        GameData.patchChangeForHero(hero.id)?.let { change ->
            val c = patchBadgeColor(change.type)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.10f))
                    .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "PATCH ${GameData.patchNotes.displayVersion} \u2022 ${change.type.uppercase().ifBlank { "CHANGE" }}" +
                        if (change.tierChange.isNotBlank()) " \u2022 ${change.tierChange.replace("->", "\u2192")}" else "",
                    color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                )
                if (change.title.isNotBlank()) Text(change.title, color = MLBBPlusColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                change.changes.forEach { Text("\u2022 $it", color = MLBBPlusColors.GithubText, fontSize = 13.sp, lineHeight = 18.sp) }
                if (change.impact.isNotBlank()) Text("Impact: ${change.impact}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
            }
        }
        // ---- Where the overlay guides use this hero ----
        val guideRefs = GameData.guideRefsForHero(hero.id)
        if (guideRefs.isNotEmpty()) {
            SubHeading("IN OVERLAY GUIDES")
            guideRefs.forEach { ref ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${ref.role} \u2014 ${ref.typeLabel}", color = MLBBPlusColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (ref.note.isNotBlank()) Text(ref.note, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        HeroInfoRow("Lane", hero.lane)
        HeroInfoRow("Role", hero.displayRole)
        HeroInfoRow("Tier", hero.tier)
        HeroInfoRow("Difficulty", "${hero.difficulty}/10")
        HeroInfoRow("Released", "${hero.releaseYear}")
        if (hero.isOP) HeroInfoTag("OP PICK", MLBBPlusColors.Gold)
        if (hero.isMeta) HeroInfoTag("META \u2022 Patch ${hero.patchVersion}", MLBBPlusColors.Green)

        if (hero.specialty.isNotEmpty()) {
            SubHeading("SPECIALTY")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hero.specialty.forEach { HeroChip(it) }
            }
        }
        if (hero.lore.isNotBlank()) {
            SubHeading("LORE")
            Text(hero.lore, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (hero.skills.isNotEmpty()) {
            SubHeading("SKILLS")
            hero.skills.forEach { s ->
                Column {
                    Text("${s.type} \u2014 ${s.name}", color = MLBBPlusColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.description, color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        if (hero.recommendedEmblem.emblem.isNotBlank()) {
            SubHeading("RECOMMENDED EMBLEM")
            Text(hero.recommendedEmblem.emblem, color = MLBBPlusColors.GithubText, fontSize = 14.sp)
            if (hero.recommendedEmblem.talents.isNotEmpty()) {
                Text(hero.recommendedEmblem.talents.joinToString(" \u2022 "), color = MLBBPlusColors.GithubSecondary, fontSize = 13.sp)
            }
        }
        if (hero.recommendedSpells.isNotEmpty()) {
            SubHeading("RECOMMENDED SPELLS")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                hero.recommendedSpells.forEach { HeroChip(it) }
            }
        }
        if (hero.coreItems.any { it.itemId != 0 }) SubHeading("CORE BUILD ORDER")
        hero.coreItems.filter { it.itemId != 0 }.forEach { item ->
            val resolved = itemsById[item.itemId]
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp)
                        .background(MLBBPlusColors.Gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${item.priority}", color = MLBBPlusColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(resolved?.name ?: item.name.ifBlank { "Item #${item.itemId}" }, color = MLBBPlusColors.GithubText, fontSize = 15.sp)
                    if (resolved != null) {
                        Text("${resolved.price} Gold \u2022 ${resolved.category}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        if (hero.countersReason.isNotBlank()) {
            SubHeading("MATCHUP NOTES")
            Text(hero.countersReason, color = MLBBPlusColors.GithubText, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (hero.bestPartners.isNotEmpty()) {
            SubHeading("BEST PARTNERS")
            Text(hero.bestPartners.joinToString(", "), color = MLBBPlusColors.GithubText, fontSize = 14.sp)
        }
        if (!hero.hasFullProfile) {
            Text(
                "Skills, build and counters for ${hero.name} are not in heroes.json yet.",
                color = MLBBPlusColors.GithubSecondary.copy(alpha = 0.8f), fontSize = 12.sp
            )
        }
        SubHeading("BASE STATS")
        HeroInfoRow("HP", "${hero.stats.hp} (+${hero.stats.hpGrowth}/lvl)")
        HeroInfoRow("Attack", "${hero.stats.attack} (+${hero.stats.attackGrowth}/lvl)")
        HeroInfoRow("Defense / Magic Def", "${hero.stats.defense} / ${hero.stats.magicDefense}")
        HeroInfoRow("Attack Speed / Move Speed", "${hero.stats.attackSpeed} / ${hero.stats.movementSpeed}")
    }
}

@Composable
private fun HeroRelationTab(
    sectionA: String, idsA: List<Int>,
    sectionB: String, idsB: List<Int>,
    related: Map<Int, Hero>, onHeroClick: (Hero) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeading(sectionA)
        val listA = idsA.mapNotNull { related[it] }
        if (listA.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listA, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) }
            }
        } else {
            NoData("No counter data for this hero yet")
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
        SubHeading(sectionB)
        val listB = idsB.mapNotNull { related[it] }
        if (listB.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listB, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) }
            }
        } else {
            NoData("No counter data for this hero yet")
        }
    }
}

@Composable
fun HeroPortraitSmall(hero: Hero, onClick: (Hero) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(hero) }) {
        HeroImage(
            hero = hero,
            modifier = Modifier
                .size(58.dp)
                .clip(shape)
                .border(2.dp, if (hero.isMeta) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.35f), shape)
        )
        Text(
            hero.name,
            color = MLBBPlusColors.GithubSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp).width(66.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MLBBPlusColors.GithubSecondary, fontSize = 15.sp)
        Text(value, color = MLBBPlusColors.GithubText, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun HeroInfoTag(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun HeroChip(label: String) {
    Box(
        Modifier
            .background(MLBBPlusColors.Gold.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = MLBBPlusColors.Gold, fontSize = 14.sp)
    }
}
