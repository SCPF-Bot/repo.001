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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mlbb.plus.ui.components.SortMenu
import com.mlbb.plus.ui.components.StatDivider
import com.mlbb.plus.ui.components.SubHeading
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.AppUiColors

// Hero Explorer + Hero detail, restyled to the gold/navy look.
// FIX 2026-11-21 - Synergies / Counters self-reference + bestPartners type:
//  [A] JSON had self in synergies/counters (Angela [34,55], Hanabi [60,65], Claude [60,65]...).
//      UI had no filter, so it showed Angela as her own partner.
//  [B] Models.kt bestPartners was List<String> but JSON sends List<Int> IDs.
//      Old parsing turned [34,55] into ["34","55"], so Overview showed "34, 55".
//      Fixed in Models.kt (List<Int> + toIntList) + here (resolve IDs -> names, filter self).

enum class DetailTab { OVERVIEW, COUNTERS, SYNERGIES }

private fun tierColor(tier: String): Color {
    val t = tier.uppercase().trim()
    return when {
        t.startsWith("S") -> AppUiColors.TierS
        t.startsWith("A") -> AppUiColors.TierA
        t.isBlank() -> AppUiColors.TierA
        else -> AppUiColors.TierB
    }
}

@Composable
fun HeroesScreen(onHeroClick: (Hero) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var selectedTier by rememberSaveable { mutableStateOf<String?>(null) }
    var patchOnly by rememberSaveable { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf("Recommended") }
    val hasAdvanced = GameData.advancedHeroes.isNotEmpty()
    val patchChangeIds = remember(GameData.patchNotes) { GameData.patchNotes.originalServer.heroChanges.map { it.heroId }.toSet() }
    val allHeroes = if (showAdvanced) GameData.heroes else GameData.liveHeroes
    val roles = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Assassin", "Support")
    val tiers = listOf<String?>(null, "S", "A", "B", "C")
    val filtered = remember(query, selectedRole, selectedTier, showAdvanced, patchOnly, allHeroes, sortBy) {
        val matches = allHeroes.filter {
            (!patchOnly || it.id in patchChangeIds) &&
                (selectedRole == null || it.role == selectedRole || it.secondaryRole == selectedRole) &&
                (selectedTier == null || it.tier.uppercase().trim() == selectedTier) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) || it.slug.contains(query, ignoreCase = true) || it.tags.any { tag -> tag.contains(query, ignoreCase = true) })
        }
        when (sortBy) {
            "Name A–Z" -> matches.sortedBy { it.name.lowercase() }
            "Win rate" -> matches.sortedByDescending { it.winRate }
            "Tier" -> matches.sortedWith(compareBy<Hero> { when (it.tier.uppercase().trim()) { "S" -> 0; "A" -> 1; "B" -> 2; "C" -> 3; else -> 4 } }.thenBy { it.name })
            else -> matches.sortedWith(compareByDescending<Hero> { it.isMeta }.thenByDescending { it.winRate })
        }
    }
    val hasActiveFilters = query.isNotBlank() || selectedRole != null || selectedTier != null || patchOnly
    fun clearFilters() { query = ""; selectedRole = null; selectedTier = null; patchOnly = false }

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        ScreenTitleBar("HERO EXPLORER", AppIcons.Person)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Patch ${GameData.patchNotes.displayVersion} • ${allHeroes.size} available heroes",
                color = AppUiColors.GithubSecondary,
                fontSize = 12.sp
            )
            // heroes.json has no Advanced-server-only heroes right now, so the switch would do nothing.
            if (hasAdvanced) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Advanced server", color = AppUiColors.GithubSecondary, fontSize = 12.sp)
                Switch(
                    checked = showAdvanced,
                    onCheckedChange = { showAdvanced = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AppUiColors.Gold,
                        uncheckedThumbColor = AppUiColors.GithubSecondary,
                        uncheckedTrackColor = AppUiColors.GithubSurface,
                        uncheckedBorderColor = AppUiColors.GithubBorder
                    )
                )
            }
        }

        SearchField(query = query, onQueryChange = { query = it }, placeholder = "Search by hero, title, or tag")
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Filter by role", color = AppUiColors.GithubSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            SortMenu(selected = sortBy, options = listOf("Recommended", "Name A–Z", "Win rate", "Tier"), onSelect = { sortBy = it })
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            items(roles) { role ->
                FilterPill(label = role ?: "All", selected = selectedRole == role, onClick = { selectedRole = role })
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filter by tier", color = AppUiColors.GithubSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        ) {
            items(tiers) { t ->
                FilterPill(label = if (t == null) "All Tiers" else "Tier $t", selected = selectedTier == t, onClick = { selectedTier = t })
            }
            if (patchChangeIds.isNotEmpty()) item {
                FilterPill(label = "Changed this patch", selected = patchOnly, onClick = { patchOnly = !patchOnly })
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} of ${allHeroes.size} heroes", color = AppUiColors.GithubSecondary, fontSize = 12.sp)
            if (hasActiveFilters) TextButton(onClick = { clearFilters() }) { Text("Clear filters", color = AppUiColors.Gold) }
        }
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No heroes found", color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("Try a different name or remove one or more filters.", color = AppUiColors.GithubSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (hasActiveFilters) TextButton(onClick = { clearFilters() }) { Text("Reset search and filters", color = AppUiColors.Gold) }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
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
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            HeroImage(
                hero = hero,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, tierColor(hero.tier).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            )
            if (!hero.isLive) {
                Box(
                    Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(AppUiColors.Orange)
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
                    Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(6.dp)).background(AppUiColors.Gold)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("OP", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            hero.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = AppUiColors.GithubText.copy(alpha = 0.85f),
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

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        // ---- Banner ----
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            HeroImage(hero = hero, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            AppUiColors.GithubBg.copy(alpha = 0.55f),
                            AppUiColors.GithubBg
                        )
                    )
                )
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = AppUiColors.Gold)
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
                Text(hero.displayRole, color = AppUiColors.GithubSecondary, fontSize = 15.sp)
                if (!hero.isLive) {
                    Text(
                        "\u26A0 ADVANCED SERVER ONLY",
                        color = AppUiColors.Orange,
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
            Modifier.fillMaxWidth().background(AppUiColors.GithubCard).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // winRate / pickRate / banRate / patchTrend are 0..1 (Models.kt) -> *100 is correct.
            HeroStatItem(Modifier.weight(1f), "Win Rate", if (tracked) "%.0f%%".format(hero.winRate * 100) else "\u2014", AppUiColors.Green)
            StatDivider()
            HeroStatItem(Modifier.weight(1f), "Pick Rate", if (tracked) "%.0f%%".format(hero.pickRate * 100) else "\u2014", AppUiColors.Blue)
            StatDivider()
            HeroStatItem(Modifier.weight(1f), "Ban Rate", if (tracked) "%.0f%%".format(hero.banRate * 100) else "\u2014", AppUiColors.Red)
            StatDivider()
            HeroStatItem(
                Modifier.weight(1f),
                "Trend",
                if (tracked) (if (hero.patchTrend >= 0) "+" else "") + "%.0f%%".format(hero.patchTrend * 100) else "\u2014",
                if (hero.patchTrend >= 0) AppUiColors.Green else AppUiColors.Red
            )
        }

        // ---- Tabs ----
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = AppUiColors.Header,
            contentColor = AppUiColors.Gold
        ) {
            DetailTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    selectedContentColor = AppUiColors.Gold,
                    unselectedContentColor = AppUiColors.Gold.copy(alpha = 0.8f),
                    text = {
                        Text(
                            if (tab == DetailTab.SYNERGIES) "Partners" else tab.name.lowercase().replaceFirstChar { it.uppercase() },
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
                DetailTab.OVERVIEW -> HeroOverviewTab(hero, itemsById, related)
                DetailTab.COUNTERS -> HeroRelationTab(
                    "Heroes that BEAT ${hero.name}", hero.counteredBy,
                    "${hero.name} BEATS", hero.counters,
                    related, onHeroClick,
                    currentHeroId = hero.id
                )
                DetailTab.SYNERGIES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SubHeading("BEST PARTNERS")
                        // FIX: filter self - JSON bug put Angela (55) in her own synergies [34,55].
                        // Also fall back to bestPartners as IDs (Models.kt FIX [10]), not name-chips.
                        val partners = hero.synergies.filter { it != hero.id }.mapNotNull { related[it] }
                        val fallbackPartners = hero.bestPartners.filter { it != hero.id }.mapNotNull { related[it] }
                        val toShow = if (partners.isNotEmpty()) partners else fallbackPartners
                        if (toShow.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(toShow, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) }
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
        Text(label, color = AppUiColors.GithubSecondary.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun NoData(text: String) {
    Text(text, color = AppUiColors.GithubSecondary.copy(alpha = 0.7f), fontSize = 13.sp)
}

@Composable
private fun HeroOverviewTab(hero: Hero, itemsById: Map<Int, Item>, heroesById: Map<Int, Hero> = emptyMap()) {
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
                if (change.title.isNotBlank()) Text(change.title, color = AppUiColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                change.changes.forEach { Text("\u2022 $it", color = AppUiColors.GithubText, fontSize = 13.sp, lineHeight = 18.sp) }
                if (change.impact.isNotBlank()) Text("Impact: ${change.impact}", color = AppUiColors.GithubSecondary, fontSize = 12.sp)
            }
        }
        // ---- Where the overlay guides use this hero ----
        val guideRefs = GameData.guideRefsForHero(hero.id)
        if (guideRefs.isNotEmpty()) {
            SubHeading("IN OVERLAY GUIDES")
            guideRefs.forEach { ref ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${ref.role} \u2014 ${ref.typeLabel}", color = AppUiColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (ref.note.isNotBlank()) Text(ref.note, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        HeroInfoRow("Lane", hero.lane)
        HeroInfoRow("Role", hero.displayRole)
        HeroInfoRow("Tier", hero.tier)
        HeroInfoRow("Difficulty", "${hero.difficulty}/10")
        HeroInfoRow("Released", "${hero.releaseYear}")
        if (hero.isOP) HeroInfoTag("OP PICK", AppUiColors.Gold)
        if (hero.isMeta) HeroInfoTag("META \u2022 Patch ${hero.patchVersion}", AppUiColors.Green)

        if (hero.specialty.isNotEmpty()) {
            SubHeading("SPECIALTY")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hero.specialty.forEach { HeroChip(it) }
            }
        }
        if (hero.description.isNotBlank()) {
            SubHeading("OVERVIEW")
            Text(hero.description, color = AppUiColors.GithubText, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (hero.lore.isNotBlank()) {
            SubHeading("LORE")
            Text(hero.lore, color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (hero.patchNotes.isNotBlank()) {
            SubHeading("PATCH ${hero.patchVersion} NOTES")
            Text(hero.patchNotes, color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (hero.skills.isNotEmpty()) {
            SubHeading("SKILLS")
            hero.skills.forEach { s ->
                Column {
                    Text("${s.type} \u2014 ${s.name}", color = AppUiColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.description, color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        if (hero.recommendedEmblem.emblem.isNotBlank()) {
            SubHeading("RECOMMENDED EMBLEM")
            Text(hero.recommendedEmblem.emblem, color = AppUiColors.GithubText, fontSize = 14.sp)
            if (hero.recommendedEmblem.talents.isNotEmpty()) {
                Text(hero.recommendedEmblem.talents.joinToString(" \u2022 "), color = AppUiColors.GithubSecondary, fontSize = 13.sp)
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
                        .background(AppUiColors.Gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, AppUiColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${item.priority}", color = AppUiColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(resolved?.name ?: item.name.ifBlank { "Item #${item.itemId}" }, color = AppUiColors.GithubText, fontSize = 15.sp)
                    if (resolved != null) {
                        Text("${resolved.price} Gold \u2022 ${resolved.category}", color = AppUiColors.GithubSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        if (hero.countersReason.isNotBlank()) {
            SubHeading("MATCHUP NOTES")
            Text(hero.countersReason, color = AppUiColors.GithubText, fontSize = 14.sp, lineHeight = 20.sp)
        }
        // FIX [10]: bestPartners is now List<Int> IDs. Resolve to names, filter self.
        // Old code did joinToString on List<String> ["34","55"] -> showed "34, 55".
        val bestPartnerNames = hero.bestPartners.filter { it != hero.id }.mapNotNull { heroesById[it]?.name }
        if (bestPartnerNames.isNotEmpty()) {
            SubHeading("BEST PARTNERS")
            Text(bestPartnerNames.joinToString(", "), color = AppUiColors.GithubText, fontSize = 14.sp)
        }
        if (!hero.hasFullProfile) {
            Text(
                "Some profile sections for ${hero.name} are not available in the current heroes.json data.",
                color = AppUiColors.GithubSecondary.copy(alpha = 0.8f), fontSize = 12.sp
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
    related: Map<Int, Hero>, onHeroClick: (Hero) -> Unit,
    currentHeroId: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeading(sectionA)
        // FIX: filter self - e.g. Hanabi counters [60,65] contained Hanabi (60) itself.
        val listA = idsA.filter { it != currentHeroId }.mapNotNull { related[it] }
        if (listA.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listA, key = { it.id }) { h -> HeroPortraitSmall(h, onHeroClick) }
            }
        } else {
            NoData("No counter data for this hero yet")
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppUiColors.GithubBorder))
        SubHeading(sectionB)
        val listB = idsB.filter { it != currentHeroId }.mapNotNull { related[it] }
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
            color = AppUiColors.GithubSecondary,
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
        Text(label, color = AppUiColors.GithubSecondary, fontSize = 15.sp)
        Text(value, color = AppUiColors.GithubText, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
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
            .background(AppUiColors.Gold.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, AppUiColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = AppUiColors.Gold, fontSize = 14.sp)
    }
}
