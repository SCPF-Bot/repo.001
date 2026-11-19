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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.SampleData
import com.mlbb.plus.ui.theme.MLBBPlusColors

// Heroes — converted from old HeroListScreen.kt + HeroDetailScreen.kt
// CHANGES: package com.mlbb.plus.ui.screens, GitHub Dark + Gold, no Hilt/ViewModel/Paging,
// local filtering instead of PagingData, same visual structure as old files:
// List: header HERO EXPLORER + search + FilterChip row + 3-col grid
// Detail: banner + stats + PrimaryTabRow (Overview / Counters / Synergies) + relation rows
// Detail Counters/Synergies now EXACTLY like Hanabi screenshots: small square portraits, gold headers, thin divider, no big cards

enum class DetailTab { OVERVIEW, COUNTERS, SYNERGIES }

@Composable
fun HeroesScreen(onHeroClick: (Hero) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    val roles = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Assassin", "Support")
    val filtered = SampleData.heroes.filter {
        (selectedRole == null || it.role == selectedRole) && (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MLBBPlusColors.Gold) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MLBBPlusColors.Gold, modifier = Modifier.size(18.dp))
                Text("HERO EXPLORER", color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.size(48.dp))
        }
        // Search
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search heroes...", color = MLBBPlusColors.GithubSecondary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MLBBPlusColors.Gold.copy(alpha = 0.7f)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = MLBBPlusColors.GithubSecondary) } },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MLBBPlusColors.Gold.copy(alpha = 0.7f), unfocusedBorderColor = MLBBPlusColors.GithubBorder, focusedTextColor = MLBBPlusColors.GithubText, unfocusedTextColor = MLBBPlusColors.GithubText, cursorColor = MLBBPlusColors.Gold),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            items(roles) { role ->
                FilterChip(
                    selected = selectedRole == role, onClick = { selectedRole = role },
                    label = { Text(role ?: "All", fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MLBBPlusColors.Gold, selectedLabelColor = Color.Black),
                    border = FilterChipDefaults.filterChipBorder(true, selectedRole == role, borderColor = MLBBPlusColors.GithubBorder, selectedBorderColor = MLBBPlusColors.Gold)
                )
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No heroes found", color = MLBBPlusColors.GithubSecondary) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered) { hero ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MLBBPlusColors.GithubCard).clickable { onHeroClick(hero) }.padding(4.dp)) {
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface), contentAlignment = Alignment.Center) {
                            Text(hero.name.first().toString(), color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        }
                        Text(hero.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MLBBPlusColors.GithubSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HeroDetailScreen(hero: Hero, onBack: () -> Unit, onHeroClick: (Hero) -> Unit) {
    var activeTab by remember { mutableStateOf(DetailTab.OVERVIEW) }
    val related = SampleData.heroesById
    Column(Modifier.fillMaxSize().background(MLBBPlusColors.GithubBg)) {
        // Banner — same as old file, but Gold + GithubBg gradient
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MLBBPlusColors.GithubSurface, MLBBPlusColors.GithubBg))))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MLBBPlusColors.Gold) }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(MLBBPlusColors.Gold.copy(alpha = 0.20f)).border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("${hero.tier} Tier", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Text(hero.name, color = MLBBPlusColors.GithubText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(buildString { append(hero.role); hero.secondaryRole?.let { append(" / $it") } }, color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
            }
        }
        // Stats row — Hanabi example: 55% Win / 3% Pick / 11% Ban / +0% Trend
        Row(Modifier.fillMaxWidth().background(MLBBPlusColors.GithubCard).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            HeroStatItem("Win Rate", "%.0f%%".format(hero.winRate * 100), MLBBPlusColors.Green)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Pick Rate", "%.0f%%".format(hero.pickRate * 100), MLBBPlusColors.Gold)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Ban Rate", "%.0f%%".format(hero.banRate * 100), MLBBPlusColors.Red)
            Box(Modifier.width(1.dp).height(36.dp).background(MLBBPlusColors.GithubBorder))
            HeroStatItem("Trend", if (hero.patchTrend >= 0) "+%.0f%%".format(hero.patchTrend * 100) else "%.0f%%".format(hero.patchTrend * 100), if (hero.patchTrend >= 0) MLBBPlusColors.Green else MLBBPlusColors.Red)
        }
        // Tabs
        PrimaryTabRow(selectedTabIndex = activeTab.ordinal, containerColor = MLBBPlusColors.GithubCard, contentColor = MLBBPlusColors.Gold) {
            DetailTab.entries.forEach { tab ->
                Tab(selected = activeTab == tab, onClick = { activeTab = tab }, text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (activeTab) {
                DetailTab.OVERVIEW -> HeroOverviewTab(hero)
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
private fun HeroOverviewTab(hero: Hero) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroInfoRow("Lane", hero.lane)
        HeroInfoRow("Role", buildString { append(hero.role); hero.secondaryRole?.let { append(" / $it") } })
        HeroInfoRow("Tier", hero.tier)
        if (hero.isOP) HeroInfoTag("OP PICK", MLBBPlusColors.Gold)
        Text("RECOMMENDED SPELLS", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { hero.recommendedSpells.forEach { HeroChip(it) } }
        Text("CORE BUILD ORDER", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        hero.coreItems.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(MLBBPlusColors.Gold.copy(0.15f), RoundedCornerShape(6.dp)).border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("${item.priority}", color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(item.name, color = MLBBPlusColors.GithubText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HeroRelationTab(sectionA: String, idsA: List<Int>, sectionB: String, idsB: List<Int>, related: Map<Int, Hero>, onHeroClick: (Hero) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(sectionA, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { idsA.take(5).mapNotNull { related[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
        if (idsA.isEmpty()) Text("No data", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBPlusColors.GithubBorder))
        Text(sectionB, color = MLBBPlusColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { idsB.take(5).mapNotNull { related[it] }.forEach { h -> HeroPortraitSmall(h, onHeroClick) } }
        if (idsB.isEmpty()) Text("No data", color = MLBBPlusColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
fun HeroPortraitSmall(hero: Hero, onClick: (Hero) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick(hero) }) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MLBBPlusColors.GithubSurface).border(1.dp, MLBBPlusColors.GithubBorder, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(hero.name.first().toString(), color = MLBBPlusColors.Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
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
