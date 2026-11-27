package com.mlbb.plus.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.mlbb.plus.ui.components.AppIcons
import kotlinx.serialization.Serializable

/** Top-level bottom bar / navigation rail destinations. */
enum class AppTab(val label: String, val icon: ImageVector, val testTag: String) {
    HOME("Home", AppIcons.Home, "nav_tab_home"),
    HEROES("Heroes", AppIcons.Person, "nav_tab_heroes"),
    ITEMS("Items", AppIcons.Apps, "nav_tab_items"),
    PATCH("Patch", AppIcons.History, "nav_tab_patch"),
    SETTINGS("Settings", AppIcons.Settings, "nav_tab_settings")
}

/** Type-safe Navigation Compose destination keys. */
sealed interface AppRoute {
    @Serializable
    data object HomeRoute : AppRoute

    @Serializable
    data object HeroesRoute : AppRoute

    @Serializable
    data class HeroDetailRoute(val heroId: Int) : AppRoute

    @Serializable
    data object ItemsRoute : AppRoute

    @Serializable
    data class ItemDetailRoute(val itemId: Int) : AppRoute

    @Serializable
    data object PatchRoute : AppRoute

    @Serializable
    data object SettingsRoute : AppRoute

    @Serializable
    data object KnowledgeBaseRoute : AppRoute
}

fun AppTab.toRoute(): AppRoute = when (this) {
    AppTab.HOME -> AppRoute.HomeRoute
    AppTab.HEROES -> AppRoute.HeroesRoute
    AppTab.ITEMS -> AppRoute.ItemsRoute
    AppTab.PATCH -> AppRoute.PatchRoute
    AppTab.SETTINGS -> AppRoute.SettingsRoute
}
