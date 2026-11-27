package com.mlbb.plus.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mlbb.plus.R
import com.mlbb.plus.ui.components.AppIcons
import com.mlbb.plus.ui.components.EmptyState
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.KnowledgeActionTarget
import com.mlbb.plus.ui.navigation.AppRoute
import com.mlbb.plus.ui.navigation.AppTab
import com.mlbb.plus.ui.navigation.toRoute
import com.mlbb.plus.ui.screens.HeroDetailScreen
import com.mlbb.plus.ui.screens.HeroesScreen
import com.mlbb.plus.ui.screens.HomeScreen
import com.mlbb.plus.ui.screens.ItemDetailScreen
import com.mlbb.plus.ui.screens.ItemsScreen
import com.mlbb.plus.ui.screens.KnowledgeBaseScreen
import com.mlbb.plus.ui.screens.PatchNotesScreen
import com.mlbb.plus.ui.screens.SettingsScreen
import com.mlbb.plus.ui.theme.AppDimens
import com.mlbb.plus.ui.theme.AppTheme
import com.mlbb.plus.ui.theme.AppUiTheme
import com.mlbb.plus.ui.viewmodel.AppViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        GameData.load(this)
        appViewModel.refreshDataState()
        appViewModel.loadPreferences(this)
        setContent {
            MLBBPlusApp(
                viewModel = appViewModel,
                onLaunchWidget = { role, roleType -> launchWidget(role, roleType) }
            )
        }
    }

    private fun launchWidget(role: String, roleType: String): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
            return false
        }
        val intent = Intent(this, WidgetOverlayService::class.java).apply {
            putExtra(WidgetOverlayService.EXTRA_ROLE, role)
            putExtra(WidgetOverlayService.EXTRA_ROLE_TYPE, roleType)
        }
        ContextCompat.startForegroundService(this, intent)
        return true
    }
}

@Composable
private fun MLBBPlusApp(
    viewModel: AppViewModel,
    onLaunchWidget: (String, String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val overlayRunningMsg = stringResource(R.string.snackbar_overlay_running)
    val overlayPermissionMsg = stringResource(R.string.snackbar_permission_needed)

    var autoStartTriggeredThisSession by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPreferences(ctx)
        snapshotFlow { WidgetOverlayService.isRunning }
            .distinctUntilChanged()
            .collect { running ->
                viewModel.refreshOverlayState(
                    granted = Settings.canDrawOverlays(ctx),
                    running = running
                )
            }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOverlayState(
                    granted = Settings.canDrawOverlays(ctx),
                    running = WidgetOverlayService.isRunning
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        uiState.autoStartLastRole,
        uiState.overlayGranted,
        uiState.overlayRunning,
        uiState.selectedRole,
        uiState.selectedRoleType,
        uiState.preferencesLoaded
    ) {
        if (uiState.preferencesLoaded &&
            uiState.autoStartLastRole &&
            uiState.overlayGranted &&
            !uiState.overlayRunning &&
            !autoStartTriggeredThisSession
        ) {
            autoStartTriggeredThisSession = true
            val started = onLaunchWidget(uiState.selectedRole, uiState.selectedRoleType)
            if (started) {
                viewModel.refreshOverlayState(granted = true, running = true)
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevelDestination = currentDestination == null ||
        currentDestination.hasRoute<AppRoute.HomeRoute>() ||
        currentDestination.hasRoute<AppRoute.HeroesRoute>() ||
        currentDestination.hasRoute<AppRoute.ItemsRoute>() ||
        currentDestination.hasRoute<AppRoute.PatchRoute>() ||
        currentDestination.hasRoute<AppRoute.SettingsRoute>()

    fun navigateToTab(tab: AppTab) {
        navController.navigate(tab.toRoute()) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun isTabSelected(tab: AppTab): Boolean = when (tab) {
        AppTab.HOME -> currentDestination?.hierarchy?.any { it.hasRoute<AppRoute.HomeRoute>() } == true
        AppTab.HEROES -> currentDestination?.hierarchy?.any {
            it.hasRoute<AppRoute.HeroesRoute>() || it.hasRoute<AppRoute.HeroDetailRoute>()
        } == true
        AppTab.ITEMS -> currentDestination?.hierarchy?.any {
            it.hasRoute<AppRoute.ItemsRoute>() || it.hasRoute<AppRoute.ItemDetailRoute>()
        } == true
        AppTab.PATCH -> currentDestination?.hierarchy?.any { it.hasRoute<AppRoute.PatchRoute>() } == true
        AppTab.SETTINGS -> currentDestination?.hierarchy?.any {
            it.hasRoute<AppRoute.SettingsRoute>() || it.hasRoute<AppRoute.KnowledgeBaseRoute>()
        } == true
    }

    AppUiTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = AppTheme.colors.headerContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
                        ),
                        header = { Spacer(Modifier.height(AppDimens.Space12)) }
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = isTabSelected(tab)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                },
                                alwaysShowLabel = true,
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = if (useNavigationRail) {
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    } else {
                        WindowInsets.safeDrawing
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail && isTopLevelDestination) {
                            NavigationBar(
                                containerColor = AppTheme.colors.headerContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                tonalElevation = 3.dp
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val selected = isTabSelected(tab)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab) },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        },
                                        alwaysShowLabel = true,
                                        modifier = Modifier.testTag(tab.testTag),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.HomeRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding),
                        enterTransition = {
                            fadeIn(animationSpec = tween(AppDimens.MotionFastMs))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(AppDimens.MotionFastMs))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(AppDimens.MotionFastMs))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(AppDimens.MotionFastMs))
                        }
                    ) {
                        composable<AppRoute.HomeRoute> {
                            HomeScreen(
                                selectedRole = uiState.selectedRole,
                                onRoleChange = { viewModel.selectRole(it, ctx) },
                                selectedRoleType = uiState.selectedRoleType,
                                onRoleTypeChange = { viewModel.selectRoleType(it, ctx) },
                                overlayGranted = uiState.overlayGranted,
                                overlayRunning = uiState.overlayRunning,
                                onLaunchWidget = {
                                    val started = onLaunchWidget(uiState.selectedRole, uiState.selectedRoleType)
                                    if (started) {
                                        viewModel.refreshOverlayState(granted = true, running = true)
                                    }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (started) overlayRunningMsg else overlayPermissionMsg
                                        )
                                    }
                                },
                                onHeroClick = { heroId ->
                                    if (GameData.heroesById.containsKey(heroId)) {
                                        navController.navigate(AppRoute.HeroDetailRoute(heroId))
                                    }
                                }
                            )
                        }

                        composable<AppRoute.HeroesRoute> {
                            HeroesScreen(
                                onHeroClick = { hero ->
                                    navController.navigate(AppRoute.HeroDetailRoute(hero.id))
                                }
                            )
                        }

                        composable<AppRoute.HeroDetailRoute> { entry ->
                            val route = entry.toRoute<AppRoute.HeroDetailRoute>()
                            val hero = GameData.heroesById[route.heroId]
                            if (hero != null) {
                                HeroDetailScreen(
                                    hero = hero,
                                    onBack = { navController.popBackStack() },
                                    onHeroClick = { target ->
                                        navController.navigate(AppRoute.HeroDetailRoute(target.id))
                                    },
                                    onItemClick = { item ->
                                        navController.navigate(AppRoute.ItemDetailRoute(item.id))
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EmptyState(
                                        icon = AppIcons.Person,
                                        titleRes = R.string.state_empty_title,
                                        messageRes = R.string.state_empty_message,
                                        actionLabelRes = R.string.action_back,
                                        onAction = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        composable<AppRoute.ItemsRoute> {
                            ItemsScreen(
                                onItemClick = { item ->
                                    navController.navigate(AppRoute.ItemDetailRoute(item.id))
                                }
                            )
                        }

                        composable<AppRoute.ItemDetailRoute> { entry ->
                            val route = entry.toRoute<AppRoute.ItemDetailRoute>()
                            val item = GameData.itemsById[route.itemId]
                            if (item != null) {
                                ItemDetailScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onItemClick = { target ->
                                        navController.navigate(AppRoute.ItemDetailRoute(target.id))
                                    },
                                    onHeroClick = { hero ->
                                        navController.navigate(AppRoute.HeroDetailRoute(hero.id))
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EmptyState(
                                        icon = AppIcons.Apps,
                                        titleRes = R.string.state_empty_title,
                                        messageRes = R.string.state_empty_message,
                                        actionLabelRes = R.string.action_back,
                                        onAction = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        composable<AppRoute.PatchRoute> {
                            PatchNotesScreen(
                                onHeroClick = { heroId ->
                                    if (GameData.heroesById.containsKey(heroId)) {
                                        navController.navigate(AppRoute.HeroDetailRoute(heroId))
                                    }
                                },
                                onItemClick = { itemId ->
                                    if (GameData.itemsById.containsKey(itemId)) {
                                        navController.navigate(AppRoute.ItemDetailRoute(itemId))
                                    }
                                }
                            )
                        }

                        composable<AppRoute.SettingsRoute> {
                            SettingsScreen(
                                overlayGranted = uiState.overlayGranted,
                                onOverlayGrantedChange = { wanted ->
                                    if (wanted) {
                                        ctx.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:${ctx.packageName}".toUri()
                                            )
                                        )
                                    }
                                },
                                autoStartLastRole = uiState.autoStartLastRole,
                                onAutoStartChange = { enabled ->
                                    viewModel.setAutoStartLastRole(enabled, ctx)
                                },
                                onOpenKnowledgeBase = {
                                    navController.navigate(AppRoute.KnowledgeBaseRoute)
                                }
                            )
                        }

                        composable<AppRoute.KnowledgeBaseRoute> {
                            KnowledgeBaseScreen(
                                onBack = { navController.popBackStack() },
                                onNavigate = { target ->
                                    when (target) {
                                        KnowledgeActionTarget.HEROES -> navigateToTab(AppTab.HEROES)
                                        KnowledgeActionTarget.ITEMS -> navigateToTab(AppTab.ITEMS)
                                        KnowledgeActionTarget.PATCH -> navigateToTab(AppTab.PATCH)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
