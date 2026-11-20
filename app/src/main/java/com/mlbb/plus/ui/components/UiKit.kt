package com.mlbb.plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.mlbb.plus.ui.theme.AppUiColors

// ============================================================
// Shared look-and-feel: icons, title bars, chips, search field.
// Icons are built from Material path data in code, so no extra
// dependency (material-icons-extended) is needed.
// ============================================================

private fun vec(name: String, path: String): ImageVector {
    val nodes = try {
        PathParser().parsePathString(path).toNodes()
    } catch (e: Exception) {
        PathParser().parsePathString("M4,4h16v16H4z").toNodes()
    }
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(pathData = nodes, fill = SolidColor(Color.Black)).build()
}

object AppIcons {
    val Home: ImageVector by lazy { vec("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z") }
    val Person: ImageVector by lazy {
        vec("Person", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    }
    val Apps: ImageVector by lazy {
        vec("Apps", "M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,20h4v-4L4,16v4zM4,14h4v-4L4,10v4zM10,14h4v-4h-4v4zM16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z")
    }
    val History: ImageVector by lazy {
        vec("History", "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z")
    }
    val Settings: ImageVector by lazy {
        vec("Settings", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")
    }
    val Search: ImageVector by lazy {
        vec("Search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    }
    val Close: ImageVector by lazy {
        vec("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }
    val ArrowBack: ImageVector by lazy {
        vec("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }
    val ChevronRight: ImageVector by lazy {
        vec("ChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    }
    val PlayArrow: ImageVector by lazy { vec("PlayArrow", "M8,5v14l11,-7z") }
    val Fire: ImageVector by lazy {
        vec("Fire", "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM11.71,19c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12 1.77,-0.36 3.6,-1.21 4.62,-2.58 0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z")
    }
    val Shield: ImageVector by lazy {
        vec("Shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z")
    }
}

fun roleColor(role: String): Color = when (role.trim().lowercase()) {
    "tank" -> Color(0xFF4C9AFF)
    "fighter" -> Color(0xFFFF8A3D)
    "mage" -> Color(0xFFB07CFF)
    "marksman" -> Color(0xFFFFD700)
    "assassin" -> Color(0xFFFF5470)
    "support" -> Color(0xFF3DDC97)
    else -> Color(0xFF8B8EAA)
}

/** Centered gold title with icon, used by Heroes / Items / Patch Notes / Settings. */
@Composable
fun ScreenTitleBar(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(AppUiColors.Header)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(AppUiColors.Gold.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(22.dp))
        }
        Text(
            title, color = AppUiColors.GithubText, fontWeight = FontWeight.Bold,
            fontSize = 19.sp, letterSpacing = 0.3.sp
        )
    }
}

/** Home header: icon tile + gold title + subtitle, with a gold hairline underneath. */
@Composable
fun AppHeader(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppUiColors.Header).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppUiColors.Gold.copy(alpha = 0.14f))
                    .border(1.dp, AppUiColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AppUiColors.Gold, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(title, color = AppUiColors.Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp)
                Text(subtitle, color = AppUiColors.GithubSecondary, fontSize = 12.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppUiColors.Gold.copy(alpha = 0.35f)))
    }
}

/** "| QUICK ACTIONS" style section label with a gold marker. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(AppUiColors.Gold))
        Text(text, color = AppUiColors.GithubSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}

/** Gold, letter-spaced heading used inside detail pages ("RECOMMENDED SPELLS"). */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = AppUiColors.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
}

/** Rounded outlined filter pill. Selected = gold tint + gold border. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) AppUiColors.Gold.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) AppUiColors.Gold.copy(alpha = 0.8f) else AppUiColors.GithubBorder, shape)
            .semantics { role = Role.Tab; this.selected = selected }
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) AppUiColors.Gold else AppUiColors.GithubText.copy(alpha = 0.85f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = AppUiColors.GithubSecondary, fontSize = 14.sp, maxLines = 1) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = AppUiColors.Gold) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = "Clear search", tint = AppUiColors.GithubSecondary)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppUiColors.Gold.copy(alpha = 0.8f),
            unfocusedBorderColor = AppUiColors.GithubBorder,
            focusedTextColor = AppUiColors.GithubText,
            unfocusedTextColor = AppUiColors.GithubText,
            cursorColor = AppUiColors.Gold,
            focusedContainerColor = AppUiColors.GithubCard,
            unfocusedContainerColor = AppUiColors.GithubCard
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 6.dp)
    )
}


/** Compact, clearly labelled sort control for users who want more control over long lists. */
@Composable
fun SortMenu(selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Sort: $selected  ▾", color = AppUiColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppUiColors.GithubCard)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = AppUiColors.GithubText) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/** Small light-gray tier square (S / A / B) that sits on a portrait corner. */
@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val t = tier.trim().uppercase().ifBlank { "?" }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFD7D8E6))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(t, color = Color(0xFF1A1B2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.width(1.dp).height(36.dp).background(AppUiColors.GithubBorder))
}
