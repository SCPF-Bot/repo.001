package com.mlbb.plus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlbb.plus.ui.components.AppButton
import com.mlbb.plus.ui.components.AppCard
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.AppTopBar
import com.mlbb.plus.ui.model.KnowledgeActionTarget
import com.mlbb.plus.ui.model.KnowledgeBaseData
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes
import com.mlbb.plus.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun KnowledgeBaseScreen(
    onBack: () -> Unit,
    onNavigate: (KnowledgeActionTarget) -> Unit
) {
    val ext = AppTheme.colors
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 840.dp

        BackHandler(enabled = !isWide && mobileReading) {
            mobileReading = false
        }

        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (!isWide && mobileReading) activeEntry.title else "Knowledge Base",
                subtitle = if (!isWide && mobileReading) "Tap Back for all topics"
                else "${entries.size} reference guides for MLBB mechanics",
                onBack = {
                    if (!isWide && mobileReading) {
                        mobileReading = false
                    } else {
                        onBack()
                    }
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isWide || !mobileReading) {
                    Column(
                        modifier = (if (isWide) Modifier.width(340.dp) else Modifier.fillMaxWidth())
                            .fillMaxHeight()
                            .then(
                                if (isWide) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                } else {
                                    Modifier
                                }
                            )
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(AppDimens.Space12),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                        ) {
                            entries.forEach { entry ->
                                val selected = entry.id == activeEntry.id
                                AppCard(
                                    onClick = { selectTopic(entry.id) },
                                    containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    borderColor = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    contentPadding = PaddingValues(AppDimens.Space12),
                                    modifier = Modifier.testTag("kb_topic_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)
                                        ) {
                                            Text(
                                                text = entry.categoryLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = entry.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = AppIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(AppDimens.IconSizeSmall)
                                        )
                                    }
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
                            .padding(horizontal = AppDimens.Space20, vertical = AppDimens.Space16),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                            Text(
                                text = "${activeEntry.categoryLabel} · Topic ${activeIndex + 1} of ${entries.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeEntry.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeEntry.summary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            activeEntry.action?.let { action ->
                                Spacer(Modifier.height(AppDimens.Space4))
                                AppButton(
                                    onClick = { onNavigate(action.target) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(AppDimens.Space6))
                                    Icon(
                                        imageVector = AppIcons.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(AppDimens.IconSizeSmall)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                            Text(
                                text = "How it works",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            activeEntry.details.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        activeEntry.table?.let { table ->
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                                Text(
                                    text = "Quick Reference",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.CardLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                                        val colCount = table.headers.size
                                        val colWidth = if (colCount >= 4) 130.dp else 160.dp
                                        Row(
                                            modifier = Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space10),
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                                        ) {
                                            table.headers.forEach { header ->
                                                Text(
                                                    text = header,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.width(colWidth)
                                                )
                                            }
                                        }
                                        table.rows.forEach { row ->
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Row(
                                                modifier = Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space10),
                                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
                                            ) {
                                                row.forEachIndexed { cIdx, cell ->
                                                    Text(
                                                        text = cell,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (cIdx == 0) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (cIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                        modifier = Modifier.width(colWidth)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (activeEntry.mistakes.isNotEmpty()) {
                            AppCard(borderColor = ext.warning.copy(alpha = 0.5f)) {
                                Text(
                                    text = "COMMON MISTAKES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.warning
                                )
                                activeEntry.mistakes.forEach { mistake ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                                        Text(
                                            text = "!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.warning,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = mistake,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        activeEntry.appTip?.let { tip ->
                            AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(
                                    text = "Using this in MLBB Plus",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val related = activeEntry.relatedIds.mapNotNull { rid -> entries.firstOrNull { it.id == rid } }
                        if (related.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                                Text(
                                    text = "Related topics",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                related.forEach { rel ->
                                    AppCard(
                                        onClick = { selectTopic(rel.id) },
                                        contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space10)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rel.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = AppIcons.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(AppDimens.IconSizeSmall)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (prevEntry != null || nextEntry != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
                            ) {
                                if (prevEntry != null) {
                                    AppCard(
                                        onClick = { selectTopic(prevEntry.id) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(AppDimens.Space12)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.ArrowBack,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(AppDimens.IconSizeSmall)
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = "Previous",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = prevEntry.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }

                                if (nextEntry != null) {
                                    AppCard(
                                        onClick = { selectTopic(nextEntry.id) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(AppDimens.Space12)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                Text(
                                                    text = "Next",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = nextEntry.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = AppIcons.ArrowForward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(AppDimens.IconSizeSmall)
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(AppDimens.Space16))
                    }
                }
            }
        }
    }
}
