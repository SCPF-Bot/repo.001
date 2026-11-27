package com.mlbb.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.R
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes
import com.mlbb.plus.ui.theme.AppTheme

/** Central icon set shared by app navigation and screens. */
object AppIcons {
    val Apps: ImageVector = Icons.Filled.Apps
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowForward: ImageVector = Icons.AutoMirrored.Filled.ArrowForward
    val OpenInNew: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
    val Sort: ImageVector = Icons.AutoMirrored.Filled.Sort
    val Book: ImageVector = Icons.Filled.Book
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val ChevronRight: ImageVector = Icons.Filled.ChevronRight
    val Close: ImageVector = Icons.Filled.Close
    val Delete: ImageVector = Icons.Filled.DeleteOutline
    val Download: ImageVector = Icons.Filled.Download
    val ExpandMore: ImageVector = Icons.Filled.ExpandMore
    val Fire: ImageVector = Icons.Filled.LocalFireDepartment
    val History: ImageVector = Icons.Filled.History
    val Home: ImageVector = Icons.Filled.Home
    val Info: ImageVector = Icons.Filled.Info
    val Person: ImageVector = Icons.Filled.Person
    val PlayArrow: ImageVector = Icons.Filled.PlayArrow
    val Replay: ImageVector = Icons.Filled.Replay
    val Revamp: ImageVector = Icons.Filled.AutoFixHigh
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Shield: ImageVector = Icons.Filled.Shield
    val TrendingDown: ImageVector = Icons.AutoMirrored.Filled.TrendingDown
    val TrendingUp: ImageVector = Icons.AutoMirrored.Filled.TrendingUp
    val Tune: ImageVector = Icons.Filled.Tune
    val VolumeUp: ImageVector = Icons.AutoMirrored.Filled.VolumeUp
    val Warning: ImageVector = Icons.Filled.WarningAmber
}

/** Consistent small section heading used throughout the screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

/** Shared title/subtitle header using Material 3 surfaceContainer & theme tokens. */
@Composable
fun AppHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.headerContainer)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShapes.Card)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSize)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Small uppercase subsection heading for detail panels. */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

/** Consistent tier chip for hero cards, including the S+ meta tier. */
@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val normalized = tier.trim().uppercase().ifBlank { "—" }
    val tint = tierSemanticColor(normalized)
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))
            .border(1.dp, tint.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = normalized,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
            color = tint,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
fun tierSemanticColor(tier: String): Color {
    val normalized = tier.trim().uppercase()
    val ext = AppTheme.colors
    return when {
        normalized.startsWith("S") -> ext.tierS
        normalized.startsWith("A") -> ext.tierA
        normalized.startsWith("B") -> ext.tierB
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Role-tinted fallback portrait background. */
fun roleColor(role: String): Color = when (role.trim().lowercase()) {
    "tank" -> Color(0xFF5B9BD5)
    "fighter" -> Color(0xFFE07A5F)
    "assassin" -> Color(0xFFB07AE5)
    "mage" -> Color(0xFF8B7CF6)
    "marksman" -> Color(0xFFE6A23C)
    "support" -> Color(0xFF42BFA5)
    else -> Color(0xFFC99A25)
}

/** Accessible back bar fallback when custom inline header is needed. */
@Composable
fun DetailBackBar(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.headerContainer)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space10)
    ) {
        Row(
            modifier = Modifier
                .clip(AppShapes.Chip)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.Chip)
                .semantics { role = Role.Button }
                .clickable { onBack() }
                .heightIn(min = AppDimens.TouchTargetMin)
                .padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6)
        ) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeSmall)
            )
            Text(
                text = stringResource(R.string.action_back),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
