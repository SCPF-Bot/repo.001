package com.mlbb.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private fun assetModel(imageFromJson: String, fallbackPath: String): String {
    val path = imageFromJson.ifBlank { fallbackPath }.trim()
    if (path.startsWith("http://") || path.startsWith("https://") ||
        path.startsWith("file://") || path.startsWith("content://")
    ) return path
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
    val model = assetModel(
        hero.image.ifBlank { hero.portrait },
        "heroes/${hero.slug.ifBlank { hero.id.toString() }}.webp"
    )
    var state by remember(hero.id, model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    if (state is AsyncImagePainter.State.Error) {
        FallbackPortrait(
            initial = hero.name.firstOrNull()?.uppercase() ?: "?",
            tint = roleColor(hero.role),
            modifier = modifier,
            contentDescription = contentDescription
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onState = { state = it }
            )
        }
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
    var state by remember(item.id, model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    if (state is AsyncImagePainter.State.Error) {
        FallbackPortrait(
            initial = item.name.firstOrNull()?.uppercase() ?: "?",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier,
            contentDescription = contentDescription
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onState = { state = it }
            )
        }
    }
}

@Composable
private fun FallbackPortrait(
    initial: String,
    tint: Color,
    modifier: Modifier,
    contentDescription: String
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.62f), tint.copy(alpha = 0.24f), base)
                )
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight).value
        val basis = if (side.isFinite()) side else 48f
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (basis * 0.40f).coerceIn(12f, 36f).sp
        )
    }
}

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
