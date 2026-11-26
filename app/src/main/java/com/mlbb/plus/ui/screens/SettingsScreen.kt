package com.mlbb.plus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.DetailBackBar
import com.mlbb.plus.ui.components.ScreenTitleBar
import com.mlbb.plus.ui.components.SectionLabel
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.KnowledgeActionTarget
import com.mlbb.plus.ui.model.KnowledgeBaseData
import com.mlbb.plus.ui.model.KnowledgeEntry
import com.mlbb.plus.ui.theme.AppUiColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    overlayGranted: Boolean,
    onOverlayGrantedChange: (Boolean) -> Unit,
    autoStartLastRole: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenHeroes: () -> Unit = {},
    onOpenItems: () -> Unit = {},
    onOpenPatchNotes: () -> Unit = {}
) {
    var showKnowledgeBase by rememberSaveable { mutableStateOf(false) }

    if (showKnowledgeBase) {
        KnowledgeBaseView(
            onBack = { showKnowledgeBase = false },
            onNavigate = { target ->
                when (target) {
                    KnowledgeActionTarget.HEROES -> onOpenHeroes()
                    KnowledgeActionTarget.ITEMS -> onOpenItems()
                    KnowledgeActionTarget.PATCH -> onOpenPatchNotes()
                }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        ScreenTitleBar("SETTINGS", AppIcons.Settings)
        SettingsContent(
            overlayGranted = overlayGranted,
            onOverlayGrantedChange = onOverlayGrantedChange,
            autoStartLastRole = autoStartLastRole,
            onAutoStartChange = onAutoStartChange,
            onOpenKnowledgeBase = { showKnowledgeBase = true }
        )
    }
}

@Composable
private fun SettingsContent(
    overlayGranted: Boolean,
    onOverlayGrantedChange: (Boolean) -> Unit,
    autoStartLastRole: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenKnowledgeBase: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(AppUiColors.GithubBg)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("FLOATING GUIDE")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(AppUiColors.GithubCard)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                        .background((if (overlayGranted) AppUiColors.Green else AppUiColors.Orange).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (overlayGranted) "✓" else "!",
                        color = if (overlayGranted) AppUiColors.Green else AppUiColors.Orange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (overlayGranted) "Permission is enabled" else "Permission is needed",
                        color = AppUiColors.GithubText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (overlayGranted) "The floating guide is allowed to appear over other apps."
                        else "Android needs your permission before the guide can appear over Mobile Legends.",
                        color = AppUiColors.GithubSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Button(
                onClick = { onOverlayGrantedChange(true) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppUiColors.Gold, contentColor = Color(0xFF171717)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (overlayGranted) "Manage Android permission" else "Open Android settings", fontWeight = FontWeight.Bold)
            }
            Text(
                "On the next screen, find MLBB Plus and enable ‘Display over other apps’. You can return here afterward.",
                color = AppUiColors.GithubSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        SectionLabel("STARTUP")
        SettingsRow(
            title = "Start the guide when the app opens",
            subtitle = "Automatically show your last selected position and playstyle when you open MLBB Plus. Requires overlay permission.",
            checked = autoStartLastRole,
            onChecked = onAutoStartChange
        )

        SectionLabel("HELP")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(AppUiColors.GithubCard)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(16.dp))
                .semantics { role = Role.Button }
                .clickable { onOpenKnowledgeBase() }
                .heightIn(min = 68.dp)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(AppUiColors.Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Book, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Help & Knowledge Base", color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Meta, hero tiers, rotation, CC matrix, battle spell CDs & objective timers",
                    color = AppUiColors.GithubSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(20.dp))
        }

        DataFilesSection()
        Text("MLBB Plus v1.0", color = AppUiColors.GithubSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun KnowledgeBaseView(
    onBack: () -> Unit,
    onNavigate: (KnowledgeActionTarget) -> Unit
) {
    val entries = KnowledgeBaseData.entries
    var selectedId by rememberSaveable { mutableStateOf(entries.first().id) }
    var mobileReading by rememberSaveable { mutableStateOf(false) }
    val readerScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val activeIndex = entries.indexOfFirst { it.id == selectedId }.let { if (it >= 0) it else 0 }
    val activeEntry = entries[activeIndex]
    val prevEntry = if (activeIndex > 0) entries[activeIndex - 1] else null
    val nextEntry = if (activeIndex < entries.lastIndex) entries[activeIndex + 1] else null

    fun selectTopic(id: String) {
        selectedId = id
        mobileReading = true
        scope.launch { readerScroll.scrollTo(0) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppUiColors.GithubBg)) {
        val isWide = maxWidth >= 840.dp

        BackHandler {
            if (!isWide && mobileReading) {
                mobileReading = false
            } else {
                onBack()
            }
        }

        Column(Modifier.fillMaxSize()) {
            DetailBackBar(
                label = if (!isWide && mobileReading) "Glossary Topics" else "Settings • Knowledge Base",
                onBack = {
                    if (!isWide && mobileReading) {
                        mobileReading = false
                    } else {
                        onBack()
                    }
                }
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (isWide || !mobileReading) {
                    Column(
                        modifier = (if (isWide) Modifier.width(340.dp) else Modifier.fillMaxWidth())
                            .fillMaxHeight()
                            .then(
                                if (isWide) Modifier.border(width = 1.dp, color = AppUiColors.GithubBorder)
                                else Modifier
                            )
                            .background(AppUiColors.GithubBg)
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "Knowledge Base & Glossary",
                                color = AppUiColors.GithubText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${entries.size} reference guides for MLBB mechanics and app features",
                                color = AppUiColors.GithubSecondary,
                                fontSize = 12.sp
                            )
                        }
                        HorizontalDivider(color = AppUiColors.GithubBorder.copy(alpha = 0.7f))

                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            entries.forEach { entry ->
                                val selected = entry.id == activeEntry.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) AppUiColors.GithubSurface else AppUiColors.GithubCard.copy(alpha = 0.7f))
                                        .border(
                                            1.dp,
                                            if (selected) AppUiColors.Gold.copy(alpha = 0.5f) else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectTopic(entry.id) }
                                        .heightIn(min = 56.dp)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            entry.categoryLabel,
                                            color = AppUiColors.Gold.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            entry.title,
                                            color = AppUiColors.GithubText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 19.sp
                                        )
                                        Text(
                                            entry.summary,
                                            color = AppUiColors.GithubSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        AppIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = if (selected) AppUiColors.Gold else AppUiColors.GithubSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isWide || mobileReading) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(readerScroll)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Header
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${activeEntry.categoryLabel} · Topic ${activeIndex + 1} of ${entries.size}",
                                color = AppUiColors.Gold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                activeEntry.title,
                                color = AppUiColors.GithubText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp
                            )
                            Text(
                                activeEntry.summary,
                                color = AppUiColors.GithubText.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                            activeEntry.action?.let { action ->
                                Spacer(Modifier.height(2.dp))
                                Button(
                                    onClick = { onNavigate(action.target) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppUiColors.Gold,
                                        contentColor = Color(0xFF171717)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(action.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = AppUiColors.GithubBorder.copy(alpha = 0.8f))

                        // How it works
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("How it works", color = AppUiColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            activeEntry.details.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("•", color = AppUiColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(line, color = AppUiColors.GithubSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                                }
                            }
                        }

                        // Quick Reference Table
                        activeEntry.table?.let { table ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick Reference", color = AppUiColors.GithubText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppUiColors.GithubCard)
                                        .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(14.dp))
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    val colCount = table.headers.size
                                    val colWidth = if (colCount >= 4) 130.dp else 160.dp
                                    Row(
                                        Modifier.border(width = 0.5.dp, color = AppUiColors.GithubBorder)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        table.headers.forEach { header ->
                                            Text(
                                                header,
                                                color = AppUiColors.Gold,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(colWidth)
                                            )
                                        }
                                    }
                                    table.rows.forEach { row ->
                                        HorizontalDivider(color = AppUiColors.GithubBorder.copy(alpha = 0.6f))
                                        Row(
                                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            row.forEachIndexed { cIdx, cell ->
                                                Text(
                                                    cell,
                                                    color = if (cIdx == 0) AppUiColors.GithubText else AppUiColors.GithubSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (cIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                    lineHeight = 17.sp,
                                                    modifier = Modifier.width(colWidth)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Common mistakes
                        if (activeEntry.mistakes.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppUiColors.GithubCard)
                                    .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("COMMON MISTAKES", color = AppUiColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                activeEntry.mistakes.forEach { mistake ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("!", color = AppUiColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(mistake, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }

                        // App tip
                        activeEntry.appTip?.let { tip ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppUiColors.GithubSurface.copy(alpha = 0.7f))
                                    .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Using this in MLBB Plus", color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(tip, color = AppUiColors.GithubText.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }

                        // Related topics
                        val related = activeEntry.relatedIds.mapNotNull { rid -> entries.firstOrNull { it.id == rid } }
                        if (related.isNotEmpty()) {
                            HorizontalDivider(color = AppUiColors.GithubBorder.copy(alpha = 0.7f))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Related topics", color = AppUiColors.GithubSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                related.forEach { rel ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(AppUiColors.GithubCard)
                                            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(10.dp))
                                            .clickable { selectTopic(rel.id) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(rel.title, color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Icon(AppIcons.ChevronRight, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Prev / Next navigation
                        if (prevEntry != null || nextEntry != null) {
                            HorizontalDivider(color = AppUiColors.GithubBorder.copy(alpha = 0.7f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (prevEntry != null) {
                                    Row(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AppUiColors.GithubCard)
                                            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
                                            .clickable { selectTopic(prevEntry.id) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(AppIcons.ArrowBack, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Previous", color = AppUiColors.GithubSecondary, fontSize = 10.sp)
                                            Text(prevEntry.title, color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }

                                if (nextEntry != null) {
                                    Row(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AppUiColors.GithubCard)
                                            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
                                            .clickable { selectTopic(nextEntry.id) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("Next", color = AppUiColors.GithubSecondary, fontSize = 10.sp)
                                            Text(nextEntry.title, color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Icon(AppIcons.ArrowForward, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .heightIn(min = 76.dp).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = AppUiColors.GithubSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = AppUiColors.Gold, checkedTrackColor = AppUiColors.Gold.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun DataFilesSection() {
    val notes = GameData.patchNotes
    val livePatch = notes.displayVersion
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppUiColors.GithubCard)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
                .semantics { role = Role.Button }.clickable { expanded = !expanded }
                .heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Data status & troubleshooting", color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Advanced details about the app's local guide files", color = AppUiColors.GithubSecondary, fontSize = 11.sp)
            }
            Text(if (expanded) "−" else "+", color = AppUiColors.Gold, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fullProfiles = GameData.heroes.count { it.hasFullProfile }
            DataRow(
                "heroes.json",
                "${GameData.liveHeroes.size} live \u2022 ${GameData.advancedHeroes.size} advanced \u2022 $fullProfiles with full profile (skills + build)",
                if (GameData.heroes.isEmpty()) "MISSING"
                else if (GameData.heroesLatest) "LATEST"
                else if (fullProfiles < GameData.heroes.size) "PARTIAL"
                else "OK",
                if (GameData.heroes.isEmpty()) AppUiColors.Red
                else if (!GameData.heroesLatest && fullProfiles < GameData.heroes.size) AppUiColors.Orange
                else AppUiColors.Green
            )
            DataRow(
                "items.json",
                "${GameData.items.size} items",
                if (GameData.items.isEmpty()) "MISSING" else if (GameData.itemsLatest) "LATEST" else "OK",
                if (GameData.items.isEmpty()) AppUiColors.Red else AppUiColors.Green
            )
            val adv = if (notes.advancedServer.hasContent) " \u2022 Advanced ${notes.advancedDisplayVersion}" else " \u2022 no Advanced / Nov 01 data in file"
            DataRow(
                "patch_notes.json",
                "Patch $livePatch \u2022 ${notes.originalServer.heroChanges.size} hero / ${notes.originalServer.itemChanges.size} item changes$adv",
                if (!notes.originalServer.hasContent) "MISSING" else if (notes.latest) "LATEST" else "OK",
                if (notes.originalServer.hasContent) AppUiColors.Green else AppUiColors.Red
            )
            com.mlbb.plus.ui.model.RoleTypes.roles.forEach { role ->
                val g = GameData.guides[role]
                if (g == null) {
                    DataRow("${role.lowercase()}_guide.json", "Not found or invalid \u2014 overlay runs without a guide", "MISSING", AppUiColors.Red)
                } else {
                    val older = !g.latest && g.patch.isNotBlank() && g.patch != livePatch
                    DataRow(
                        g.assetName,
                        "${g.types.size} types \u2022 ${g.stepCount} steps \u2022 patch ${g.patch.ifBlank { "?" }}" +
                            (if (g.updated.isNotBlank()) " \u2022 ${g.updated}" else "") +
                            (if (older) " \u2022 older than $livePatch" else ""),
                        if (g.latest) "LATEST" else if (older) "OLDER" else "OK",
                        if (older) AppUiColors.Orange else AppUiColors.Green
                    )
                }
            }
            val missingPicks = GameData.unlinkedGuidePicks
            val missingItems = GameData.unlinkedGuideItems
            if (missingPicks.isNotEmpty()) {
                Text("Guide picks not in heroes.json: ${missingPicks.joinToString(", ")}", color = AppUiColors.Orange, fontSize = 12.sp)
            }
            if (missingItems.isNotEmpty()) {
                Text("Guide core items not in items.json: ${missingItems.joinToString(", ")}", color = AppUiColors.Orange, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DataRow(name: String, detail: String, status: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = AppUiColors.GithubText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = AppUiColors.GithubSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}
