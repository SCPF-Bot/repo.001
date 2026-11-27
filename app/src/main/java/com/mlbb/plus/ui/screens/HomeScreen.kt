package com.mlbb.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlbb.plus.R
import com.mlbb.plus.ui.components.AppButton
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppExposedDropdown
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppStatusBadge
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.components.SectionHeader
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.GuideFile
import com.mlbb.plus.ui.model.GuideType
import com.mlbb.plus.ui.model.HeroPick
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes
import com.mlbb.plus.ui.theme.AppTheme

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
    val roles = remember { RoleTypes.roles }
    val typeIds = remember(selectedRole) { RoleTypes.typesFor(selectedRole) }
    val shortPatch = GameData.patchNotes.shortVersion
    val guide = GameData.guides[selectedRole]
    val guidePatch = guide?.patch?.substringBefore("-")?.ifBlank { shortPatch } ?: shortPatch
    val guideType = guide?.type(selectedRoleType) ?: guide?.types?.firstOrNull()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.app_name),
            subtitle = "$selectedRole guide • Patch $guidePatch",
            leadingIcon = AppIcons.Shield
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AppDimens.MaxContentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)
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

                if (guide != null && guide.mechanics.isNotEmpty()) {
                    MechanicsCard(
                        guide = guide,
                        title = "${selectedRole.uppercase()} MECHANICS • PATCH $guidePatch"
                    )
                }
            }
        }

        // Sticky bottom CTA bar so primary action never overlaps card text
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.headerContainer,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
                contentAlignment = Alignment.Center
            ) {
                AppButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLaunchWidget()
                    },
                    leadingIcon = AppIcons.PlayArrow,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .testTag("home_launch_button")
                ) {
                    Text(
                        text = stringResource(
                            if (overlayGranted) R.string.action_launch_widget
                            else R.string.action_enable_widget
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
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
    val ext = AppTheme.colors
    val livePatch = GameData.patchNotes.shortVersion
    val guideStale = guide != null && guide.patch.isNotBlank() &&
        guide.patch.substringBefore("-") != livePatch

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier.padding(AppDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
                ) {
                    Icon(
                        imageVector = AppIcons.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppDimens.IconSize)
                    )
                    Text(
                        text = "$selectedRole Guide",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppStatusBadge(
                        text = if (overlayGranted) "PERMISSION ON" else "SETUP NEEDED",
                        color = if (overlayGranted) ext.success else ext.warning,
                        leadingIcon = if (overlayGranted) AppIcons.CheckCircle else AppIcons.Warning
                    )
                }

                if (guide == null) {
                    Text(
                        text = "No ${selectedRole.lowercase()}_guide.json found. The overlay still gives you the game timer, Turtle / Lord tracker and enemy spell cooldowns.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6)
                    ) {
                        AppStatusBadge(
                            text = if (guideStale) "GUIDE ${guide.patch} • OLDER THAN $livePatch"
                            else "PATCH ${guide.patch.ifBlank { livePatch }}",
                            color = if (guideStale) ext.warning else ext.success,
                            leadingIcon = if (guideStale) AppIcons.Warning else AppIcons.CheckCircle
                        )
                        AppStatusBadge("${guide.types.size} TYPES", ext.info)
                        AppStatusBadge("${guide.stepCount} STEPS", MaterialTheme.colorScheme.secondary)
                        if (guide.updated.isNotBlank()) {
                            AppStatusBadge("UPDATED ${guide.updated}", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
                ) {
                    AppExposedDropdown(
                        label = "Position",
                        selectedOption = selectedRole,
                        options = roles,
                        displayFor = { it },
                        onSelect = onRoleChange,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag("home_role_dropdown")
                    )
                    AppExposedDropdown(
                        label = "Playstyle",
                        selectedOption = selectedRoleType,
                        options = typeIds,
                        displayFor = { RoleTypes.labelFor(selectedRole, it) },
                        onSelect = onRoleTypeChange,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag("home_playstyle_dropdown")
                    )
                }

                if (guideType != null) {
                    if (guideType.blurb.isNotBlank()) {
                        Text(
                            text = guideType.blurb,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (guideType.playstyle.isNotBlank()) {
                        Text(
                            text = guideType.playstyle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GuideTypeBriefing(guideType = guideType, onHeroClick = onHeroClick)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space4)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6)
                    ) {
                        Icon(
                            imageVector = if (overlayGranted) AppIcons.CheckCircle else AppIcons.Info,
                            contentDescription = null,
                            tint = if (overlayGranted) ext.success else ext.warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (overlayGranted) {
                                "The floating guide is allowed to appear over your game."
                            } else {
                                "To show the floating guide, tap Enable Widget and allow ‘Display over other apps’ in Android settings."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overlayGranted) ext.success else ext.warning
                        )
                    }
                    Text(
                        text = if (overlayRunning) "● Overlay is currently running" else "○ Overlay is idle — ready to launch",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overlayRunning) ext.success else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideTypeBriefing(guideType: GuideType, onHeroClick: (Int) -> Unit) {
    val ext = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space10)) {
        if (guideType.winCondition.isNotBlank()) {
            BriefLine("WIN", guideType.winCondition, ext.success)
        }
        if (guideType.loseCondition.isNotBlank()) {
            BriefLine("AVOID", guideType.loseCondition, MaterialTheme.colorScheme.error)
        }

        if (guideType.heroes.isNotEmpty()) {
            SectionHeader(title = "RECOMMENDED PICKS")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
            ) {
                guideType.heroes.forEach { pick -> PickChip(pick, onHeroClick) }
            }
        }

        val loadout = buildList {
            if (guideType.blessing.isNotBlank()) add("Blessing" to guideType.blessing)
            if (guideType.emblem.isNotBlank()) add("Emblem" to guideType.emblem)
            if (guideType.battleSpells.isNotEmpty()) add("Spells" to guideType.battleSpells.joinToString(" • "))
            if (guideType.coreItems.isNotEmpty()) add("Core items" to guideType.coreItems.joinToString(" • "))
        }
        if (loadout.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(AppDimens.Space12),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                ) {
                    loadout.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefLine(label: String, text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        verticalAlignment = Alignment.Top
    ) {
        AppStatusBadge(text = label, color = color)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PickChip(pick: HeroPick, onHeroClick: (Int) -> Unit) {
    val hero = pick.heroId?.let { GameData.heroesById[it] }
    Surface(
        modifier = Modifier
            .width(if (pick.note.isBlank()) 132.dp else 188.dp)
            .heightIn(min = AppDimens.TouchTargetMin)
            .clip(AppShapes.Card)
            .then(
                if (hero != null) {
                    Modifier
                        .clickable(role = Role.Button) { onHeroClick(hero.id) }
                        .testTag("home_pick_${hero.id}")
                } else {
                    Modifier
                }
            ),
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
        ) {
            if (hero != null) {
                HeroImage(
                    hero = hero,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = pick.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (pick.note.isNotBlank()) {
                    Text(
                        text = pick.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (hero != null) {
                    Text(
                        text = "Tier ${hero.tier}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MechanicsCard(guide: GuideFile, title: String) {
    var expanded by rememberSaveable(guide.role) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = AppDimens.MotionFastMs),
        label = "mechanicsChevron"
    )

    AppCard(
        modifier = Modifier.animateContentSize(animationSpec = tween(AppDimens.MotionMediumMs)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .clickable { expanded = !expanded }
                .heightIn(min = 56.dp)
                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12)
                .testTag("home_mechanics_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (expanded) "Hide notes and rules"
                    else "${guide.mechanics.size} notes • ${guide.globalRules.size} rules of thumb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = AppIcons.ExpandMore,
                contentDescription = if (expanded) "Collapse mechanics" else "Expand mechanics",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(AppDimens.IconSize)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppDimens.Space16, end = AppDimens.Space16, bottom = AppDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space10)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                guide.mechanics.forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = note.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (guide.globalRules.isNotEmpty()) {
                    SectionHeader(title = "RULES OF THUMB")
                    guide.globalRules.forEach { rule ->
                        Text(
                            text = "• $rule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
