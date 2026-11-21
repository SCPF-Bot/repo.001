package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.mlbb.plus.ui.components.roleColor
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

private fun assetModel(imageFromJson: String, fallbackPath: String): String {
    val path = imageFromJson.ifBlank { fallbackPath }.trim()
    // Already a full URL / file URI / android_asset URI -> use as-is.
    if (path.startsWith("http://") || path.startsWith("https://") ||
        path.startsWith("file://") || path.startsWith("content://")
    ) return path
    // Strip a leading slash so we don't get file:///android_asset//...
    val clean = path.removePrefix("/")
    return "file:///android_asset/$clean"
}

@Composable
fun HeroImage(
    hero: Hero,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String = hero.name
) {
    val model = assetModel(hero.image.ifBlank { hero.portrait }, "heroes/${hero.slug.ifBlank { hero.id.toString() }}.webp")
    // FIX [5]: reset failed when model changes.
    var failed by remember(hero.id, model) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = hero.name.firstOrNull()?.uppercase() ?: "?",
            tint = roleColor(hero.role),
            modifier = modifier,
            contentDescription = contentDescription
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            // FIX [1]: Coil3 onState, not onSuccess/onError.
            onState = { state ->
                failed = state is AsyncImagePainter.State.Error
            }
        )
    }
}

@Composable
fun ItemImage(
    item: Item,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String = item.name
) {
    val model = assetModel(item.image, "items/${item.id}.webp")
    var failed by remember(item.id, model) { mutableStateOf(false) }
    if (failed) {
        FallbackPortrait(
            initial = item.name.firstOrNull()?.uppercase() ?: "?",
            tint = MLBBPlusColors.Gold,
            modifier = modifier,
            contentDescription = contentDescription
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onState = { state ->
                failed = state is AsyncImagePainter.State.Error
            }
        )
    }
}

/**
 * Shown when a bundled portrait cannot be loaded.
 * The CALLER decides the shape (circle / rounded) with clip(); this only paints
 * a role-tinted gradient with a scaled initial, so grids still look finished.
 */
@Composable
private fun FallbackPortrait(initial: String, tint: Color, modifier: Modifier, contentDescription: String) {
    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.60f), tint.copy(alpha = 0.22f), MLBBPlusColors.GithubSurface)
                )
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight).value
        val basis = if (side.isFinite()) side else 48f
        Text(
            text = initial,
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (basis * 0.42f).sp
        )
    }
}


/** Shared image entry point for list/detail cards. */
@Composable
fun AssetImage(
    hero: Hero,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String = hero.name
) {
    HeroImage(hero, modifier, contentScale, contentDescription)
}

@Composable
fun AssetImage(
    item: Item,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String = item.name
) {
    ItemImage(item, modifier, contentScale, contentDescription)
}
