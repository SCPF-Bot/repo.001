package com.mlbb.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Book
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.plus.ui.theme.AppUiColors

/** Central icon set shared by app navigation and screens. */
object AppIcons {
    val Apps: ImageVector = Icons.Filled.Apps
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowForward: ImageVector = Icons.AutoMirrored.Filled.ArrowForward
    val Book: ImageVector = Icons.Filled.Book
    val OpenInNew: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
    val ChevronRight: ImageVector = Icons.Filled.ChevronRight
    val Close: ImageVector = Icons.Filled.Close
    val Fire: ImageVector = Icons.Filled.LocalFireDepartment
    val History: ImageVector = Icons.Filled.History
    val Home: ImageVector = Icons.Filled.Home
    val Person: ImageVector = Icons.Filled.Person
    val PlayArrow: ImageVector = Icons.Filled.PlayArrow
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Shield: ImageVector = Icons.Filled.Shield
}

/** Consistent small section heading used throughout the screens. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppUiColors.GithubSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1
    )
}

// APPEND THIS BLOCK to components/UiKit.kt — do NOT keep it as a separate
// FilterBar.kt file. structure.md shows components/ holding only UiKit.kt,
// and UiKit.kt is where the shared headers / labels / search / sort /
// tier-badge primitives live. AppIcons is in the same package (UiKit.kt),
// so no import for it is needed here. Heroes + Items use the SAME bar,
// so a fix here fixes both screens.
// Layout: search | Filter by [role] [tier] | Sort by [sort] in ONE 52dp
// GithubCard bar. The Row inside scrolls horizontally on a 360dp phone,
// so nothing is clipped. Grid starts right underneath it.
@Composable
fun OneBarFilter(content: @Composable RowScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppUiColors.GithubCard)
            .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// CompactSearchBox — small search box that fits INSIDE the one-bar filter.
// SearchField in UiKit.kt is built full-width / 56dp, so it can't live
// in a one-bar row. Same colours (GithubSurface bg, GithubBorder, Gold
// search icon + cursor, GithubText, Secondary placeholder) at 40dp tall.
@Composable
fun CompactSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(query.isNotEmpty()) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isOpen = expanded || query.isNotEmpty()

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        if (query.isEmpty() && !isFocused) {
            expanded = false
        }
    }

    if (!isOpen) {
        Box(
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppUiColors.GithubSurface)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(10.dp))
                .semantics { role = Role.Button }
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Search, contentDescription = placeholder, tint = AppUiColors.Gold, modifier = Modifier.size(18.dp))
        }
        return
    }

    Box(
        modifier
            .width(160.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppUiColors.GithubSurface)
            .border(1.dp, AppUiColors.Gold.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(AppIcons.Search, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = AppUiColors.GithubText, fontSize = 12.sp),
                    cursorBrush = SolidColor(AppUiColors.Gold),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(placeholder, color = AppUiColors.GithubSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (!state.isFocused && query.isEmpty()) {
                                expanded = false
                            }
                        }
                )
            }
            Icon(
                AppIcons.Close,
                contentDescription = "Close search",
                tint = AppUiColors.GithubSecondary,
                modifier = Modifier.size(16.dp).clickable {
                    onQueryChange("")
                    expanded = false
                }
            )
        }
    }
}

// CompactDropdown — selection box for the one-bar filter: 40dp tall,
// GithubSurface + GithubBorder, value + Gold ▾, GithubCard menu on tap.
@Composable
fun CompactDropdown(
    display: String,
    options: List<String>,
    displayFor: (String) -> String,
    onSelect: (String) -> Unit,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(if (compact) 32.dp else 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppUiColors.GithubSurface)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(10.dp))
                .semantics { role = Role.DropdownList }
                .clickable { expanded = true }
                .padding(horizontal = if (compact) 8.dp else 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    displayFor(display),
                    color = AppUiColors.GithubText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("\u25be", color = AppUiColors.Gold, fontSize = 12.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppUiColors.GithubCard)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(displayFor(opt), color = AppUiColors.GithubText, fontSize = 12.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
fun BarDivider() {
    Box(Modifier.width(1.dp).height(24.dp).background(AppUiColors.GithubBorder))
}

// "Filter by" / "Sort by" labels inside the one-bar filter.
@Composable
fun FilterBarLabel(text: String) {
    Text(text, color = AppUiColors.GithubSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
}

// Count line just below the one-bar filter ("133 of 133 heroes").
@Composable
fun FilterBarCount(text: String) {
    Text(text, color = AppUiColors.GithubSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
}


/** Compact screen title bar used by Settings and Patch Notes. */
@Composable
fun ScreenTitleBar(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().background(AppUiColors.Header)
            .border(width = 1.dp, color = AppUiColors.GithubBorder)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(22.dp))
        Text(title, color = AppUiColors.GithubText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

/** Shared title/subtitle header used by the Home, Heroes, and Items screens. */
@Composable
fun AppHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().background(AppUiColors.Header)
            .border(width = 1.dp, color = AppUiColors.GithubBorder)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(26.dp))
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppUiColors.GithubText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AppUiColors.GithubSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small uppercase subsection heading for detail panels. */
@Composable
fun SubHeading(text: String) {
    Text(text, color = AppUiColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
}

/** Consistent tier chip for hero cards, including the S+ meta tier. */
@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val normalized = tier.trim().uppercase().ifBlank { "—" }
    val tint = when {
        normalized.startsWith("S") -> AppUiColors.TierS
        normalized.startsWith("A") -> AppUiColors.TierA
        normalized.startsWith("B") -> AppUiColors.TierB
        else -> AppUiColors.GithubSecondary
    }
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.16f))
        .border(1.dp, tint.copy(alpha = 0.65f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(normalized, color = tint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Role-tinted fallback portrait background. */
fun roleColor(role: String): androidx.compose.ui.graphics.Color = when (role.trim().lowercase()) {
    "tank" -> androidx.compose.ui.graphics.Color(0xFF5B9BD5)
    "fighter" -> androidx.compose.ui.graphics.Color(0xFFE07A5F)
    "assassin" -> androidx.compose.ui.graphics.Color(0xFFB07AE5)
    "mage" -> androidx.compose.ui.graphics.Color(0xFF8B7CF6)
    "marksman" -> androidx.compose.ui.graphics.Color(0xFFE6A23C)
    "support" -> androidx.compose.ui.graphics.Color(0xFF42BFA5)
    else -> AppUiColors.Gold
}


/** Top navigation bar with Back button for sub-views. */
@Composable
fun DetailBackBar(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUiColors.Header)
            .border(width = 1.dp, color = AppUiColors.GithubBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppUiColors.GithubSurface)
                .border(1.dp, AppUiColors.GithubBorder, RoundedCornerShape(8.dp))
                .semantics { role = Role.Button }
                .clickable { onBack() }
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = AppUiColors.Gold, modifier = Modifier.size(16.dp))
            Text("Back", color = AppUiColors.GithubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = label,
            color = AppUiColors.GithubSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
