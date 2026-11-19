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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.mlbb.plus.ui.model.Hero
import com.mlbb.plus.ui.model.Item
import com.mlbb.plus.ui.theme.MLBBPlusColors

// ============================================================
// plus/ui/screens/AssetImage.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/screens/AssetImage.kt
// ------------------------------------------------------------
// FIXES:
//  [1] CRITICAL - Coil3 API mismatch. Old code used Coil2 API:
//      AsyncImage(onSuccess = ..., onError = ...) with
//      import coil3.compose.AsyncImage. In Coil3 those params
//      do NOT exist - it uses onState: (AsyncImagePainter.State)
//      -> Unit. Old code does NOT compile with Coil3.
//      Fix: onState { failed = it is AsyncImagePainter.State.Error }.
//      If you actually use Coil2, change import to
//      coil.compose.AsyncImage and keep onSuccess/onError - but
//      keep Coil3 + this fix, Coil2 is EOL.
//  [2] CRITICAL - Coil3 dependency was MISSING from build.gradle.kts.
//      This file imports coil3 but .kts had no Coil. Added:
//      implementation("io.coil-kt.coil3:coil-compose:3.0.4")
//      in the polished .kts in this batch.
//  [3] hero.image / item.image from JSON was ignored.
//      Old: always "images/heroes/{id}.webp". If JSON has a
//      custom path, it was ignored and image failed. Fix: use
//      hero.image if non-blank, else ID-based path, via
//      assetModel(). Handles already-prefixed values.
//  [4] FallbackPortrait had dead param label (never used).
//      Fix: removed label, added semantics contentDescription
//      so TalkBack reads the hero/item name.
//  [5] failed state key was only hero.id / item.id.
//      If the image path changes for the same id, failed stays
//      true forever. Fix: remember(id, imagePath).
// ============================================================

private fun assetModel(imageFromJson: String, id: Int, folder: String): String {
    val path = imageFromJson.ifBlank { "images/$folder/$id.webp" }.trim()
    // Already a full URL / file URI / android_asset URI -> use as-is.
    if (path.startsWith("http://") || path.startsWith("https://") ||
        path.startsWith("file://") || path.startsWith("content://")
    ) return path
    // Strip a leading slash so we don't get file:///android_asset//...
    val clean = path.removePrefix("/")
    return "file:///android_asset/$clean"
}

@Composable
fun HeroImage(hero: Hero, modifier: Modifier) {
    val model = assetModel(hero.image, hero.id, "heroes")
    // FIX [5]: reset failed when model changes.
    var failed by remember(hero.id, model) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = hero.name.firstOrNull()?.uppercase() ?: "?",
            modifier = modifier,
            contentDescription = hero.name
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = hero.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            // FIX [1]: Coil3 onState, not onSuccess/onError.
            onState = { state ->
                failed = state is AsyncImagePainter.State.Error
            }
        )
    }
}

@Composable
fun ItemImage(item: Item, modifier: Modifier) {
    val model = assetModel(item.image, item.id, "items")
    var failed by remember(item.id, model) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = item.name.firstOrNull()?.uppercase() ?: "?",
            modifier = modifier,
            contentDescription = item.name
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onState = { state ->
                failed = state is AsyncImagePainter.State.Error
            }
        )
    }
}

@Composable
private fun FallbackPortrait(initial: String, modifier: Modifier, contentDescription: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MLBBPlusColors.GithubSurface)
            .border(1.dp, MLBBPlusColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            // FIX [4]: TalkBack reads the name, not just the letter.
            .semantics { this.contentDescription = contentDescription },
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
