package com.mlbb.plus.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mlbb.plus.R
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.navigation.AppRoute
import com.mlbb.plus.ui.navigation.AppTab
import com.mlbb.plus.ui.navigation.toRoute
import com.mlbb.plus.ui.viewmodel.AppViewModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.18 runs SDK 36 only on a Java 21+ runtime, but the build and CI use JDK 17.
// SDK 35 keeps the test on JDK 17; it does not change the app's targetSdk (36).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStateAndNavigationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        GameData.load(context)
    }

    @Test
    fun `app resources and bundled game data load cleanly`() {
        assertEquals("MLBB Plus", context.getString(R.string.app_name))
        assertTrue("Expected bundled heroes", GameData.heroes.isNotEmpty())
        assertTrue("Expected bundled items", GameData.items.isNotEmpty())
        assertTrue("Expected patch version", GameData.patchNotes.shortVersion.isNotBlank())
        RoleTypes.roles.forEach { role ->
            assertTrue(
                "Expected guide types for $role",
                RoleTypes.typesFor(role).isNotEmpty()
            )
        }
    }

    @Test
    fun `viewModel persists role playstyle and auto start preference`() {
        val viewModel = AppViewModel()
        viewModel.loadPreferences(context)
        assertEquals("Roam", viewModel.uiState.value.selectedRole)
        assertFalse(viewModel.uiState.value.autoStartLastRole)

        val jungleTypes = RoleTypes.typesFor("Jungle")
        val targetType = jungleTypes.last()
        viewModel.selectRole("Jungle", context)
        viewModel.selectRoleType(targetType, context)
        viewModel.setAutoStartLastRole(true, context)

        val restored = AppViewModel()
        restored.loadPreferences(context)
        assertEquals("Jungle", restored.uiState.value.selectedRole)
        assertEquals(targetType, restored.uiState.value.selectedRoleType)
        assertTrue(restored.uiState.value.autoStartLastRole)
    }

    @Test
    fun `viewModel falls back safely for unknown persisted role or type`() {
        context.getSharedPreferences(AppViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AppViewModel.KEY_LAST_ROLE, "InvalidLane")
            .putString(AppViewModel.KEY_LAST_ROLE_TYPE, "InvalidType")
            .commit()

        val viewModel = AppViewModel()
        viewModel.loadPreferences(context)
        assertEquals("Roam", viewModel.uiState.value.selectedRole)
        assertEquals(RoleTypes.defaultTypeFor("Roam"), viewModel.uiState.value.selectedRoleType)

        viewModel.selectRole("Mid", context)
        assertEquals("Mid", viewModel.uiState.value.selectedRole)
        assertTrue(viewModel.uiState.value.selectedRoleType in RoleTypes.typesFor("Mid"))
    }

    @Test
    fun `type safe navigation routes round trip through kotlinx serialization`() {
        val json = Json
        assertEquals(
            AppRoute.HomeRoute,
            json.decodeFromString<AppRoute.HomeRoute>(json.encodeToString(AppRoute.HomeRoute))
        )
        assertEquals(
            AppRoute.HeroesRoute,
            json.decodeFromString<AppRoute.HeroesRoute>(json.encodeToString(AppRoute.HeroesRoute))
        )
        val heroDetail = AppRoute.HeroDetailRoute(heroId = 42)
        assertEquals(
            heroDetail,
            json.decodeFromString<AppRoute.HeroDetailRoute>(json.encodeToString(heroDetail))
        )
        assertEquals(
            AppRoute.ItemsRoute,
            json.decodeFromString<AppRoute.ItemsRoute>(json.encodeToString(AppRoute.ItemsRoute))
        )
        val itemDetail = AppRoute.ItemDetailRoute(itemId = 105)
        assertEquals(
            itemDetail,
            json.decodeFromString<AppRoute.ItemDetailRoute>(json.encodeToString(itemDetail))
        )
        assertEquals(
            AppRoute.PatchRoute,
            json.decodeFromString<AppRoute.PatchRoute>(json.encodeToString(AppRoute.PatchRoute))
        )
        assertEquals(
            AppRoute.SettingsRoute,
            json.decodeFromString<AppRoute.SettingsRoute>(json.encodeToString(AppRoute.SettingsRoute))
        )
        assertEquals(
            AppRoute.KnowledgeBaseRoute,
            json.decodeFromString<AppRoute.KnowledgeBaseRoute>(json.encodeToString(AppRoute.KnowledgeBaseRoute))
        )

        val mappedTabRoutes = AppTab.entries.map { it.toRoute() }.toSet()
        assertEquals(AppTab.entries.size, mappedTabRoutes.size)
    }
}
