package com.mlbb.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mlbb.plus.BuildConfig
import com.mlbb.plus.tts.VoiceGuideManager
import com.mlbb.plus.tts.VoiceGuideSettings
import com.mlbb.plus.tts.VoiceModel
import com.mlbb.plus.tts.VoiceModelInstaller
import com.mlbb.plus.ui.components.AppButton
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppOutlinedButton
import com.mlbb.plus.ui.components.AppStatusBadge
import com.mlbb.plus.ui.components.AppTextButton
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes
import com.mlbb.plus.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun SettingsScreen(
    overlayGranted: Boolean,
    onOverlayGrantedChange: (Boolean) -> Unit,
    autoStartLastRole: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenKnowledgeBase: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = "Settings",
            subtitle = "Overlay permissions, voice coach & data health",
            leadingIcon = AppIcons.Settings
        )
        SettingsContent(
            overlayGranted = overlayGranted,
            onOverlayGrantedChange = onOverlayGrantedChange,
            autoStartLastRole = autoStartLastRole,
            onAutoStartChange = onAutoStartChange,
            onOpenKnowledgeBase = onOpenKnowledgeBase,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsContent(
    overlayGranted: Boolean,
    onOverlayGrantedChange: (Boolean) -> Unit,
    autoStartLastRole: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = AppTheme.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.Space16),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.MaxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)
        ) {
            SectionLabel("FLOATING GUIDE")
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                ) {
                    val statusTint = if (overlayGranted) ext.success else ext.warning
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShapes.Card)
                            .background(statusTint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (overlayGranted) AppIcons.CheckCircle else AppIcons.Warning,
                            contentDescription = null,
                            tint = statusTint
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                        Text(
                            text = if (overlayGranted) "Permission is enabled" else "Permission is needed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (overlayGranted) {
                                "The floating guide is allowed to appear over other apps."
                            } else {
                                "Android needs your permission before the guide can appear over Mobile Legends."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AppButton(
                    onClick = { onOverlayGrantedChange(true) },
                    modifier = Modifier.testTag("settings_overlay_permission_button")
                ) {
                    Text(
                        text = if (overlayGranted) "Manage Android permission" else "Open Android settings",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "On the next screen, find MLBB Plus and enable ‘Display over other apps’. Return here afterward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("STARTUP")
            SettingsRow(
                title = "Start the guide when the app opens",
                subtitle = "Automatically show your last selected position and playstyle when you open MLBB Plus. Requires overlay permission.",
                checked = autoStartLastRole,
                onChecked = onAutoStartChange,
                modifier = Modifier.testTag("settings_autostart_switch")
            )

            VoiceGuideSection()

            SectionLabel("HELP")
            AppCard(
                onClick = onOpenKnowledgeBase,
                modifier = Modifier.testTag("settings_knowledge_base_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.TouchTargetMin),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShapes.Card)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                        Text(
                            text = "Help & Knowledge Base",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Meta, hero tiers, rotation, CC matrix, battle spell CDs & objective timers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DataFilesSection()

            Text(
                text = "MLBB Plus v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceGuideSection() {
    val context = LocalContext.current
    val ext = AppTheme.colors
    val manager = remember { VoiceGuideManager(context) }
    DisposableEffect(Unit) {
        onDispose { manager.release() }
    }

    val revision = VoiceGuideSettings.revision
    val state = manager.state
    val enabled = remember(revision) { VoiceGuideSettings.isEnabled(context) }
    val engine = remember(revision) { VoiceGuideSettings.engine(context) }
    val voiceId = remember(revision) { VoiceGuideSettings.voice(context) }
    val rate = remember(revision) { VoiceGuideSettings.rate(context) }
    val autoSpeak = remember(revision) { VoiceGuideSettings.autoSpeak(context) }
    val announceSpells = remember(revision) { VoiceGuideSettings.announceSpells(context) }
    val installer = remember { VoiceModelInstaller(context) }

    val installedIds by produceState<Set<String>>(emptySet(), revision, state.installing, state.modelLabel) {
        value = withContext(Dispatchers.IO) {
            installer.models.filter { installer.isInstalled(it) }.map { it.id }.toSet()
        }
    }
    val selectedModel = installer.forVoice(voiceId)
    val selectedInstalled = selectedModel != null && selectedModel.id in installedIds

    SectionLabel("VOICE GUIDE")

    SettingsRow(
        title = "Voice Guide",
        subtitle = "Hear the current step through the floating guide. Nothing is spoken or focused until you switch this on.",
        checked = enabled,
        onChecked = { manager.setEnabled(it) },
        modifier = Modifier.testTag("settings_voice_guide_switch")
    )

    if (!enabled) {
        Text(
            text = "Off. The floating guide behaves exactly as before: no voice controls, no audio focus, no voice model downloaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    AppCard {
        VoiceSettingLabel("VOICE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
        ) {
            installer.models.forEach { model ->
                model.speakers.keys.forEach { id ->
                    VoiceOptionChip(
                        label = voiceLabel(id, model),
                        selected = voiceId == id,
                        trailing = if (model.id in installedIds) "installed" else model.sizeLabel,
                        onClick = {
                            VoiceGuideSettings.setVoice(context, id)
                            manager.onSettingsChanged()
                        }
                    )
                }
            }
        }

        VoiceSettingLabel("SPEED")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
        ) {
            VoiceGuideSettings.RATES.forEach { option ->
                VoiceOptionChip(
                    label = speedLabel(option),
                    selected = abs(rate - option) < 0.001f,
                    trailing = null,
                    onClick = { manager.setRate(option) }
                )
            }
        }

        VoiceSettingLabel("ENGINE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
        ) {
            VoiceOptionChip(
                label = "Automatic",
                selected = engine == VoiceGuideSettings.ENGINE_AUTO,
                trailing = "recommended",
                onClick = {
                    VoiceGuideSettings.setEngine(context, VoiceGuideSettings.ENGINE_AUTO)
                    manager.onSettingsChanged()
                }
            )
            VoiceOptionChip(
                label = "Neural (offline)",
                selected = engine == VoiceGuideSettings.ENGINE_SHERPA,
                trailing = "downloads once",
                onClick = {
                    VoiceGuideSettings.setEngine(context, VoiceGuideSettings.ENGINE_SHERPA)
                    manager.onSettingsChanged()
                }
            )
            VoiceOptionChip(
                label = "System voice",
                selected = engine == VoiceGuideSettings.ENGINE_SYSTEM,
                trailing = "no model needed",
                onClick = {
                    VoiceGuideSettings.setEngine(context, VoiceGuideSettings.ENGINE_SYSTEM)
                    manager.onSettingsChanged()
                }
            )
        }

        VoiceSettingLabel("VOICE MODEL ON THIS DEVICE")
        if (installedIds.isEmpty()) {
            Text(
                text = "No voice model downloaded yet. Turning Voice Guide on downloads the selected one (about 20 to 105 MB) to Android/data/<app>/files/tts. Speech then works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            installer.models.filter { it.id in installedIds }.forEach { model ->
                VoiceModelRow(model = model, onDelete = { manager.deleteModel(model) })
            }
        }

        if (state.installing) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = state.progressLabel.ifBlank { "Preparing install" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppOutlinedButton(
                    onClick = { manager.cancelInstall() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel install")
                }
            }
        } else if (selectedModel != null) {
            AppButton(
                onClick = { manager.installSelectedModel() },
                leadingIcon = AppIcons.Download,
                modifier = Modifier.testTag("settings_install_voice_button")
            ) {
                Text(
                    text = if (selectedInstalled) {
                        "Reinstall voice • ${selectedModel.sizeLabel}"
                    } else {
                        "Install voice • ${selectedModel.sizeLabel}"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AppOutlinedButton(
            onClick = { manager.playTestLine() },
            leadingIcon = AppIcons.VolumeUp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_test_voice_button")
        ) {
            Text("Play test voice", style = MaterialTheme.typography.labelLarge)
        }

        if (state.message.isNotBlank()) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.phase == VoiceGuideManager.Phase.ERROR) MaterialTheme.colorScheme.error else ext.success
            )
        }
        if (state.engineLabel.isNotBlank()) {
            Text(
                text = "Active engine: ${state.engineLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Neural voice packs download once (" +
                installer.models.joinToString(", ") { "${it.sizeBytes / 1_000_000} MB" } +
                ") and then synthesise completely offline. Game audio is ducked cleanly while speaking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SettingsRow(
        title = "Speak automatically on each step",
        subtitle = "Read the new step once when the guide advances. Off means the Speaker button is the only way to hear a step.",
        checked = autoSpeak,
        onChecked = { VoiceGuideSettings.setAutoSpeak(context, it) }
    )
    SettingsRow(
        title = "Announce enemy battle spells",
        subtitle = "Say \"Enemy Flicker used\" when you tap a lane in the cooldown tracker. Off by default.",
        checked = announceSpells,
        onChecked = { VoiceGuideSettings.setAnnounceSpells(context, it) }
    )
}

@Composable
private fun VoiceSettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun VoiceOptionChip(label: String, selected: Boolean, trailing: String?, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = AppDimens.TouchTargetMin),
        shape = AppShapes.Chip,
        label = {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium)
                if (!trailing.isNullOrBlank()) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun VoiceModelRow(model: VoiceModel, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${model.diskLabel} • ${model.speakers.size} voice" +
                        (if (model.speakers.size == 1) "" else "s"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppTextButton(onClick = onDelete) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = "Delete ${model.label}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(AppDimens.IconSizeSmall)
                )
                Text(" Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun voiceLabel(id: String, model: VoiceModel): String = when (id) {
    VoiceGuideSettings.VOICE_KOKORO_FEMALE -> "Natural female"
    VoiceGuideSettings.VOICE_KOKORO_MALE -> "Natural male"
    VoiceGuideSettings.VOICE_PIPER_FEMALE -> "Fast female"
    VoiceGuideSettings.VOICE_PIPER_MALE -> "Fast male"
    else -> model.label
}

private fun speedLabel(rate: Float): String = when {
    rate < 0.9f -> "Relaxed 0.85×"
    rate > 1.1f -> "Brisk 1.25×"
    else -> "Normal 1.0×"
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.CardLarge)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(AppDimens.Space16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AppDimens.Space12),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun DataFilesSection() {
    val ext = AppTheme.colors
    val notes = GameData.patchNotes
    val livePatch = notes.shortVersion
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space10)) {
        AppCard(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("settings_data_diagnostics_toggle")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Data status & troubleshooting",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Inspect bundled JSON datasets and guide versions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse diagnostics" else "Expand diagnostics",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                val fullProfiles = GameData.heroes.count { it.hasFullProfile }
                DataRow(
                    name = "heroes.json",
                    detail = "${GameData.liveHeroes.size} live • ${GameData.advancedHeroes.size} advanced • $fullProfiles with full profile",
                    status = if (GameData.heroes.isEmpty()) "MISSING"
                    else if (GameData.heroesLatest) "LATEST"
                    else if (fullProfiles < GameData.heroes.size) "PARTIAL"
                    else "OK",
                    color = if (GameData.heroes.isEmpty()) MaterialTheme.colorScheme.error
                    else if (!GameData.heroesLatest && fullProfiles < GameData.heroes.size) ext.warning
                    else ext.success
                )
                DataRow(
                    name = "items.json",
                    detail = "${GameData.items.size} items",
                    status = if (GameData.items.isEmpty()) "MISSING" else if (GameData.itemsLatest) "LATEST" else "OK",
                    color = if (GameData.items.isEmpty()) MaterialTheme.colorScheme.error else ext.success
                )
                val adv = if (notes.advancedServer.hasContent) " • Advanced ${notes.advancedDisplayVersion}"
                else " • no Advanced / Nov 01 data in file"
                DataRow(
                    name = "patch_notes.json",
                    detail = "Patch $livePatch • ${notes.originalServer.heroChanges.size} hero / ${notes.originalServer.itemChanges.size} item changes$adv",
                    status = if (!notes.originalServer.hasContent) "MISSING" else if (notes.latest) "LATEST" else "OK",
                    color = if (notes.originalServer.hasContent) ext.success else MaterialTheme.colorScheme.error
                )
                RoleTypes.roles.forEach { role ->
                    val g = GameData.guides[role]
                    if (g == null) {
                        DataRow(
                            name = "${role.lowercase()}_guide.json",
                            detail = "Not found or invalid — overlay runs without a guide",
                            status = "MISSING",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val guidePatch = g.patch.substringBefore("-")
                        val older = guidePatch.isNotBlank() && guidePatch != livePatch
                        DataRow(
                            name = g.assetName,
                            detail = "${g.types.size} types • ${g.stepCount} steps • patch ${g.patch.ifBlank { "?" }}" +
                                (if (g.updated.isNotBlank()) " • ${g.updated}" else "") +
                                (if (older) " • older than $livePatch" else ""),
                            status = when {
                                older -> "OLDER"
                                g.latest -> "LATEST"
                                else -> "OK"
                            },
                            color = if (older) ext.warning else ext.success
                        )
                    }
                }
                val missingPicks = GameData.unlinkedGuidePicks
                val missingItems = GameData.unlinkedGuideItems
                if (missingPicks.isNotEmpty()) {
                    Text(
                        text = "Guide picks not in heroes.json: ${missingPicks.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.warning
                    )
                }
                if (missingItems.isNotEmpty()) {
                    Text(
                        text = "Guide core items not in items.json: ${missingItems.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun DataRow(name: String, detail: String, status: String, color: Color) {
    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppStatusBadge(text = status, color = color)
        }
    }
}
