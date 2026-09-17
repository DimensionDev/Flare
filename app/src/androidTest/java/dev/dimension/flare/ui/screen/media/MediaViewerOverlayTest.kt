package dev.dimension.flare.ui.screen.media

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
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
import dev.dimension.flare.ui.component.LocalMediaTransitionGroup
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.SurfaceBindingManager
import dev.dimension.flare.ui.component.mediaTransitionSource
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.route.Route
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class, ExperimentalComposeUiApi::class)
class MediaViewerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val firstImage = testImage("first", android.graphics.Color.BLUE)
    private val secondImage = testImage("second", android.graphics.Color.GREEN)
    private val stack = mutableStateListOf<NavKey>(Route.Home)
    private val selected = mutableStateOf(firstImage)
    private val showSecondSource = mutableStateOf(true)
    private lateinit var dispatcher: OnBackPressedDispatcher
    private lateinit var viewer: MediaViewerOverlayState
    private var barClicks = 0

    @Test
    fun coversNavigationRailAndRestoresItWithoutMovingContent() {
        setContent(NavigationSuiteType.NavigationRail)
        assertCoversNavigation()
    }

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
    fun openingExpandsFromTheThumbnailWhileTheSourceLayoutStaysInPlace() {
        setContent()
        val source = composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        openMedia()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("media_hero").fetchSemanticsNodes().isNotEmpty()
        }
        val firstFrame = composeRule.onNodeWithTag("media_hero").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(96)
        val middleFrame = composeRule.onNodeWithTag("media_hero").fetchSemanticsNode().boundsInRoot
        assertTrue(firstFrame.width >= source.width)
        assertTrue(middleFrame.width > firstFrame.width)
        assertEquals(source, composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag("media_hero").assertDoesNotExist()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun predictiveBackCancellationKeepsViewerAndSourceThenCommitCloses() {
        setContent()
        openMedia()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.6f))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
        composeRule.runOnIdle {
            assertEquals(2, stack.size)
            assertEquals(1f, viewer.progress.value, 0.001f)
            assertTrue(viewer.isInteractive)
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.7f))
            dispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        assertEquals(1, stack.size)
    }

    @Test
    fun returningToAnOffscreenPageFadesWithoutScrollingOrMatchingAnotherImage() {
        setContent()
        openMedia()
        composeRule.runOnIdle {
            selected.value = secondImage
            showSecondSource.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            viewer.beginReturn()
            assertFalse(viewer.hasHero)
            viewer.requestDismiss()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_2").assertDoesNotExist()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
    }

    @Test
    fun currentPageAndDragPositionAreUsedForReturn() {
        setContent()
        openMedia()
        composeRule.runOnIdle { selected.value = secondImage }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            viewer.dragY = 180f
            viewer.beginReturn()
            assertTrue(viewer.hasHero)
        }
        composeRule.waitForIdle()
        val heroBounds = composeRule.onNodeWithTag("media_hero").fetchSemanticsNode().boundsInRoot
        assertTrue(heroBounds.top > 0f)
        composeRule.runOnIdle { runBlocking { viewer.progress.snapTo(0f) } }
        composeRule.waitForIdle()
        val destination = composeRule.onNodeWithTag("source_2").fetchSemanticsNode().boundsInRoot
        val returned = composeRule.onNodeWithTag("media_hero").fetchSemanticsNode().boundsInRoot
        assertEquals(destination.top, returned.top, 1f)
        assertEquals(destination.width, returned.width, 1f)
        composeRule.runOnIdle { viewer.requestDismiss() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_2").assertExists()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
    }

    @Test
    fun swipeDismissesAndRepeatedCloseOnlyPopsOnce() {
        setContent()
        openMedia()
        composeRule.onNodeWithTag("media_viewer_overlay").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, 1f)) }
            repeat(8) { moveBy(Offset(0f, 60f)) }
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        composeRule.runOnIdle {
            viewer.requestDismiss()
            viewer.requestDismiss()
            assertEquals(listOf<NavKey>(Route.Home), stack.toList())
        }
    }

    @Test
    fun deepLinkWithoutASourceUsesFade() {
        setContent()
        composeRule.runOnIdle { stack.add(Route.Media.Image(firstImage, firstImage)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            viewer.beginReturn()
            assertFalse(viewer.hasHero)
            viewer.requestDismiss()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
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
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
        composeRule.runOnIdle { assertEquals(2, stack.size) }
        composeRule.onNodeWithContentDescription("Test image").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
    }

    @Test
    fun slowHorizontalSwipeChangesTheImageAndItsReturnTarget() {
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
        composeRule.runOnIdle {
            assertEquals(secondImage, viewer.viewport?.url)
            viewer.beginReturn()
            assertTrue(viewer.hasHero)
            viewer.requestDismiss()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
    }

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun videoPlaysInsideTheOverlayAndSupportsSwipeDismiss() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "media_overlay_red.mp4")
        instrumentation.context.assets.open("media_overlay_red.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val uri = Uri.fromFile(file).toString()
        setContent(medias = persistentListOf(UiMedia.Video(uri, firstImage, "Test video", 90f, 160f)))
        composeRule.mainClock.autoAdvance = false
        openMedia()
        composeRule.mainClock.advanceTimeBy(500)
        val manager = GlobalContext.get().get<SurfaceBindingManager>()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { manager.playerFor(uri)?.isPlaying == true }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.mainClock.advanceTimeByFrame()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
            screenshot.recycle()
            android.graphics.Color.red(pixel) > 200 && android.graphics.Color.blue(pixel) < 50
        }
        composeRule.onNodeWithTag("media_viewer_overlay").performTouchInput { swipeDown() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("media_viewer_overlay").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        assertEquals(1, stack.size)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun mediaOpenedAboveADialogUsesItsOwnWindowAndReturnsToTheDialog() {
        setContent()
        composeRule.runOnIdle { stack.add(Route.Status.AltText("Test")) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { stack.add(Route.Media.Image(firstImage, firstImage)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
        composeRule.runOnIdle {
            viewer.beginReturn()
            assertFalse(viewer.hasHero)
            viewer.requestDismiss()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("underlying_dialog").assertExists()
        composeRule.runOnIdle { assertEquals(2, stack.size) }
    }

    private fun assertCoversNavigation() {
        val initial = composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot
        openMedia()
        val overlay = composeRule.onNodeWithTag("media_viewer_overlay").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.width, overlay.width, 1f)
        assertEquals(root.height, overlay.height, 1f)
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val color = pixels[1, pixels.height / 2]
        assertEquals(Color.Blue.blue, color.blue, 0.02f)
        composeRule.onNodeWithTag("media_viewer_overlay").performTouchInput { click(bottomCenter) }
        assertEquals(0, barClicks)
        composeRule.runOnIdle { viewer.requestDismiss() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        assertEquals(initial, composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot)
    }

    private fun openMedia() {
        composeRule.onNodeWithTag("source_1").performTouchInput { click() }
        if (!composeRule.mainClock.autoAdvance) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.onAllNodesWithTag("media_viewer_overlay").fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
    }

    private fun setContent(
        layout: NavigationSuiteType = NavigationSuiteType.NavigationRail,
        style: BottomBarStyle = BottomBarStyle.Floating,
        medias: ImmutableList<UiMedia> = persistentListOf(),
    ) {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalGlobalAppearance provides GlobalAppearance.Default.copy(bottomBarStyle = style)) {
                    MediaViewerOverlayHost {
                        val host = checkNotNull(LocalMediaViewerOverlayHost.current)
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
                                sceneStrategies = remember(host) { listOf(MediaOverlaySceneStrategy(host), DialogSceneStrategy()) },
                                entryProvider =
                                    entryProvider {
                                        entry<Route.Home> {
                                            val group = remember { Any() }
                                            CompositionLocalProvider(LocalMediaTransitionGroup provides group) {
                                                Column(Modifier.fillMaxSize().background(Color.White)) {
                                                    Box(
                                                        Modifier
                                                            .size(
                                                                100.dp,
                                                            ).mediaTransitionSource(
                                                                firstImage,
                                                            ).testTag("source_1")
                                                            .background(Color.Blue)
                                                            .clickable {
                                                                stack.add(Route.Media.Image(firstImage, firstImage))
                                                            },
                                                    )
                                                    if (showSecondSource.value) {
                                                        Box(
                                                            Modifier
                                                                .size(
                                                                    80.dp,
                                                                ).mediaTransitionSource(secondImage)
                                                                .testTag("source_2")
                                                                .background(Color.Green),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        entry<Route.Status.AltText>(metadata = DialogSceneStrategy.dialog()) {
                                            Box(Modifier.size(160.dp).testTag("underlying_dialog"))
                                        }
                                        entry<Route.Media.Image>(metadata = { mediaOverlayMetadata(it) }) {
                                            val current = checkNotNull(LocalMediaViewerOverlay.current)
                                            SideEffect { viewer = current }
                                            if (medias.isNotEmpty()) {
                                                RawMediaScreen(
                                                    medias = medias,
                                                    index = 0,
                                                    preview = firstImage,
                                                    onDismiss = current::requestDismiss,
                                                    toAltText = {},
                                                    uriHandler = LocalUriHandler.current,
                                                )
                                            } else {
                                                MediaOverlayDismissArea(enabled = true) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .mediaViewerViewport(
                                                                selected.value,
                                                                selected.value,
                                                                null,
                                                            ).background(Color.Blue),
                                                    )
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

    private fun backEvent(progress: Float) = BackEventCompat(0f, 300f, progress, BackEventCompat.EDGE_LEFT)

    private fun testImage(
        name: String,
        color: Int,
    ): String {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "media_test_$name.png")
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }
}
