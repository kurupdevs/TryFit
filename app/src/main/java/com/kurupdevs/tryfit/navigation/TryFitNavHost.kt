package com.kurupdevs.tryfit.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kurupdevs.tryfit.TryFitApplication
import com.kurupdevs.tryfit.data.PrefsKeys
import com.kurupdevs.tryfit.ui.components.FloatingNavBar
import com.kurupdevs.tryfit.ui.screens.assistant.AssistantScreen
import com.kurupdevs.tryfit.ui.screens.home.HomeScreen
import com.kurupdevs.tryfit.ui.screens.notifications.NotificationsScreen
import com.kurupdevs.tryfit.ui.screens.onboarding.OnboardingScreen
import com.kurupdevs.tryfit.ui.screens.product.ProductDetailScreen
import com.kurupdevs.tryfit.ui.screens.profile.BodyProfileScreen
import com.kurupdevs.tryfit.ui.screens.settings.SettingsScreen
import com.kurupdevs.tryfit.ui.screens.tryon.TryOnFlowScreen
import com.kurupdevs.tryfit.ui.screens.tryon.TryOnScreen
import com.kurupdevs.tryfit.ui.screens.wardrobe.WardrobeScreen
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import com.kurupdevs.tryfit.ui.theme.TryFitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Root navigation host.
 *
 * - [TryFitRoutes.ONBOARDING] first (skipped for returning users via the
 *   DataStore flag), then the 5-tab scaffold.
 * - Tab switches use saveState/restoreState so each tab keeps its own back
 *   stack (SPEC §4); re-tap scroll-to-top lands in Phase 1.
 * - Push routes (product detail, try-on flow, notifications, body profile)
 *   hide the floating nav.
 * - The center AI orb does NOT navigate: it stretches into a bottom sheet
 *   (SPEC §3.6, 320ms) hosting [AssistantScreen].
 * - The NavHost sits inside a [SharedTransitionLayout] so the home hero image
 *   shared-elements into [ProductDetailScreen] (SPEC §4, 340ms).
 * - Onboarding→Home: photo fade+scale→1.06 (320ms exit), content fade+slideUp
 *   24dp (350ms enter) + the home feed's own 60ms stagger (SPEC §4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryFitApp() {
    TryFitTheme {
        val context = LocalContext.current
        val container = remember {
            (context.applicationContext as TryFitApplication).container
        }

        // Returning users skip onboarding (flag written by OnboardingScreen).
        var prefsReady by remember { mutableStateOf(false) }
        var onboardingDone by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            onboardingDone = container.prefs.data
                .map { it[PrefsKeys.ONBOARDING_DONE] == true }
                .first()
            prefsReady = true
        }

        if (prefsReady) {
            MainNavHost(onboardingDone = onboardingDone)
        } else {
            // Cold start: plain white until the flag loads (splash covers this).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

/**
 * The nav graph + floating nav + assistant sheet. Split out of [TryFitApp]
 * so the onboarding-gate above never needs a non-local return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavHost(onboardingDone: Boolean) {
    val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val sheetScope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showAssistantSheet by rememberSaveable { mutableStateOf(false) }

        val selectedTab = if (showAssistantSheet) BottomTab.ASSISTANT
        else BottomTab.fromRoute(currentRoute)
        val showNavBar = currentRoute in TabRoutes

        // Hide-on-scroll state, driven by HomeScreen (SPEC §4); reset on route change.
        var navVisible by remember { mutableStateOf(true) }
        LaunchedEffect(currentRoute) { navVisible = true }

        Box(modifier = Modifier.fillMaxSize()) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone) TryFitRoutes.HOME else TryFitRoutes.ONBOARDING,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(
                        route = TryFitRoutes.ONBOARDING,
                        exitTransition = {
                            fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 1.06f)
                        }
                    ) {
                        OnboardingScreen(
                            onFinished = {
                                navController.navigate(TryFitRoutes.HOME) {
                                    popUpTo(TryFitRoutes.ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = TryFitRoutes.HOME,
                        enterTransition = {
                            fadeIn(tween(350)) + slideInVertically(tween(350)) { 24 }
                        }
                    ) {
                        HomeScreen(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            onProductClick = { id ->
                                navController.navigate(TryFitRoutes.productDetail(id))
                            },
                            onTryOnClick = { id ->
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            },
                            onTryLook = { ids ->
                                navController.navigate(TryFitRoutes.tryOnLook(ids))
                            },
                            onNotificationsClick = {
                                navController.navigate(TryFitRoutes.NOTIFICATIONS)
                            },
                            onWardrobeClick = { navController.navigateToTab(BottomTab.WARDROBE) },
                            onNavVisibilityChange = { navVisible = it }
                        )
                    }
                    composable(TryFitRoutes.TRY_ON) {
                        TryOnScreen(
                            onNewTryOn = { navController.navigate(TryFitRoutes.tryOnFlow()) },
                            onTryProduct = { id ->
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            },
                            onScreenshotTryOn = {
                                navController.navigate(TryFitRoutes.tryOnFlow(screenshot = true))
                            },
                        )
                    }
                    // Kept registered so deep links / tests can land here; the orb
                    // itself opens the bottom sheet above instead of navigating.
                    composable(TryFitRoutes.ASSISTANT) {
                        AssistantScreen(
                            onTryOnClick = { id ->
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            }
                        )
                    }
                    composable(TryFitRoutes.WARDROBE) {
                        WardrobeScreen(
                            onProductClick = { id -> navController.navigate(TryFitRoutes.productDetail(id)) },
                            onBrowseProducts = { navController.navigateToTab(BottomTab.HOME) },
                            onTryProduct = { id ->
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            },
                            onTryLook = { ids ->
                                navController.navigate(TryFitRoutes.tryOnLook(ids))
                            },
                            onOpenResult = { sessionId ->
                                navController.navigate(TryFitRoutes.tryOnResult(sessionId))
                            },
                            onNotificationsClick = { navController.navigate(TryFitRoutes.NOTIFICATIONS) },
                            onStartTryOn = { navController.navigateToTab(BottomTab.TRY_ON) },
                        )
                    }
                    composable(TryFitRoutes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onBodyProfileClick = { navController.navigate(TryFitRoutes.BODY_PROFILE) },
                            onLogout = {
                                navController.navigate(TryFitRoutes.ONBOARDING) {
                                    popUpTo(TryFitRoutes.ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(
                        route = TryFitRoutes.PRODUCT_DETAIL,
                        arguments = listOf(navArgument("productId") { type = NavType.StringType })
                    ) { entry ->
                        ProductDetailScreen(
                            productId = entry.arguments?.getString("productId").orEmpty(),
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            onBack = { navController.popBackStack() },
                            onTryOn = { id ->
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            }
                        )
                    }
                    composable(
                        route = TryFitRoutes.TRY_ON_FLOW,
                        arguments = listOf(
                            navArgument("productId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("screenshot") {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument("resultSessionId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { entry ->
                        TryOnFlowScreen(
                            productId = entry.arguments?.getString("productId"),
                            screenshot = entry.arguments?.getBoolean("screenshot") == true,
                            resultSessionId = entry.arguments?.getString("resultSessionId"),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = TryFitRoutes.TRY_ON_RESULT,
                        arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                    ) { entry ->
                        TryOnFlowScreen(
                            productId = null,
                            screenshot = false,
                            resultSessionId = entry.arguments?.getString("sessionId"),
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(TryFitRoutes.NOTIFICATIONS) {
                        NotificationsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenResult = { sessionId ->
                                navController.navigate(TryFitRoutes.tryOnResult(sessionId))
                            },
                        )
                    }
                    composable(TryFitRoutes.BODY_PROFILE) {
                        BodyProfileScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            FloatingNavBar(
                selectedTab = selectedTab,
                visible = showNavBar && navVisible,
                onTabSelected = { tab ->
                    if (tab == BottomTab.ASSISTANT) {
                        // Orb press: ripple (in the bar) -> sheet stretches up (320ms).
                        showAssistantSheet = true
                    } else {
                        navController.navigateToTab(tab)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TryFitSpacing.NavBottomOffset)
            )

            if (showAssistantSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAssistantSheet = false },
                    sheetState = sheetState,
                    containerColor = TryFitColors.BgScreen,
                    dragHandle = null
                ) {
                    AssistantScreen(
                        onTryOnClick = { id ->
                            sheetScope.launch {
                                // Let the sheet animate out before pushing the flow.
                                sheetState.hide()
                                showAssistantSheet = false
                                navController.navigate(TryFitRoutes.tryOnFlow(productId = id))
                            }
                        }
                    )
                }
            }
        }
    }

private fun NavHostController.navigateToTab(tab: BottomTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
