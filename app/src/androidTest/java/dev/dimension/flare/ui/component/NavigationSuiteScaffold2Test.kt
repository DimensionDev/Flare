package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.ui.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import soup.compose.material.motion.animation.materialSharedAxisZ
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
class NavigationSuiteScaffold2Test {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationTransitionDoesNotDrawOverNavigationRail() {
        composeRule.mainClock.autoAdvance = false
        val backStack = mutableStateListOf<Route>(Route.Home)

        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalGlobalAppearance provides GlobalAppearance.Default,
                ) {
                    NavigationSuiteScaffold2(
                        navigationSuiteItems = {},
                        secondaryItems = {},
                        wideNavigationRailState = rememberWideNavigationRailState(),
                        modifier = Modifier.fillMaxSize(),
                        bottomBarAutoHideEnabled = false,
                        showFab = false,
                        layoutType = NavigationSuiteType.NavigationRail,
                        containerColor = NAVIGATION_RAIL_COLOR,
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .testTag(NAV_DISPLAY_TAG),
                            transitionSpec = { materialSharedAxisZ(true) },
                            popTransitionSpec = { materialSharedAxisZ(false) },
                            entryProvider =
                                entryProvider {
                                    entry<Route.Home> {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Red),
                                        )
                                    }
                                    entry<Route.Notification> {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Blue),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val navBounds =
            composeRule
                .onNodeWithTag(NAV_DISPLAY_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val sampleX = navBounds.left.roundToInt() - 2
        val sampleY = navBounds.center.y.roundToInt()
        val railColorBeforeTransition =
            composeRule.onRoot().captureToImage().toPixelMap()[sampleX, sampleY]

        composeRule.runOnIdle {
            backStack += Route.Notification
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(50)

        val railColorDuringTransition =
            composeRule.onRoot().captureToImage().toPixelMap()[sampleX, sampleY]

        assertEquals(railColorBeforeTransition.red, railColorDuringTransition.red, COLOR_TOLERANCE)
        assertEquals(railColorBeforeTransition.green, railColorDuringTransition.green, COLOR_TOLERANCE)
        assertEquals(railColorBeforeTransition.blue, railColorDuringTransition.blue, COLOR_TOLERANCE)
    }

    @Test
    fun fullscreenDialogOverlayIsNotClippedToNavigationContent() {
        val backStack = mutableStateListOf<Route>(Route.Home, Route.Notification)

        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalGlobalAppearance provides GlobalAppearance.Default,
                ) {
                    NavigationSuiteScaffold2(
                        navigationSuiteItems = {},
                        secondaryItems = {},
                        wideNavigationRailState = rememberWideNavigationRailState(),
                        modifier = Modifier.fillMaxSize(),
                        bottomBarAutoHideEnabled = false,
                        showFab = false,
                        layoutType = NavigationSuiteType.NavigationRail,
                        containerColor = NAVIGATION_RAIL_COLOR,
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .testTag(NAV_DISPLAY_TAG),
                            sceneStrategies = listOf(DialogSceneStrategy()),
                            entryProvider =
                                entryProvider {
                                    entry<Route.Home> {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Red),
                                        )
                                    }
                                    entry<Route.Notification>(
                                        metadata =
                                            DialogSceneStrategy.dialog(
                                                DialogProperties(
                                                    usePlatformDefaultWidth = false,
                                                    decorFitsSystemWindows = false,
                                                ),
                                            ),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(MEDIA_OVERLAY_COLOR)
                                                .testTag(MEDIA_OVERLAY_TAG),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val navigationContentBounds =
            composeRule
                .onNodeWithTag(NAV_DISPLAY_TAG)
                .fetchSemanticsNode()
                .boundsInWindow
        val overlayBounds =
            composeRule
                .onNodeWithTag(MEDIA_OVERLAY_TAG)
                .fetchSemanticsNode()
                .boundsInWindow

        assertTrue(
            "Expected the fullscreen dialog to extend beyond the navigation content: " +
                "overlay=$overlayBounds, navigationContent=$navigationContentBounds",
            overlayBounds.left < navigationContentBounds.left,
        )
    }

    private companion object {
        const val NAV_DISPLAY_TAG = "nav-display"
        const val MEDIA_OVERLAY_TAG = "media-overlay"
        val NAVIGATION_RAIL_COLOR = Color.Green
        val MEDIA_OVERLAY_COLOR = Color.Magenta
        const val COLOR_TOLERANCE = 0.01f
    }
}
