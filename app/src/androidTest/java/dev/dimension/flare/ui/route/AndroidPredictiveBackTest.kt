package dev.dimension.flare.ui.route

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AndroidPredictiveBackTest {
    private val motionDurationScale =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }

    @get:Rule
    val compose = createComposeRule(effectContext = motionDurationScale)
    private val stack = mutableStateListOf(0, 1)
    private val input = DirectNavigationEventInput()
    private var eventTime = 1_000L
    private var edge = NavigationEvent.EDGE_LEFT
    private var fallbacks = 0
    private var threePages = false
    private val creations = IntArray(3)
    private val cornerShape = mutableStateOf<Shape?>(RoundedCornerShape(24.dp))

    @Test
    fun ordinaryPushAndButtonBackClipOnlyDuringAnimation() {
        stack.removeLast()
        show()
        assertEquals(Color.Red, frame().corner)
        compose.runOnIdle { stack.add(1) }
        advance(180)
        assertEquals(0f, frame("push-active").corner.blue, 0.01f)
        advance(800)
        assertEquals(Color.Blue, frame("push-idle").corner)

        compose.runOnIdle { stack.removeLast() }
        advance(180)
        assertEquals(0f, frame("pop-active").corner.red, 0.01f)
        assertRoot()
    }

    @Test
    fun disabledAnimationsDoNotLeaveClippingAfterNavigationOrCancellation() {
        motionDurationScale.scaleFactor = 0f
        show()
        compose.runOnIdle { stack.removeLast() }
        advance(64)
        assertEquals(Color.Red, frame().corner)
        compose.runOnIdle { stack.add(1) }
        advance(64)
        assertEquals(Color.Blue, frame().corner)

        start()
        progress(0.6f, 360f, 32)
        compose.runOnIdle { input.backCancelled() }
        advance(64)
        assertEquals(Color.Blue, frame().corner)

        start()
        progress(0.6f, 360f, 32)
        compose.runOnIdle { input.backCompleted() }
        assertRoot()
    }

    @Test
    fun disabledCornersStaySquareDuringPreviewAndRebound() {
        cornerShape.value = null
        show()
        start()
        progress(0.6f, 360f, 96)
        assertEquals(Color.Blue, frame().blueCorner)
        compose.runOnIdle { input.backCancelled() }
        val frames = trace("unclipped-cancel", 40)
        assertTrue("Every rebound frame keeps square corners", frames.all { it.blueCorner == Color.Blue })
        assertEquals(Color.Blue, frame().corner)
    }

    @Test
    fun slowDragFromLeftKeepsExitingPageOpaqueAndTracksVerticalMotion() = slowDrag(NavigationEvent.EDGE_LEFT)

    @Test
    fun slowDragFromRightKeepsExitingPageOpaqueAndTracksVerticalMotion() = slowDrag(NavigationEvent.EDGE_RIGHT)

    private fun slowDrag(swipeEdge: Int) {
        show()
        start(swipeEdge)
        for (p in listOf(0.1f, 0.3f, 0.6f)) progress(p, 360f, 96)
        advance(160)
        val down = frame("slow-$swipeEdge-preview-down")
        assertEquals("Preview keeps rounded corners", 0f, down.blueCorner.blue, 0.01f)
        assertEquals("Preview must remain opaque", 1f, down.center.blue, 0.01f)
        assertEquals("No previous-page bleed-through", 0f, down.center.red, 0.01f)
        val eased = androidPredictiveBackEasing.transform(0.6f)
        val scale = 1f - 0.15f * eased
        assertNear("Exiting preview height", 480f * scale, down.blue.height.toFloat())
        assertNear("Entering page shrinks with the exiting page", 480f * 0.95f * scale, down.red.height.toFloat())
        val y = (480f * (1f - scale) / 2f - 8f) * 0.75f
        assertNear("Downward finger tracking", 240f + y, down.blue.centerY)
        assertTrue("Keep the native 8px display margin", down.blue.bottom <= 472)
        progress(0.6f, 120f, 96)
        advance(160)
        val up = frame("slow-$swipeEdge-preview-up")
        assertNear("Upward finger tracking", 240f - y, up.blue.centerY)
        assertTrue("Keep the native 8px display margin", up.blue.top >= 8)
        compose.runOnIdle { input.backCompleted() }
        val released = trace("slow-$swipeEdge", 35)
        assertTrue("Outgoing page survives the first release frame", released.first().blue.height > 0)
        assertRoot()
    }

    @Test
    fun fastFlingHasMorePostCommitShrinkThanSlowReleaseAtTheSameProgress() {
        show()

        fun release(interval: Long): List<Frame> {
            start()
            for (p in listOf(0.03f, 0.08f, 0.18f, 0.35f)) progress(p, 280f, interval)
            compose.runOnIdle { input.backCompleted() }
            return trace("release-${interval}ms", 8)
        }
        val slow = release(112)
        advance(800)
        compose.runOnIdle { stack.add(1) }
        advance(800)
        val fast = release(16)
        assertTrue(
            "Fling must carry velocity into the actual rendered frames: slow=${slow.map {
                it.red.height
            }}, fast=${fast.map { it.red.height }}",
            slow.zip(fast).any { (a, b) -> a.red.height - b.red.height >= 2 },
        )
        advance(800)
        assertRoot()
    }

    @Test
    fun cancellationReturnsToTheSameOpaquePageWithoutPopping() {
        show()
        start()
        for (p in listOf(0.1f, 0.3f, 0.65f)) progress(p, 360f, 64)
        compose.runOnIdle { input.backCancelled() }
        val frames = trace("cancel", 40)
        assertTrue("Cancellation must never fade the current page", frames.all { it.center.blue > 0.99f })
        assertEquals(listOf(0, 1), stack.toList())
        assertEquals("Cancellation retains entry state", 1, creations[1])
        assertNear(
            "Cancelled width",
            320f,
            frames
                .last()
                .blue.width
                .toFloat(),
        )
        assertNear("Cancelled Y", 240f, frames.last().blue.centerY)
        assertEquals("Rebound keeps rounded corners", 0f, frames.first().blueCorner.blue, 0.01f)
        assertEquals("Idle after cancellation fills the corner", Color.Blue, frame("cancel-idle").corner)
    }

    @Test
    fun secondBackCanStartBeforeTheFirstCommitFinishes() {
        stack.add(2)
        threePages = true
        show()
        start()
        for (p in listOf(0.05f, 0.15f, 0.4f)) progress(p, 320f, 16)
        compose.runOnIdle { input.backCompleted() }
        advance(48)
        assertEquals(listOf(0, 1), stack.toList())
        val before = frame()
        start()
        val takenOver = frame()
        assertTrue("Taking over must not reset the entering page's geometry", abs(takenOver.green.height - before.green.height) < 12)
        for (p in listOf(0.05f, 0.2f, 0.5f)) progress(p, 200f, 16)
        compose.runOnIdle { input.backCompleted() }
        val frames = trace("consecutive", 40)
        assertTrue("At least one page must remain rendered during interruption", frames.all { it.coloredPixels > 320 * 480 / 2 })
        assertRoot()
    }

    @Test
    fun aNewGestureCanTakeOverCancellationWithoutResettingVerticalPosition() {
        show()
        start()
        for (p in listOf(0.1f, 0.3f, 0.6f)) progress(p, 360f, 32)
        compose.runOnIdle { input.backCancelled() }
        advance(32)
        val before = frame()
        start(y = 360f)
        val after = frame()
        assertTrue("New gesture must retain the current Y", abs(after.blue.centerY - before.blue.centerY) < 8f)
        progress(0.2f, 380f, 32)
        compose.runOnIdle { input.backCancelled() }
        trace("cancel-takeover", 40)
        assertEquals(listOf(0, 1), stack.toList())
        assertNear("Second cancellation restores the page", 480f, frame().blue.height.toFloat())
        assertEquals(Color.Blue, frame().corner)
    }

    @Test
    fun rootBackRemainsAvailableToTheSystem() {
        stack.removeLast()
        show()
        compose.runOnIdle { input.backCompleted() }
        assertEquals(1, fallbacks)
        assertEquals(listOf(0), stack.toList())
    }

    @Test
    fun deviceCornerUpdatesDoNotRecreateTheCurrentEntry() {
        show()
        assertEquals(Color.Blue, frame().corner)
        start()
        progress(0.6f, 240f, 96)
        assertEquals(0f, frame().blueCorner.blue, 0.01f)
        compose.runOnIdle { cornerShape.value = null }
        advance(16)
        assertEquals(1f, frame().blueCorner.blue, 0.01f)
        compose.runOnIdle { cornerShape.value = RoundedCornerShape(24.dp) }
        advance(16)
        assertEquals(0f, frame().blueCorner.blue, 0.01f)
        compose.runOnIdle { input.backCancelled() }
        advance(800)
        assertEquals(Color.Blue, frame().corner)
        assertEquals(1, creations[1])
    }

    @Test
    fun listDetailBackKeepsTheScaffoldUnscaledDuringPreviewCancelAndCommit() = sameSceneBack(listDetail = true)

    @Test
    fun sameKeySceneBackSkipsCrossSceneMotionDuringPreviewCancelAndCommit() = sameSceneBack(listDetail = false)

    private fun sameSceneBack(listDetail: Boolean) {
        val name = if (listDetail) "list-detail" else "same-key"
        show(listDetail = listDetail, sameKey = !listDetail)
        assertNear("Detail starts at full height", 480f, frame().blue.height.toFloat())
        start()
        assertNear("Same-scene preview must not apply the entering scale", 480f, frame("$name-start").blue.height.toFloat())
        progress(0.6f, 360f, 96)
        assertNear("Pane preview must not scale the scaffold", 480f, frame("$name-preview").blue.height.toFloat())
        val previewCorner = frame().corner
        assertEquals("Same-scene preview keeps the panes filling the corner", 1f, previewCorner.red + previewCorner.blue, 0.01f)
        compose.runOnIdle { input.backCancelled() }
        trace("$name-cancel", 40)
        assertEquals(listOf(0, 1), stack.toList())
        assertNear("Cancellation restores the detail pane", 480f, frame().blue.height.toFloat())
        assertEquals(1, creations[1])
        start()
        progress(0.6f, 120f, 96)
        compose.runOnIdle { input.backCompleted() }
        trace("$name-commit", 40)
        assertRoot()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    private fun show(
        listDetail: Boolean = false,
        sameKey: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher =
                    NavigationEventDispatcher(
                        onBackCompletedFallback = { fallbacks++ },
                    ).apply { addInput(input) }
            }
        compose.setContent {
            DisposableEffect(owner) { onDispose { owner.navigationEventDispatcher.dispose() } }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, LocalDensity provides Density(1f)) {
                val provider =
                    remember {
                        { route: Int ->
                            val metadata =
                                if (!listDetail) {
                                    emptyMap()
                                } else if (route == 0) {
                                    ListDetailSceneStrategy.listPane(sceneKey = "Lists")
                                } else {
                                    ListDetailSceneStrategy.detailPane(sceneKey = "Lists")
                                }
                            NavEntry(route, metadata = metadata) {
                                val token = rememberSaveable { ++creations[route] }
                                val color =
                                    when (route) {
                                        0 -> Color.Red
                                        1 -> if (threePages) Color.Green else Color.Blue
                                        else -> Color.Blue
                                    }
                                Box(Modifier.fillMaxSize().background(color).testTag("page-$route-$token"))
                            }
                        }
                    }
                Box(Modifier.requiredSize(320.dp, 480.dp).background(Color.Black).testTag("window")) {
                    AndroidNavDisplay(
                        entries = rememberDecoratedNavEntries(stack, listOf(rememberSaveableStateHolderNavEntryDecorator()), provider),
                        sceneStrategies =
                            if (listDetail) {
                                listOf(
                                    rememberListDetailSceneStrategy(
                                        shouldHandleSinglePaneLayout = true,
                                        directive = PaneScaffoldDirective.Default,
                                    ),
                                )
                            } else if (sameKey) {
                                remember {
                                    listOf(
                                        SceneStrategy<Int> { entries ->
                                            SameKeyScene(with(SinglePaneSceneStrategy<Int>()) { calculateScene(entries) })
                                        },
                                    )
                                }
                            } else {
                                remember { listOf(SinglePaneSceneStrategy()) }
                            },
                        cornerShape = cornerShape.value,
                        transitionSpec = { androidOpenTransition(96) },
                        popTransitionSpec = { androidCloseTransition(96) },
                        onBack = { stack.removeLastOrNull() },
                    )
                }
            }
        }
        advance(800)
    }

    private fun start(
        swipeEdge: Int = NavigationEvent.EDGE_LEFT,
        y: Float = 240f,
    ) {
        edge = swipeEdge
        eventTime += 16
        compose.runOnIdle { input.backStarted(NavigationEvent(swipeEdge = edge, touchY = y, frameTimeMillis = eventTime)) }
        advance(16)
    }

    private fun progress(
        p: Float,
        y: Float,
        millis: Long,
    ) {
        eventTime += millis
        compose.runOnIdle {
            input.backProgressed(NavigationEvent(swipeEdge = edge, progress = p, touchX = p * 320, touchY = y, frameTimeMillis = eventTime))
        }
        advance(millis)
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun trace(
        name: String,
        count: Int,
    ): List<Frame> =
        List(count) {
            advance(16)
            frame(if (it in listOf(0, 2, 4, 8, 16, 34)) "$name-frame-$it" else null)
        }.also { frames -> println("BACK_FRAMES $name red=${frames.map { it.red.height }} blue=${frames.map { it.blue.height }}") }

    private fun assertRoot() {
        advance(800)
        assertEquals(listOf(0), stack.toList())
        assertEquals("Previous entry retains its saved state", 1, creations[0])
        val root = frame()
        assertNear("Previous page restored to full height", 480f, root.red.height.toFloat())
        assertEquals(1f, root.center.red, 0.01f)
        assertEquals("Idle root fills the corner", Color.Red, root.corner)
    }

    private fun assertNear(
        message: String,
        expected: Float,
        actual: Float,
    ) = assertEquals(message, expected, actual, 3f)

    private fun frame(name: String? = null): Frame {
        val image = compose.onNodeWithTag("window").captureToImage()
        if (name != null) {
            val directory =
                File(
                    InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                    "predictive-back",
                ).apply {
                    mkdirs()
                }
            File(directory, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val pixels = image.toPixelMap()
        val red = Bounds()
        val blue = Bounds()
        val green = Bounds()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                val bounds =
                    when {
                        c.red > 0.2f && c.red > c.blue * 2 && c.red > c.green * 2 -> red
                        c.blue > 0.2f && c.blue > c.red * 2 && c.blue > c.green * 2 -> blue
                        c.green > 0.2f && c.green > c.red * 2 && c.green > c.blue * 2 -> green
                        else -> null
                    }
                if (bounds != null) {
                    bounds.add(x, y)
                    count++
                }
            }
        }
        val blueCorner = pixels[(blue.left + 2).coerceAtMost(319), (blue.top + 2).coerceAtMost(479)]
        return Frame(pixels[160, 240], pixels[1, 1], blueCorner, red, blue, green, count)
    }

    private data class Frame(
        val center: Color,
        val corner: Color,
        val blueCorner: Color,
        val red: Bounds,
        val blue: Bounds,
        val green: Bounds,
        val coloredPixels: Int,
    )

    private data class SameKeyScene(
        val scene: Scene<Int>,
    ) : Scene<Int> by scene {
        override val key: Any = "Lists"
    }

    private class Bounds {
        var left = 320
        var top = 480
        var right = 0
        var bottom = 0
        val width get() = (right - left).coerceAtLeast(0)
        val height get() = (bottom - top).coerceAtLeast(0)
        val centerY get() = (top + bottom) / 2f

        fun add(
            x: Int,
            y: Int,
        ) {
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x + 1)
            bottom = maxOf(bottom, y + 1)
        }
    }
}
