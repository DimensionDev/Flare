package dev.dimension.flare.ui.screen.media

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalMediaSharedElementGroup
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.SurfaceBindingManager
import dev.dimension.flare.ui.component.mediaSharedElementDestination
import dev.dimension.flare.ui.component.mediaSharedElementSource
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.route.Route
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class, ExperimentalComposeUiApi::class)
class MediaSharedTransitionTest {
    @get:Rule
    val composeRule =
        createComposeRule(
            ComposeUiTestConfig(
                effectContext =
                    object : MotionDurationScale {
                        override val scaleFactor: Float = 1f
                    },
            ),
        )

    private val firstImage = testImage("first", android.graphics.Color.BLUE)
    private val secondImage = testImage("second", android.graphics.Color.GREEN)
    private val stack = mutableStateListOf<NavKey>(Route.Home)
    private val selected = mutableStateOf(firstImage)
    private val sourceOffset = mutableStateOf(DpOffset.Zero)
    private lateinit var host: MediaSharedTransitionHostState
    private lateinit var dispatcher: OnBackPressedDispatcher
    private lateinit var drag: MediaDismissState
    private var barClicks = 0

    @Test
    fun coversFloatingBottomBar() {
        setContent(NavigationSuiteType.NavigationBar, BottomBarStyle.Floating)
        assertCoversNavigation()
    }

    @Test
    fun coversClassicBottomBar() {
        setContent(NavigationSuiteType.NavigationBar, BottomBarStyle.Classic)
        assertCoversNavigation()
    }

    @Test
    fun coversNavigationRail() {
        setContent()
        assertCoversNavigation()
    }

    @Test
    fun partiallyVisibleSourceUsesOfficialSharedTransitionInBothDirections() {
        sourceOffset.value = DpOffset(0.dp, 40.dp)
        setContent()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("source_1").performTouchInput { click(Offset(width / 2f, 20f)) }
            composeRule.waitUntil(timeoutMillis = 3_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.shared.scope.isTransitionActive
            }
            composeRule.runOnUiThread { assertEquals(1L, host.presentations.single().group) }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle { host.presentations.single().dismiss() }
            composeRule.waitUntil(timeoutMillis = 3_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.shared.scope.isTransitionActive
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
        composeRule.onNodeWithTag("source_1").assertExists()
    }

    @Test
    fun draggingDownRevealsTimelineBeforeClosing() = assertDragRevealsTimeline(1f)

    @Test
    fun draggingUpRevealsTimelineBeforeClosing() = assertDragRevealsTimeline(-1f)

    @Test
    fun shortDragReturnsToTheViewer() {
        setContent()
        openMedia()
        composeRule.onNodeWithTag("media_shared_viewer").performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 0.12f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0f, drag.offset, 0.01f)
            assertEquals(2, stack.size)
        }
    }

    @Test
    fun deepLinkWithoutAPressedSourceUsesTheDefaultFade() {
        setContent()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { stack.add(Route.Media.Image(firstImage, firstImage)) }
            composeRule.waitUntil(timeoutMillis = 3_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.presentations.isNotEmpty()
            }
            composeRule.runOnUiThread {
                assertEquals(null, host.presentations.single().group)
                assertFalse(host.shared.scope.isTransitionActive)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
    }

    @Test
    fun realImageResetsZoomBeforeClosingAndStillSupportsSwipeDismiss() {
        setContent(medias = persistentListOf(UiMedia.Image(firstImage, firstImage, "Test image", 40f, 40f, false)))
        openMedia()
        composeRule.onNodeWithContentDescription("Test image").performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                start1 = center + Offset(60f, 0f),
                end0 = center - Offset(240f, 0f),
                end1 = center + Offset(240f, 0f),
                durationMillis = 400,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Test image").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertEquals(2, stack.size)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertExists()
        composeRule.onNodeWithContentDescription("Test image").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
    }

    @Test
    fun horizontalSwipeChangesTheImageAndItsReturnTarget() {
        setContent(
            medias =
                persistentListOf(
                    UiMedia.Image(firstImage, firstImage, "First image", 40f, 40f, false),
                    UiMedia.Image(secondImage, secondImage, "Second image", 40f, 40f, false),
                ),
        )
        openMedia()
        composeRule.onNodeWithContentDescription("First image").performTouchInput {
            down(Offset(width * 0.85f, centerY))
            repeat(8) { moveBy(Offset(-1f, 0f)) }
            repeat(12) { moveBy(Offset(-width * 0.055f, 0f)) }
            up()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(secondImage, host.shared.hiddenKey?.preview) }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { host.presentations.single().dismiss() }
            composeRule.waitUntil(timeoutMillis = 3_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.shared.scope.isTransitionActive
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        assertEquals(1, stack.size)
    }

    @Test
    fun mediaOpenedFromADialogUsesTheDialogFallback() {
        setContent()
        composeRule.runOnIdle { stack.add(Route.Status.AltText("Test")) }
        composeRule.onNodeWithTag("dialog_source").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(host.presentations.isEmpty())
            assertEquals(null, host.shared.pressedKey)
            assertFalse(host.shared.scope.isTransitionActive)
        }
        composeRule.onNodeWithTag("media_page").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_page").assertDoesNotExist()
        composeRule.onNodeWithTag("dialog_source").assertExists()
        assertEquals(2, stack.size)
    }

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun videoStillPlaysOnSurfaceViewAndCanBeSwipedClosed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "media_shared_red.mp4")
        instrumentation.context.assets
            .open("media_shared_red.mp4")
            .use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.fromFile(file).toString()
        setContent(medias = persistentListOf(UiMedia.Video(uri, firstImage, "Test video", 90f, 160f)))
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("source_1").performTouchInput { click() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.presentations
                    .singleOrNull()
                    ?.visibility
                    ?.isIdle == true && !host.shared.scope.isTransitionActive
            }
            val manager = GlobalContext.get().get<SurfaceBindingManager>()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.runOnUiThread { manager.playerFor(uri)?.isPlaying == true }
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.mainClock.advanceTimeByFrame()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                val pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                screenshot.recycle()
                android.graphics.Color.red(pixel) > 200 && android.graphics.Color.blue(pixel) < 50
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val letterbox = screenshot.getPixel(screenshot.width / 2, screenshot.height / 3)
            screenshot.recycle()
            assertTrue("The thumbnail must not remain behind the ready SurfaceView", android.graphics.Color.blue(letterbox) < 50)
            composeRule.onNodeWithTag("media_shared_viewer").performTouchInput { swipeDown() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.presentations.isEmpty()
            }
            assertEquals(1, stack.size)
        } finally {
            composeRule.runOnUiThread { host.presentations.toList().forEach { it.dismiss() } }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.mainClock.advanceTimeByFrame()
                host.presentations.isEmpty()
            }
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
    }

    private fun assertCoversNavigation() {
        val initial = composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot
        openMedia()
        val viewer = composeRule.onNodeWithTag("media_shared_viewer").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.width, viewer.width, 1f)
        assertEquals(root.height, viewer.height, 1f)
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        assertEquals(1f, pixels[1, pixels.height / 2].blue, 0.02f)
        composeRule.onNodeWithTag("media_shared_viewer").performTouchInput { click(bottomCenter) }
        assertEquals(0, barClicks)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
        assertEquals(initial, composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot)
    }

    private fun assertDragRevealsTimeline(direction: Float) {
        setContent()
        openMedia()
        composeRule.onNodeWithTag("media_shared_viewer").performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 0.4f * direction))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("Keep the timeline and viewer mounted while dragging", 2, stack.size) }
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val y = if (direction > 0f) 10 else pixels.height - 10
        val revealed = pixels[pixels.width / 2, y]
        assertTrue("The white timeline must be visible through the scrim while the finger is down", revealed.red > 0.15f)
        composeRule.onNodeWithTag("media_shared_viewer").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertDoesNotExist()
        assertEquals(1, stack.size)
    }

    private fun openMedia() {
        composeRule.onNodeWithTag("source_1").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_shared_viewer").assertExists()
    }

    private fun setContent(
        layout: NavigationSuiteType = NavigationSuiteType.NavigationRail,
        style: BottomBarStyle = BottomBarStyle.Floating,
        medias: ImmutableList<UiMedia> = persistentListOf(),
    ) {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        composeRule.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalGlobalAppearance provides GlobalAppearance.Default.copy(bottomBarStyle = style)) {
                    MediaSharedTransitionHost {
                        host = checkNotNull(LocalMediaSharedTransitionHost.current)
                        dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                        NavigationSuiteScaffold2(
                            navigationSuiteItems = {
                                item(selected = true, onClick = { barClicks++ }, icon = { Text("Home") }, label = { Text("Home") })
                            },
                            secondaryItems = {},
                            wideNavigationRailState = rememberWideNavigationRailState(),
                            layoutType = layout,
                            bottomBarAutoHideEnabled = false,
                            showFab = false,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            NavDisplay(
                                backStack = stack,
                                entryDecorators =
                                    listOf(
                                        rememberSaveableStateHolderNavEntryDecorator(),
                                        rememberViewModelStoreNavEntryDecorator(),
                                    ),
                                onBack = { stack.removeLastOrNull() },
                                sceneStrategies = remember(host) { listOf(MediaSharedSceneStrategy(host), DialogSceneStrategy()) },
                                entryProvider =
                                    entryProvider {
                                        entry<Route.Home> {
                                            CompositionLocalProvider(LocalMediaSharedElementGroup provides 1L) {
                                                Column(Modifier.fillMaxSize().background(Color.White).testTag("timeline")) {
                                                    Box(Modifier.size(100.dp).clipToBounds()) {
                                                        Box(
                                                            Modifier
                                                                .offset(sourceOffset.value.x, sourceOffset.value.y)
                                                                .size(100.dp)
                                                                .mediaSharedElementSource(firstImage)
                                                                .background(Color.Blue)
                                                                .testTag("source_1")
                                                                .clickable { stack.add(Route.Media.Image(firstImage, firstImage)) },
                                                        )
                                                    }
                                                    Box(Modifier.size(80.dp).mediaSharedElementSource(secondImage).background(Color.Green))
                                                }
                                            }
                                        }
                                        entry<Route.Status.AltText>(metadata = DialogSceneStrategy.dialog()) {
                                            Box(
                                                Modifier
                                                    .size(160.dp)
                                                    .mediaSharedElementSource(firstImage)
                                                    .background(Color.Blue)
                                                    .testTag("dialog_source")
                                                    .clickable { stack.add(Route.Media.Image(firstImage, firstImage)) },
                                            )
                                        }
                                        entry<Route.Media.Image>(metadata = {
                                            DialogSceneStrategy.dialog(
                                                DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                                            ) +
                                                (MEDIA_SHARED_ROUTE to it)
                                        }) {
                                            val dismiss: () -> Unit = LocalMediaSharedDismiss.current ?: { stack.removeLastOrNull() }
                                            if (medias.isNotEmpty()) {
                                                RawMediaScreen(medias, 0, firstImage, dismiss, {}, LocalUriHandler.current)
                                            } else {
                                                val state = remember { MediaDismissState() }
                                                SideEffect { drag = state }
                                                Box(
                                                    Modifier.fillMaxSize().testTag("media_page").background(
                                                        Color.Black.copy(
                                                            alpha =
                                                                1f - state.progress,
                                                        ),
                                                    ),
                                                ) {
                                                    MediaDismissGesture(state, enabled = true, onDismiss = dismiss) {
                                                        Box(
                                                            Modifier
                                                                .fillMaxSize()
                                                                .mediaSharedElementDestination(selected.value)
                                                                .background(if (selected.value == firstImage) Color.Blue else Color.Green),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun testImage(
        name: String,
        color: Int,
    ): String {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "media_shared_$name.png")
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }
}
