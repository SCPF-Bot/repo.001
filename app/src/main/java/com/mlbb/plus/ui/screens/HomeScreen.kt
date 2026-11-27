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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppHeader
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.GuideFile
import com.mlbb.plus.ui.model.GuideType
import com.mlbb.plus.ui.model.HeroPick
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
    onHeroClick: (Int) -> Unit,
) {
    // Removed 2026-11-27 (Home simplification): onOpenHeroes / onOpenItems /
    // onOpenPatchNotes. Their only consumers were the Quick Actions row and the
    // CURRENT META banner; with both gone the callbacks would have been
    // accepted-but-unwired no-ops — the same trap as the old detail-screen
    // `onBack`. The bottom navigation bar / rail already reaches every section.
    val roles = remember { RoleTypes.roles }
    val typeIds = remember(selectedRole) { RoleTypes.typesFor(selectedRole) }
    // Bare patch number ("2.2.23") from PatchNotesFile.shortVersion: displayVersion can
    // carry a data stamp ("2.2.23-tier-2026-11-23"), which wrapped the header to 2 lines
    // and made guide.patch ("2.2.23") never equal the live patch in the stale check below.
    val shortPatch = GameData.patchNotes.shortVersion
    val guide = GameData.guides[selectedRole]
    val guidePatch = guide?.patch?.substringBefore("-")?.ifBlank { shortPatch } ?: shortPatch
    val guideType = guide?.type(selectedRoleType) ?: guide?.types?.firstOrNull()

    Box(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = 960.dp).fillMaxWidth()
                .align(Alignment.TopCenter).verticalScroll(rememberScrollState())
        ) {
            AppHeader(
                title = "MLBB PLUS",
                subtitle = "$selectedRole guide \u2022 patch $guidePatch \u2022 floating guide",
                icon = AppIcons.Shield
            )

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
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

                // Lane mechanics: one collapsible card, COLLAPSED by default.
                // The section label now lives inside the card header so there is a
                // single 48dp+ tap target instead of a label above an always-open card.
                if (guide != null && guide.mechanics.isNotEmpty()) {
                    MechanicsCard(
                        guide = guide,
                        title = "${selectedRole.uppercase()} MECHANICS \u2022 PATCH $guidePatch"
                    )
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
    val livePatch = GameData.patchNotes.shortVersion
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

/**
 * <role>_mechanics from the guide's meta, as a collapsible card.
 *
 * COLLAPSED BY DEFAULT (2026-11-27 redesign): Home is the launch screen, and lane
 * mechanics are reference detail rather than the primary task — so they no longer
 * push the guide setup down the page. The previous two-stage "first 3 notes + Show
 * all" toggle is replaced by a single expand/collapse: the header is the only tap
 * target, it is 56dp tall (clears the 48dp minimum), and the collapsed subtitle
 * still says what is inside so the section is not a mystery row.
 *
 * State is keyed on the role and saved, so switching role resets it to collapsed
 * while rotation / process death keeps whatever the player chose.
 */
@Composable
private fun MechanicsCard(guide: GuideFile, title: String) {
    var expanded by rememberSaveable(guide.role) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .clickable { expanded = !expanded }
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = AppUiColors.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (expanded) "Hide notes and rules"
                    else "${guide.mechanics.size} notes \u2022 ${guide.globalRules.size} rules of thumb",
                    color = AppUiColors.GithubSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Chevron points right when collapsed, down when expanded.
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = AppUiColors.Gold,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f)
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                guide.mechanics.forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(note.title, color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(note.text, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (guide.globalRules.isNotEmpty()) {
                    Text("RULES OF THUMB", color = AppUiColors.GithubSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                    guide.globalRules.forEach {
                        Text("\u2022 $it", color = AppUiColors.GithubText, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
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
        Icon(
            AppIcons.PlayArrow,
            contentDescription = null,
            tint = Color(0xFF171717), // Theme onPrimary; avoid pure black.
            modifier = Modifier.size(24.dp)
        )
        Text(if (enabledPermission) "Launch Widget" else "Enable Widget", color = Color(0xFF171717), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}
