@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.demo

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.entryProvider
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.compose.AndroidComposeNavigationRendererPlugin
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.navigation.NavigationBackRequest
import dev.dimension.flare.ui.navigation.NavigationDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class FlareComposeNavigationLifecycleTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun keepsPredictiveSnapshotWithoutKeepingItsEffectsActive() {
        val activeEntries = mutableStateListOf<String>()
        val realizationCounts = mutableMapOf<String, Int>()
        val disposalCounts = mutableMapOf<String, Int>()
        val widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeNavigationRendererPlugin)
        lateinit var backStack: SnapshotStateList<LifecycleRoute>
        lateinit var refreshModel: () -> Unit

        composeRule.setContent {
            var modelVersion by remember { mutableIntStateOf(0) }
            val appliedModelVersion = modelVersion
            refreshModel = { modelVersion += 1 }
            backStack =
                remember {
                    mutableStateListOf<LifecycleRoute>(
                        LifecycleHome,
                        LifecycleDetail,
                    )
                }
            val provider =
                remember {
                    entryProvider<LifecycleRoute> {
                        entry<LifecycleHome> {
                            TrackedPage(
                                label = "home",
                                activeEntries = activeEntries,
                                realizationCounts = realizationCounts,
                                disposalCounts = disposalCounts,
                            )
                        }
                        entry<LifecycleDetail> {
                            TrackedPage(
                                label = "detail",
                                activeEntries = activeEntries,
                                realizationCounts = realizationCounts,
                                disposalCounts = disposalCounts,
                            )
                        }
                    }
                }
            FlareComposeHost(
                widgetSystem = widgetSystem,
            ) {
                NavigationDisplay(
                    backStack = backStack,
                    onBack = { request ->
                        check(appliedModelVersion >= 0)
                        request.applyTo(backStack)
                    },
                    entryProvider = provider,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            activeEntries.toList() == listOf("detail")
        }
        composeRule.runOnIdle {
            assertEquals(mapOf("home" to 1, "detail" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1), disposalCounts)
            refreshModel()
        }
        composeRule.runOnIdle {
            assertEquals(mapOf("home" to 1, "detail" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1), disposalCounts)
            backStack.removeAt(backStack.lastIndex)
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertEquals(listOf("home"), activeEntries.toList())
            assertEquals(mapOf("home" to 2, "detail" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1, "detail" to 1), disposalCounts)
        }
    }

    @Test
    public fun acceptedComposeBackRequestIsTerminalAndLateApplyIsNoOp() {
        val widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeNavigationRendererPlugin)
        lateinit var backStack: SnapshotStateList<LifecycleRoute>
        lateinit var backDispatcher: OnBackPressedDispatcher
        var receivedRequest: NavigationBackRequest<LifecycleRoute>? = null

        composeRule.setContent {
            backDispatcher =
                checkNotNull(LocalView.current.findViewTreeOnBackPressedDispatcherOwner())
                    .onBackPressedDispatcher
            backStack =
                remember {
                    mutableStateListOf<LifecycleRoute>(
                        LifecycleHome,
                        LifecycleDetail,
                    )
                }
            val provider =
                remember {
                    entryProvider<LifecycleRoute> {
                        entry<LifecycleHome> {
                            Text("home")
                        }
                        entry<LifecycleDetail> {
                            Text("detail")
                        }
                    }
                }
            FlareComposeHost(widgetSystem = widgetSystem) {
                NavigationDisplay(
                    backStack = backStack,
                    onBack = { receivedRequest = it },
                    entryProvider = provider,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            backDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { receivedRequest != null }
        composeRule.runOnIdle {
            val request = checkNotNull(receivedRequest)
            assertEquals(listOf(LifecycleHome, LifecycleDetail), request.base)
            assertEquals(listOf(LifecycleHome), request.target)
            assertTrue(request.isActive)
            assertTrue(request.accept())
            assertFalse(request.isActive)
            assertFalse(request.reject())
            assertFalse(request.applyTo(backStack))
            assertEquals(listOf(LifecycleHome, LifecycleDetail), backStack.toList())
        }
    }
}

private sealed interface LifecycleRoute

private data object LifecycleHome : LifecycleRoute

private data object LifecycleDetail : LifecycleRoute

@Composable
@FlareUiComposable
private fun TrackedPage(
    label: String,
    activeEntries: MutableList<String>,
    realizationCounts: MutableMap<String, Int>,
    disposalCounts: MutableMap<String, Int>,
) {
    DisposableEffect(label) {
        activeEntries += label
        realizationCounts[label] = realizationCounts.getOrElse(label) { 0 } + 1
        onDispose {
            activeEntries -= label
            disposalCounts[label] = disposalCounts.getOrElse(label) { 0 } + 1
        }
    }
    Text(label)
}
