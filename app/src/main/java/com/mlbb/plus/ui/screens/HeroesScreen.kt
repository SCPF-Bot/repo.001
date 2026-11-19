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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.theme.MLBBPlusColors

// HEROES — MAXIMIZED for heroes.json (134 entries, 133 live + Dori Advanced-only)
// Uses MLBBPlusColors.TierS / TierA / TierB + Advanced / OP from plus/ui/theme/ (134 entries, 133 live + Dori Advanced-only)
// Uses GameData.liveHeroes from heroes.json. Filters: search + role + lane + tier + live/advanced.
// Detail shows EVERY JSON field: title, difficulty, specialty, lore, skills, emblem, core build (resolved item names), countersReason, bestPartners, base stats.
// Dori (134) is hidden from live list — shown only in Advanced section at bottom.

enum class DetailTab { OVERVIEW, COUNTERS, SYNERGIES }

@Composable
fun HeroesScreen(onHeroClick: (Hero) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var selectedTier by remember { mutableStateOf<String?>(null) }
    // MAXIMIZED: live heroes from JSON, 133 live. Advanced heroes (Dori) only when toggle on.
    val allHeroes = if (showAdvanced) GameData.heroes else GameData.liveHeroes
    val roles = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Assassin", "Support")
    val tiers = listOf<String?>(null, "S", "A", "B")
    val filtered = allHeroes.filter {
        (selectedRole == null || it.role == selectedRole || it.secondaryRole == selectedRole) &&
        (selectedTier == null || it.tier == selectedTier) &&
        (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true))
    }.sortedWith(compareByDescending<Hero> { it.isMeta }.thenByDescending { it.winRate })
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("👤", fontSize = 16.sp)
                Text("HERO EXPLORER", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
            }
        }
        // Count + Advanced toggle — shows JSON is actually loaded
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} heroes • Patch ${GameData.patchNotes.originalServer.version.ifBlank { "2.2.23" }}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Advanced", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
                Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it }, colors = SwitchDefaults.colors(checkedThumbColor = MLBBPlusColors.Gold, checkedTrackColor = MLBBPlusColors.Gold.copy(alpha = 0.3f)))
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search heroes... (e.g. Marcel, Soul Photographer)", color = MLBBPlusColors.GithubSecondary) },
            leadingIcon = { Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Text("✕", color = MLBBPlusColors.GithubSecondary, fontSize = 14.sp) } },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MLBBPlusColors.Gold.copy(alpha = 0.7f), unfocusedBorderColor = MLBBPlusColors.GithubBorder, focusedTextColor = MLBBPlusColors.GithubText, unfocusedTextColor = MLBBPlusColors.GithubText, cursorColor = MLBBPlusColors.Gold),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            items(roles) { role ->
                FilterChip(selected = selectedRole == role, onClick = { selectedRole = role }, label = { Text(role ?: "All", fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MLBBPlusColors.Gold, selectedLabelColor = Color.Black), border = FilterChipDefaults.filterChipBorder(true, selectedRole == role, borderColor = MLBBPlusColors.GithubBorder, selectedBorderColor = MLBBPlusColors.Gold))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            items(tiers) { t ->
                FilterChip(selected = selectedTier == t, onClick = { selectedTier = t }, label = { Text(if (t == null) "All Tiers" else "Tier $t", fontWeight = if (selectedTier == t) FontWeight.Bold else FontWeight.Normal) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MLBBPlusColors.Gold.copy(alpha = 0.3f), selectedLabelColor = MLBBPlusColors.Gold), border = FilterChipDefaults.filterChipBorder(true, selectedTier == t, borderColor = MLBBPlusColors.GithubBorder, selectedBorderColor = MLBBPlusColors.Gold))
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No heroes found — check heroes.json", color = MLBBPlusColors.GithubSecondary) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered) { hero ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MLBBPlusColors.GithubCard).clickable { onHeroClick(hero) }.padding(4.dp)) {
                        Box {
                            AsyncImage(model = "file:///android_asset/images/heroes/${hero.id}.webp", contentDescription = hero.name, contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface))
                            if (!hero.isLive) Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Orange).padding(horizontal = 4.dp, vertical = 2.dp)) { Text("ADV", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
                            if (hero.isOP) Box(Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Gold).padding(horizontal = 4.dp, vertical = 2.dp)) { Text("OP", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
                        }
                        Text(hero.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MLBBPlusColors.GithubSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                        Text("#${hero.id} • ${hero.tier}", color = when (hero.tier) { "S" -> MLBBPlusColors.TierS; "A" -> MLBBPlusColors.TierA; else -> MLBBPlusColors.TierB }, fontSize = 8.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun HeroDetailScreen(hero: Hero, onBack: () -> Unit, onHeroClick: (Hero) -> Unit) {
    var activeTab by remember { mutableStateOf(DetailTab.OVERVIEW) }
    val related = GameData.heroesById
    val itemsById = GameData.itemsById
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            AsyncImage(model = "file:///android_asset/images/heroes/${hero.id}.webp", contentDescription = "${hero.name} portrait", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MLBBPlusColors.GithubBg.copy(alpha = 0.3f), MLBBPlusColors.GithubBg))))
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).clickable { onBack() }.clip(RoundedCornerShape(100.dp)).background(MLBBPlusColors.GithubCard.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("← Back", color = MLBBPlusColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                if (hero.title.isNotBlank()) Text(hero.title, color = MLBBPlusColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(hero.name, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("${hero.displayRole} • #${hero.id} • ${hero.lane} • Tier ${hero.tier}", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
                if (!hero.isLive) Text("⚠ ADVANCED SERVER ONLY — not playable on Original Server yet", color = MLBBPlusColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().background(MLBBPlusColors.GithubCard).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            HeroStatItem("Win Rate", "%.1f%%".format(hero.winRate * 100), MLBBPlusColors.Green)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Pick Rate", "%.1f%%".format(hero.pickRate * 100), MLBBPlusColors.Gold)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Ban Rate", "%.1f%%".format(hero.banRate * 100), MLBBPlusColors.Red)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Trend", if (hero.patchTrend >= 0) "+%.1f%%".format(hero.patchTrend * 100) else "%.1f%%".format(hero.patchTrend * 100), if (hero.patchTrend >= 0) MLBBPlusColors.Green else MLBBPlusColors.Red)
        }
        PrimaryTabRow(selectedTabIndex = activeTab.ordinal, containerColor = MLBBPlusColors.GithubCard, contentColor = MLBBPlusColors.Gold) {
            DetailTab.entries.forEach { tab -> Tab(selected = activeTab == tab, onClick = { activeTab = tab }, text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (activeTab) {
                DetailTab.OVERVIEW -> HeroOverviewTab(hero, itemsById)
                DetailTab.COUNTERS -> HeroRelationTab("Heroes that BEAT ${hero.name}", hero.counteredBy, "${hero.name} BEATS", hero.counters, related, onHeroClick)
                DetailTab.SYNERGIES -> HeroRelationTab("BEST PARTNERS", hero.synergies, "WORKS AGAINST", hero.counters, related, onHeroClick)
            }
        }
    }
}

@Composable
private fun HeroStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(label, color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp, letterSpacing = 0.3.sp) }
}

@Composable
private fun HeroOverviewTab(hero: Hero, itemsById: Map<Int, com.mlbb.plus.ui.model.Item>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroInfoRow("Lane", hero.lane)
        HeroInfoRow("Role", hero.displayRole)
        HeroInfoRow("Tier", hero.tier)
        HeroInfoRow("Difficulty", "${hero.difficulty}/10")
        HeroInfoRow("Released", "${hero.releaseYear}")
        if (hero.specialty.isNotEmpty()) { Text("SPECIALTY", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { hero.specialty.forEach { HeroChip(it) } } }
        if (hero.isOP) HeroInfoTag("OP PICK", MLBBPlusColors.Gold)
        if (hero.isMeta) HeroInfoTag("META • Patch ${hero.patchVersion}", MLBBPlusColors.Green)
        if (hero.lore.isNotBlank()) { Text("LORE", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(hero.lore, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        if (hero.skills.isNotEmpty()) {
            Text("SKILLS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            hero.skills.forEach { s -> Column { Text("${s.type} — ${s.name}", color = MLBBPlusColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(s.description, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) } }
        }
        if (hero.recommendedEmblem.emblem.isNotBlank()) { Text("RECOMMENDED EMBLEM", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(hero.recommendedEmblem.emblem, color = MLBBPlusColors.GithubText, fontSize = 13.sp); if (hero.recommendedEmblem.talents.isNotEmpty()) Text(hero.recommendedEmblem.talents.joinToString(" • "), color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp) }
        Text("RECOMMENDED SPELLS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { hero.recommendedSpells.forEach { HeroChip(it) } }
        Text("CORE BUILD ORDER — from items.json", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        hero.coreItems.forEach { item ->
            val resolved = itemsById[item.itemId]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(MLBBPlusColors.Gold.copy(0.15f), RoundedCornerShape(6.dp)).border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("${item.priority}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text(resolved?.name ?: item.name.ifBlank { "Item #${item.itemId}" }, color = MLBBPlusColors.GithubText, fontSize = 13.sp)
                if (resolved != null) Text("• ${resolved.price} Gold • ${resolved.category}", color = MLBBPlusColors.GithubSecondary, fontSize = 11.sp)
            }
        }
        if (hero.countersReason.isNotBlank()) { Text("MATCHUP NOTES", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(hero.countersReason, color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        if (hero.bestPartners.isNotEmpty()) { Text("BEST PARTNERS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); Text(hero.bestPartners.joinToString(", "), color = MLBBPlusColors.GithubText, fontSize = 13.sp) }
        Text("BASE STATS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        HeroInfoRow("HP", "${hero.stats.hp} (+${hero.stats.hpGrowth}/lvl)")
        HeroInfoRow("Attack", "${hero.stats.attack} (+${hero.stats.attackGrowth}/lvl)")
        HeroInfoRow("Defense / Magic Def", "${hero.stats.defense} / ${hero.stats.magicDefense}")
        HeroInfoRow("Attack Speed / Move Speed", "${hero.stats.attackSpeed} / ${hero.stats.movementSpeed}")
    }
}

@Composable
private fun HeroRelationTab(sectionA: String, idsA: List<Int>, sectionB: String, idsB: List<Int>, related: Map<Int, Hero>, onHeroClick: (Hero) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(sectionA, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { idsA.take(5).mapNotNull { related[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
        if (idsA.isEmpty()) Text("No data — fill counters in heroes.json", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
        Text(sectionB, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { idsB.take(5).mapNotNull { related[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
        if (idsB.isEmpty()) Text("No data — fill counters in heroes.json", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
fun HeroPortraitSmall(hero: Hero, onClick: (Hero) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(hero) }) {
        AsyncImage(model = "file:///android_asset/images/heroes/${hero.id}.webp", contentDescription = hero.name, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)))
        Text(hero.name, color = MLBBPlusColors.GithubSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HeroInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp); Text(value, color = MLBBPlusColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}
@Composable
private fun HeroInfoTag(label: String, color: Color) {
    Box(Modifier.background(color.copy(0.15f), RoundedCornerShape(6.dp)).border(1.dp, color.copy(0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
}
@Composable
private fun HeroChip(label: String) {
    Box(Modifier.background(MLBBPlusColors.Gold.copy(0.12f), RoundedCornerShape(14.dp)).border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(label, color = MLBBPlusColors.Gold, fontSize = 11.sp) }
}
