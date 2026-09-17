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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
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
    private val durationScale =
        object : MotionDurationScale {
            var scale = 1f
            override val scaleFactor: Float get() = scale
        }

    @get:Rule
    val composeRule = createComposeRule(ComposeUiTestConfig(effectContext = durationScale))

    private val firstImage = testImage("first", android.graphics.Color.BLUE)
    private val secondImage = testImage("second", android.graphics.Color.GREEN)
    private val stack = mutableStateListOf<NavKey>(Route.Home)
    private val selected = mutableStateOf(firstImage)
    private val showSecondSource = mutableStateOf(true)
    private val sourceOffset = mutableStateOf(DpOffset.Zero)
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
    fun partiallyVisibleThumbnailStillHasAHeroReturn() {
        assertPartiallyVisibleThumbnailUsesHero(DpOffset(0.dp, 40.dp))
    }

    @Test
    fun horizontallyClippedThumbnailStillHasAHeroReturn() {
        assertPartiallyVisibleThumbnailUsesHero(DpOffset(40.dp, 0.dp))
    }

    @Test
    fun completelyClippedThumbnailUsesFadeOnReturn() {
        setContent()
        openMedia()
        composeRule.runOnIdle { sourceOffset.value = DpOffset(0.dp, 120.dp) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            viewer.beginReturn()
            assertFalse("A thumbnail with no visible pixels must not be a return target", viewer.hasHero)
            viewer.requestDismiss()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
    }

    private fun assertPartiallyVisibleThumbnailUsesHero(offset: DpOffset) {
        sourceOffset.value = offset
        setContent()
        val visible = composeRule.onNodeWithTag("source_1").fetchSemanticsNode().boundsInRoot
        val inside = visible.center
        val outside = if (offset.x.value > 0) Offset(visible.right + 2f, inside.y) else Offset(inside.x, visible.bottom + 2f)
        val original = composeRule.onRoot().captureToImage().toPixelMap()
        val expectedInside = original[inside.x.toInt(), inside.y.toInt()]
        val expectedOutside = original[outside.x.toInt(), outside.y.toInt()]
        openMedia()
        composeRule.runOnIdle {
            viewer.beginReturn()
            assertTrue("A thumbnail with a visible part must retain its Hero transition", viewer.hasHero)
            runBlocking { viewer.seekReturn(1f) }
        }
        // Wait for the cached poster, then check the actual pixels at the handoff frame.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val pixels = composeRule.onRoot().captureToImage().toPixelMap()
            pixels[inside.x.toInt(), inside.y.toInt()] == expectedInside
        }
        val returned = composeRule.onRoot().captureToImage().toPixelMap()
        assertEquals(
            "The Hero must not paint the clipped-out part of its thumbnail",
            expectedOutside,
            returned[outside.x.toInt(), outside.y.toInt()],
        )
        composeRule.runOnIdle { viewer.requestDismiss() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
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
        composeRule.mainClock.advanceTimeUntil { viewer.isInteractive }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("media_hero").assertDoesNotExist()
        composeRule.onNodeWithTag("media_viewer_overlay").assertExists()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun backgroundAndControlsAreIndependentOfTheImageSpring() {
        setContent()
        composeRule.mainClock.autoAdvance = false
        openMedia()
        waitForHero()
        composeRule.mainClock.advanceTimeUntil { viewer.progress.value > 0.05f }
        composeRule.runOnIdle {
            assertTrue(viewer.progress.value < 0.35f)
            assertTrue("The background must use its own faster effects spring", viewer.backgroundAlpha > viewer.progress.value)
            assertEquals("Controls wait for the image to start moving", 0f, viewer.controlsAlpha)
        }
        composeRule.mainClock.advanceTimeUntil { viewer.controlsAlpha > 0.5f }
        composeRule.runOnIdle {
            assertTrue("Controls should appear while the image is still moving", viewer.hasHero)
            assertTrue(viewer.progress.value < 1f)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { viewer.requestDismiss() }
        composeRule.mainClock.advanceTimeUntil { viewer.controlsAlpha < 0.01f }
        composeRule.runOnIdle {
            assertTrue("Controls should fade out before the image arrives", viewer.hasHero)
            assertTrue(viewer.progress.value > 0.1f)
            assertTrue("The background should also fade out before the image arrives", viewer.backgroundAlpha < 0.01f)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun predictiveBackDuringOpeningDoesNotJumpToFullScreenAndCanBeCancelled() {
        setContent()
        composeRule.mainClock.autoAdvance = false
        openMedia()
        waitForHero()
        composeRule.mainClock.advanceTimeUntil { viewer.progress.value > 0.2f }
        var start = 0f
        composeRule.runOnIdle {
            start = viewer.progress.value
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.4f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals("Back must seek from the interrupted entrance", start * 0.6f, viewer.progress.value, 0.001f)
            dispatcher.dispatchOnBackCancelled()
        }
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.runOnIdle {
            start = viewer.progress.value
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.2f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals("A second Back must interrupt recovery at its current position", start * 0.8f, viewer.progress.value, 0.001f)
            dispatcher.dispatchOnBackCancelled()
        }
        composeRule.mainClock.advanceTimeByFrame()
        var previous = viewer.progress.value
        repeat(80) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertTrue("Cancellation must converge without replaying the entrance", viewer.progress.value >= previous)
                assertTrue(viewer.progress.value <= 1f)
                previous = viewer.progress.value
            }
        }
        composeRule.runOnIdle {
            assertTrue(viewer.isInteractive)
            assertEquals(2, stack.size)
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun closeButtonCanInterruptTheImageEntranceWithoutGrowingAgain() {
        setContent(medias = persistentListOf(UiMedia.Image(firstImage, firstImage, "Test image", 40f, 40f, false)))
        composeRule.mainClock.autoAdvance = false
        openMedia()
        waitForHero()
        composeRule.mainClock.advanceTimeUntil { viewer.controlsAlpha > 0.5f }
        composeRule.runOnIdle { assertTrue(viewer.hasHero) }
        val closeDescription = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigate_back)
        composeRule.onNodeWithContentDescription(closeDescription).performTouchInput { click() }
        composeRule.mainClock.advanceTimeByFrame()
        var previous = viewer.progress.value
        repeat(80) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertTrue("The interrupted image must shrink immediately", viewer.progress.value <= previous)
                assertTrue(viewer.progress.value >= 0f)
                previous = viewer.progress.value
            }
        }
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        assertEquals(1, stack.size)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun disablingSystemAnimationsSkipsImageBackgroundAndControlMotion() {
        durationScale.scale = 0f
        setContent()
        composeRule.mainClock.autoAdvance = false
        openMedia()
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle {
            assertTrue(viewer.isInteractive)
            assertFalse(viewer.hasHero)
            assertEquals(1f, viewer.backgroundAlpha, 0f)
            assertEquals(1f, viewer.controlsAlpha, 0f)
            viewer.requestDismiss()
        }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
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
        composeRule.runOnIdle { runBlocking { viewer.seekReturn(1f) } }
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
    fun aNewDragInterruptsSpringRecoveryAndReleaseSettlesWithoutOvershoot() {
        setContent()
        openMedia()
        composeRule.mainClock.autoAdvance = false
        val overlay = composeRule.onNodeWithTag("media_viewer_overlay")
        overlay.performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
            up()
        }
        composeRule.mainClock.advanceTimeByFrame()
        val released = viewer.dragY
        assertTrue(released > 0f)
        composeRule.mainClock.advanceTimeBy(48)
        composeRule.runOnIdle { assertTrue(viewer.dragY in 0f..<released) }
        overlay.performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        val held = viewer.dragY
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.runOnIdle { assertEquals("A held drag must stop the previous recovery spring", held, viewer.dragY, 0.01f) }
        overlay.performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()
        var previous = viewer.dragY
        repeat(80) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertTrue("Recovery must not overshoot or move away from rest", viewer.dragY in 0f..previous)
                previous = viewer.dragY
            }
        }
        composeRule.runOnIdle {
            assertEquals(0f, viewer.dragY, 0f)
            assertEquals(2, stack.size)
            assertTrue(viewer.isInteractive)
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun cancellingBackDuringDragRecoveryAlsoRestoresTheDragOffset() {
        setContent()
        openMedia()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("media_viewer_overlay").performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
            up()
        }
        composeRule.mainClock.advanceTimeBy(48)
        composeRule.runOnIdle {
            assertTrue(viewer.dragY > 0f)
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.4f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(viewer.isInteractive)
            assertEquals("Cancelling Back must resume the interrupted drag recovery", 0f, viewer.dragY, 0f)
            assertEquals(1f, viewer.backgroundAlpha, 0f)
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
        composeRule.mainClock.advanceTimeUntil { viewer.isInteractive }
        composeRule.mainClock.advanceTimeByFrame()
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
        composeRule.runOnIdle { assertEquals("The swipe must request dismissal", 1, stack.size) }
        // Advance animation time directly; polling SurfaceView semantics once per frame is slow on emulators.
        composeRule.mainClock.advanceTimeUntil { viewer.progress.value == 0f }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("media_viewer_overlay").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("media_viewer_overlay").assertDoesNotExist()
        assertEquals(1, stack.size)
        // Flush deferred Media3 listener cleanup on the UI thread before test teardown.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
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

    private fun waitForHero() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag("media_hero").fetchSemanticsNodes().isNotEmpty()
        }
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
                                                    Box(Modifier.size(100.dp).clipToBounds()) {
                                                        Box(
                                                            Modifier
                                                                .offset(x = sourceOffset.value.x, y = sourceOffset.value.y)
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
                                                    }
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
