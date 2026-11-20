package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppHeader
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.components.TierBadge
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.GuideFile
import com.mlbb.plus.ui.model.GuideType
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.HeroPick
import com.mlbb.plus.ui.model.PatchHeroChange
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.AppUiColors

@Composable
fun HomeScreen(
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    selectedRoleType: String,
    onRoleTypeChange: (String) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onLaunchWidget: () -> Unit,
    onOpenHeroes: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    onHeroClick: (Int) -> Unit,
) {
    val roles = remember { RoleTypes.roles }
    val typeIds = remember(selectedRole) { RoleTypes.typesFor(selectedRole) }
    val patch = GameData.patchNotes.displayVersion
    val guide = GameData.guides[selectedRole]
    val guideType = guide?.type(selectedRoleType) ?: guide?.types?.firstOrNull()

    Box(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = 960.dp).fillMaxWidth()
                .align(Alignment.TopCenter).verticalScroll(rememberScrollState())
        ) {
            AppHeader(
                title = "MLBB PLUS",
                subtitle = "$selectedRole guide \u2022 patch ${guide?.patch?.ifBlank { null } ?: patch} \u2022 floating guide",
                icon = AppIcons.Shield
            )

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                PatchBanner(onClick = onOpenPatchNotes)

                QuickStartCard(overlayGranted = overlayGranted)

                RolePanel(
                    selectedRole = selectedRole,
                    selectedRoleType = selectedRoleType,
                    roles = roles,
                    typeIds = typeIds,
                    guide = guide,
                    guideType = guideType,
                    onRoleChange = onRoleChange,
                    onRoleTypeChange = onRoleTypeChange,
                    overlayGranted = overlayGranted,
                    overlayRunning = overlayRunning,
                    onHeroClick = onHeroClick
                )

                if (guide != null && guide.mechanics.isNotEmpty()) {
                    SectionLabel("${selectedRole.uppercase()} MECHANICS \u2022 PATCH ${guide.patch.ifBlank { patch }}")
                    MechanicsCard(guide)
                }

                val movers = remember(GameData.patchNotes) {
                    GameData.patchNotes.originalServer.heroChanges.filter { GameData.heroesById[it.heroId] != null }
                }
                if (movers.isNotEmpty()) {
                    SectionLabel("PATCH $patch MOVERS")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        movers.forEach { change ->
                            val hero = GameData.heroesById[change.heroId] ?: return@forEach
                            MoverCard(hero = hero, change = change, onClick = { onHeroClick(hero.id) })
                        }
                    }
                }

                SectionLabel("QUICK ACTIONS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("Hero Explorer", AppIcons.Person, AppUiColors.Blue, Modifier.weight(1f), onOpenHeroes)
                    QuickActionCard("Item Explorer", AppIcons.Apps, AppUiColors.Teal, Modifier.weight(1f), onOpenItems)
                    QuickActionCard("Patch Notes", AppIcons.History, AppUiColors.GithubSecondary, Modifier.weight(1f), onOpenPatchNotes)
                }

                SectionLabel("TOP META HEROES")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameData.topMetaHeroes.forEach { hero ->
                        MetaHeroCard(hero = hero, onClick = { onHeroClick(hero.id) })
                    }
                }

                // Room so the floating launch button never covers the last card.
                Spacer(Modifier.height(84.dp))
            }
        }

        LaunchButton(
            enabledPermission = overlayGranted,
            onClick = onLaunchWidget,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun QuickStartCard(overlayGranted: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(AppUiColors.GithubSurface.copy(alpha = 0.72f))
            .border(1.dp, AppUiColors.GithubBorder, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("QUICK START", color = AppUiColors.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        QuickStartStep("1", "Choose your position", "Pick the role you play most often.")
        QuickStartStep("2", "Choose a playstyle", "The guide updates to match your strategy.")
        if (overlayGranted) {
            QuickStartStep("3", "Open your floating guide", "Tap Launch Widget at the bottom of the screen.")
        } else {
            QuickStartStep("3", "Allow the guide to appear", "Tap Enable Widget, then turn on ‘Display over other apps’ in Android settings.")
        }
    }
}

@Composable
private fun QuickStartStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(AppUiColors.Gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(number, color = AppUiColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = AppUiColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PatchBanner(onClick: () -> Unit) {
    val notes = GameData.patchNotes
    val cur = notes.originalServer
    val shape = RoundedCornerShape(18.dp)
    // Title + counts come from patch_notes.json (e.g. "Patch 2.2.23 \u2014 Popol and Kupa Revamp").
    val headline = cur.title.ifBlank { "Patch ${notes.displayVersion}" }
    val counts = buildString {
        append("${GameData.liveHeroes.size} heroes")
        if (cur.heroChanges.isNotEmpty()) append(" \u2022 ${cur.heroChanges.size} hero changes")
        if (cur.date.isNotBlank()) append(" \u2022 ${cur.date}")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(AppUiColors.Gold.copy(alpha = 0.14f), AppUiColors.Gold.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, AppUiColors.Gold.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(AppUiColors.Gold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Fire, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("CURRENT META", color = AppUiColors.Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp)
            Text(headline, color = AppUiColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(counts, color = AppUiColors.GithubSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = AppUiColors.Gold)
    }
}

@Composable
private fun RolePanel(
    selectedRole: String,
    selectedRoleType: String,
    roles: List<String>,
    typeIds: List<String>,
    guide: GuideFile?,
    guideType: GuideType?,
    onRoleChange: (String) -> Unit,
    onRoleTypeChange: (String) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onHeroClick: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val livePatch = GameData.patchNotes.displayVersion
    // Guide files are versioned independently of the patch notes: flag a guide that is older.
    val guideStale = guide != null && guide.patch.isNotBlank() && guide.patch != livePatch
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.Gold.copy(alpha = 0.30f), shape)
    ) {
        // Gold top edge, like the "Your Insights" card in the reference.
        Box(Modifier.fillMaxWidth().height(2.dp).background(AppUiColors.Gold.copy(alpha = 0.85f)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Shield, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(20.dp))
                Text(
                    "$selectedRole Guide",
                    color = AppUiColors.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(
                    text = if (overlayGranted) "PERMISSION ON" else "SETUP NEEDED",
                    color = if (overlayGranted) AppUiColors.Green else AppUiColors.Orange
                )
            }

            if (guide == null) {
                Text(
                    "No ${selectedRole.lowercase()}_guide.json found. The overlay still gives you the game timer, Turtle / Lord tracker and enemy spell cooldowns.",
                    color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(
                        text = if (guideStale) "GUIDE ${guide.patch} \u2022 OLDER THAN $livePatch" else "PATCH ${guide.patch.ifBlank { livePatch }}",
                        color = if (guideStale) AppUiColors.Orange else AppUiColors.Green
                    )
                    StatusChip("${guide.types.size} TYPES", AppUiColors.Blue)
                    StatusChip("${guide.stepCount} STEPS", AppUiColors.Teal)
                    if (guide.updated.isNotBlank()) StatusChip("UPDATED ${guide.updated}", AppUiColors.GithubSecondary)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("POSITION", color = AppUiColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    DropdownBox(value = selectedRole, options = roles, labels = { it }, onSelect = onRoleChange)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PLAYSTYLE", color = AppUiColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    // Options are guide type IDs; the labels come from <role>_guide.json.
                    DropdownBox(
                        value = selectedRoleType,
                        options = typeIds,
                        labels = { RoleTypes.labelFor(selectedRole, it) },
                        onSelect = onRoleTypeChange
                    )
                }
            }

            if (guideType != null) {
                if (guideType.blurb.isNotBlank()) {
                    Text(guideType.blurb, color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (guideType.playstyle.isNotBlank()) {
                    Text(guideType.playstyle, color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                GuideTypeBriefing(guideType = guideType, onHeroClick = onHeroClick)
            }

            Text(
                if (overlayGranted) "The floating guide is allowed to appear over your game."
                else "To show the floating guide, tap Enable Widget and allow ‘Display over other apps’ in Android settings.",
                color = if (overlayGranted) AppUiColors.Green else AppUiColors.Orange,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                if (overlayRunning) "\u25CF Overlay is running" else "\u25CB Overlay is not running",
                color = if (overlayRunning) AppUiColors.Green else AppUiColors.GithubSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Win/lose condition, picks, loadout - everything the overlay shows pre-game, readable here first. */
@Composable
private fun GuideTypeBriefing(guideType: GuideType, onHeroClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (guideType.winCondition.isNotBlank()) BriefLine("WIN", guideType.winCondition, AppUiColors.Green)
        if (guideType.loseCondition.isNotBlank()) BriefLine("AVOID", guideType.loseCondition, AppUiColors.Red)

        if (guideType.heroes.isNotEmpty()) {
            Text("PICKS", color = AppUiColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                guideType.heroes.forEach { pick -> PickChip(pick, onHeroClick) }
            }
        }

        val loadout = buildList {
            if (guideType.blessing.isNotBlank()) add("Blessing" to guideType.blessing)
            if (guideType.emblem.isNotBlank()) add("Emblem" to guideType.emblem)
            if (guideType.battleSpells.isNotEmpty()) add("Spells" to guideType.battleSpells.joinToString(" \u2022 "))
            if (guideType.coreItems.isNotEmpty()) add("Core items" to guideType.coreItems.joinToString(" \u2022 "))
        }
        if (loadout.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppUiColors.GithubSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                loadout.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label.uppercase(), color = AppUiColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(value, color = AppUiColors.GithubText, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefLine(label: String, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
        Text(text, color = AppUiColors.GithubText, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

/** A guide pick. Tappable (opens Hero Detail) when the name exists in heroes.json. */
@Composable
private fun PickChip(pick: HeroPick, onHeroClick: (Int) -> Unit) {
    val hero = pick.heroId?.let { GameData.heroesById[it] }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .width(if (pick.note.isBlank()) 120.dp else 176.dp)
            .clip(shape)
            .background(AppUiColors.GithubSurface)
            .border(1.dp, AppUiColors.GithubBorder, shape)
            .then(if (hero != null) Modifier.clickable(role = Role.Button) { onHeroClick(hero.id) } else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hero != null) {
            HeroImage(hero = hero, modifier = Modifier.size(34.dp).clip(CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(pick.name, color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pick.note.isNotBlank()) {
                Text(pick.note, color = AppUiColors.GithubSecondary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (hero != null) {
                Text("Tier ${hero.tier}", color = AppUiColors.GithubSecondary, fontSize = 10.sp)
            }
        }
    }
}

/** <role>_mechanics from the guide's meta: the 3 most useful notes, rest behind a toggle. */
@Composable
private fun MechanicsCard(guide: GuideFile) {
    var showAll by remember(guide.role) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val notes = if (showAll) guide.mechanics else guide.mechanics.take(3)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        notes.forEach { note ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(note.title, color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(note.text, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (showAll && guide.globalRules.isNotEmpty()) {
            Text("RULES OF THUMB", color = AppUiColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
            guide.globalRules.forEach { Text("\u2022 $it", color = AppUiColors.GithubText, fontSize = 12.sp, lineHeight = 18.sp) }
        }
        if (guide.mechanics.size > 3 || guide.globalRules.isNotEmpty()) {
            Text(
                if (showAll) "Show less" else "Show all ${guide.mechanics.size} notes + rules",
                color = AppUiColors.Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button) { showAll = !showAll }.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MoverCard(hero: Hero, change: PatchHeroChange, onClick: () -> Unit) {
    val color = patchBadgeColor(change.type)
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        HeroImage(hero = hero, modifier = Modifier.size(52.dp).clip(CircleShape).border(2.dp, color, CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(hero.name, color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(change.type.uppercase().ifBlank { "CHANGE" }, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        if (change.tierChange.isNotBlank()) {
            Text(change.tierChange.replace("->", "\u2192"), color = AppUiColors.GithubSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

@Composable
private fun DropdownBox(value: String, options: List<String>, labels: (String) -> String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppUiColors.GithubSurface)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
                .semantics { role = Role.DropdownList }
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(labels(value), color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("\u25BE", color = AppUiColors.Gold, fontSize = 14.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppUiColors.GithubCard)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(labels(opt), color = AppUiColors.GithubText) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f))))
            .border(1.dp, tint.copy(alpha = 0.40f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (tint == AppUiColors.GithubSecondary) AppUiColors.GithubText else tint, modifier = Modifier.size(24.dp))
        }
        Text(label, color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
    }
}

@Composable
private fun MetaHeroCard(hero: Hero, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Box {
            HeroImage(
                hero = hero,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
            TierBadge(hero.tier, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(8.dp))
        Text(hero.name, color = AppUiColors.GithubText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // winRate is 0..1 (Models.kt) -> *100 is correct.
        Text(
            if (hero.hasLiveStats) "%.0f%% win".format(hero.winRate * 100) else "Tier ${hero.tier}",
            color = AppUiColors.GithubSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LaunchButton(enabledPermission: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppUiColors.Gold)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
        Text(if (enabledPermission) "Launch Widget" else "Enable Widget", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}
