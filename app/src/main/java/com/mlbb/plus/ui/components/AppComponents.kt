package com.mlbb.plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mlbb.plus.R
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppShapes

// ============================================================
// MLBB Plus — reusable Material 3 components.
//
// Rules (see docs/ux-redesign.md §4):
//  - Colors come from MaterialTheme.colorScheme only.
//  - Type comes from MaterialTheme.typography only.
//  - Spacing comes from AppDimens only.
//  - Strings come from strings.xml (R.string.*) only.
//  - Every interactive element is >= 48dp touch target.
//  - Every state (loading/empty/error) is a component,
//    not a one-off Box/Text in a screen.
//
// These components are additive: existing screens keep
// working. Migrate screens to them one at a time.
// ============================================================

/** Primary action. Full-width on phones; wrap in Row for wide layouts. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TouchTargetMin),
        shape = AppShapes.CardLarge,
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(AppDimens.IconSizeSmall),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.padding(AppDimens.Space4))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(AppDimens.IconSize))
            Spacer(Modifier.padding(AppDimens.Space4))
        }
        content()
    }
}

/** Secondary action (outlined). */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = AppDimens.TouchTargetMin),
        shape = AppShapes.CardLarge
    ) { content() }
}

/** Text action (e.g. Clear filters). Always 48dp tall. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = AppDimens.TouchTargetMin)
    ) { content() }
}

/** Standard card with tonal surface (no heavy borders). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.semantics { role = Role.Button }
    } else {
        modifier
    }
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = cardModifier.fillMaxWidth(),
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(AppDimens.Space16), verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
            content()
        }
    }
}

/** Search field for Heroes/Items. Replaces CompactSearchBox + BasicTextField. */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderRes: Int = R.string.search_hint,
    leadingIcon: ImageVector = AppIcons.Search,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TouchTargetMin),
        placeholder = { Text(stringResource(placeholderRes)) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange(""); onClear?.invoke() }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        shape = AppShapes.Input,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

/** Top app bar with back + title. Use on every detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions
    )
}

/** Empty state: icon + title + message + optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    titleRes: Int,
    messageRes: Int,
    modifier: Modifier = Modifier,
    actionLabelRes: Int? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabelRes != null && onAction != null) {
            Spacer(Modifier.height(AppDimens.Space8))
            AppOutlinedButton(onClick = onAction) {
                Text(stringResource(actionLabelRes))
            }
        }
    }
}

/** Error state with retry. Message must tell the user what to do next. */
@Composable
fun ErrorState(
    messageRes: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
    ) {
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        AppOutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

/** Loading state. Prefer skeleton/shimmer for lists; spinner for short waits. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
