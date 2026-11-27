package com.mlbb.plus.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.mlbb.plus.ui.components.AppIcons

// ============================================================
// MLBB Plus — navigation routes (type-safe, no new dependency).
//
// MainActivity currently uses a private `Tab` enum + nullable
// selectedHero/selectedItem. This file formalizes the same
// destinations as sealed routes so back behavior, deep links,
// and state restoration have one definition. Adopt by
// replacing MainActivity's Tab enum with AppTab (same labels
// and icons — no visual change) and storing hero/item IDs
// (saveable) instead of whole objects (see docs/ux-redesign.md
// §5). Navigation Compose / Navigation 3 is NOT added:
// with 5 destinations and no argument-heavy flows, a sealed
// route + BackHandler is smaller and sufficient. Revisit if
// deep links or multi-step flows are added.
// ============================================================

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", AppIcons.Home),
    HEROES("Heroes", AppIcons.Person),
    ITEMS("Items", AppIcons.Apps),
    PATCH("Patch", AppIcons.History),
    SETTINGS("Settings", AppIcons.Settings)
}

sealed interface AppRoute {
    data object Home : AppRoute
    data object Heroes : AppRoute
    data class HeroDetail(val heroId: Int) : AppRoute
    data object Items : AppRoute
    data class ItemDetail(val itemId: Int) : AppRoute
    data object Patch : AppRoute
    data object Settings : AppRoute
    data object KnowledgeBase : AppRoute
}

/**
 * Deep-link scheme (staged — not yet in AndroidManifest).
 * Examples: mlbbplus://hero/34, mlbbplus://item/2101,
 * mlbbplus://patch, mlbbplus://guide/Roam/initiator
 * Parse with Uri and map to AppRoute; unknown links fall
 * back to Home. Do not add VIEW intent-filters until the
 * parser + fallback are tested (see QA checklist).
 */
object DeepLinks {
    const val SCHEME = "mlbbplus"
    fun hero(heroId: Int) = "$SCHEME://hero/$heroId"
    fun item(itemId: Int) = "$SCHEME://item/$itemId"
}
