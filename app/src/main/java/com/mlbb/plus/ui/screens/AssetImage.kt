package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// plus/ui/screens/AssetImage.kt
// GitHub path: app/src/main/java/com/mlbb/plus/ui/screens/AssetImage.kt
// ------------------------------------------------------------
// FALLBACK for missing portraits — NEW HERO / NEW ITEM
// Example: new hero in heroes.json but no images/heroes/{id}.webp yet,
//          or new item in items.json but no images/items/{id}.webp yet.
//
// Before: AsyncImage with no error handling -> blank gray box.
// Now:    AsyncImage + onError -> fallback box with initial letter.
//
//   Hero  -> "D" for Dori, "M" for Marcel, "H" for Hanabi
//   Item  -> "D" for Demon Hunter Sword
//
// Fallback is flat GitHub Dark + Gold, no glow.
// No crash, no blank box, name still shows below.
// ============================================================

@Composable
fun HeroImage(hero: Hero, modifier: Modifier) {
    var failed by remember(hero.id) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = hero.name.firstOrNull()?.uppercase() ?: "?",
            modifier = modifier,
            label = hero.name
        )
    } else {
        AsyncImage(
            model = "file:///android_asset/images/heroes/${hero.id}.webp",
            contentDescription = hero.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onSuccess = { failed = false },
            onError = { failed = true }
        )
    }
}

@Composable
fun ItemImage(item: Item, modifier: Modifier) {
    var failed by remember(item.id) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = item.name.firstOrNull()?.uppercase() ?: "?",
            modifier = modifier,
            label = item.name
        )
    } else {
        AsyncImage(
            model = "file:///android_asset/images/items/${item.id}.webp",
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onSuccess = { failed = false },
            onError = { failed = true }
        )
    }
}

@Composable
private fun FallbackPortrait(initial: String, modifier: Modifier, label: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MLBBPlusColors.GithubSurface)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = MLBBPlusColors.Gold,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
    }
}
