package dev.dimension.flare.ui.route

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class DeviceCornerNavEntryDecoratorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var activity: ComponentActivity
    private val enabled = mutableStateOf(true)
    private val shape = mutableStateOf<Shape>(RoundedCornerShape(40.dp))
    private val hostSize = mutableStateOf(120.dp)
    private val backStack = mutableStateListOf<NavKey>(TestRoute)
    private var pageInstances = 0

    @Test
    fun idlePageDoesNotClipInSystemScreenshot() {
        setPageContent()

        assertSystemCorners("initial-idle")
        assertCorners(Color.Red)
    }

    @Test
    fun pushAndButtonBackClipOnlyUntilTheTransitionFinishes() {
        setPageContent()
        assertCorners(Color.Red)
        startTransition()
        assertCorners(Color.Green)
        advance(1_100)
        assertCorners(Color.Red)
        assertSystemCorners("push-idle")

        composeRule.runOnIdle { backStack.removeLastOrNull() }
        advance(64)
        assertCorners(Color.Green)
        advance(1_100)
        assertCorners(Color.Red)
        assertSystemCorners("pop-idle")
    }

    @Test
    fun enteringAndLeavingMultiWindowUpdatesPixelsWithoutRecreatingPage() {
        setPageContent()
        assertCorners(Color.Red)
        startTransition()
        assertCorners(Color.Green)

        composeRule.runOnIdle {
            activity.onMultiWindowModeChanged(true, activity.resources.configuration)
        }
        advance(16)
        assertCorners(Color.Red)

        composeRule.runOnIdle {
            activity.onMultiWindowModeChanged(false, activity.resources.configuration)
        }
        advance(16)
        assertCorners(Color.Green)
        composeRule.runOnIdle { assertEquals(1, pageInstances) }
        advance(1_100)
        assertCorners(Color.Red)
    }

    @Test
    fun launchingInMultiWindowDoesNotClip() {
        lateinit var windowedActivity: ComponentActivity
        composeRule.runOnUiThread {
            windowedActivity =
                object : ComponentActivity() {
                    override fun isInMultiWindowMode() = true
                }
        }

        setPageContent(activityOverride = windowedActivity)

        assertCorners(Color.Red)
        startTransition()
        assertCorners(Color.Red)
        advance(1_100)
        assertCorners(Color.Red)
    }

    @Test
    fun changingLayoutSizeAndShapeKeepsPageState() {
        setPageContent()
        assertCorners(Color.Red)
        startTransition()
        assertCorners(Color.Green)

        composeRule.runOnIdle { hostSize.value = 200.dp }
        advance(16)
        assertCorners(Color.Green)

        composeRule.runOnIdle { enabled.value = false }
        advance(16)
        assertCorners(Color.Red)
        composeRule.runOnIdle { enabled.value = true }
        advance(16)
        assertCorners(Color.Green)

        composeRule.runOnIdle { shape.value = RectangleShape }
        advance(16)
        assertCorners(Color.Red)
        composeRule.runOnIdle { assertEquals(1, pageInstances) }
    }

    @Test
    fun dialogEntriesAreNotClipped() {
        setPageContent(metadata = DialogSceneStrategy.dialog())

        assertCorners(Color.Red, OVERLAY_CONTENT_TAG)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun bottomSheetEntriesAreNotClipped() {
        setPageContent(metadata = BottomSheetSceneStrategy.bottomSheet())

        assertCorners(Color.Red, OVERLAY_CONTENT_TAG)
    }

    private fun setPageContent(
        metadata: Map<String, Any> = emptyMap(),
        activityOverride: ComponentActivity? = null,
    ) {
        composeRule.mainClock.autoAdvance = false
        if (metadata.isNotEmpty()) backStack.add(TestOverlay)
        composeRule.setContent {
            activity = activityOverride ?: LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalActivity provides activity) {
                val isInMultiWindowMode = rememberIsInMultiWindowMode()
                val provider =
                    remember {
                        { route: NavKey ->
                            NavEntry(route, metadata = if (route == TestOverlay) metadata else emptyMap()) {
                                if (route == TestOverlay) {
                                    Box(Modifier.size(120.dp).background(Color.Red).testTag(OVERLAY_CONTENT_TAG))
                                } else {
                                    if (route == TestRoute) remember { pageInstances++ }
                                    Box(Modifier.fillMaxSize().background(Color.Red))
                                }
                            }
                        }
                    }
                Box(Modifier.size(hostSize.value).background(Color.Green).testTag(CORNER_HOST_TAG)) {
                    AndroidNavDisplay(
                        entries = rememberDecoratedNavEntries(backStack, emptyList(), provider),
                        sceneStrategies = remember { listOf(DialogSceneStrategy(), BottomSheetSceneStrategy()) },
                        cornerShape = shape.value.takeIf { enabled.value && !isInMultiWindowMode },
                        // Keep both pages stationary so their corners expose only clipping.
                        transitionSpec = {
                            fadeIn(tween(1_000), initialAlpha = 0.99f) togetherWith
                                fadeOut(tween(1_000), targetAlpha = 0.99f)
                        },
                        popTransitionSpec = {
                            fadeIn(tween(1_000), initialAlpha = 0.99f) togetherWith
                                fadeOut(tween(1_000), targetAlpha = 0.99f)
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
        }
        advance(64)
    }

    private fun startTransition() {
        composeRule.runOnIdle { backStack.add(TestPage) }
        advance(64)
    }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    private fun assertSystemCorners(name: String) {
        val bounds = composeRule.onNodeWithTag(CORNER_HOST_TAG).fetchSemanticsNode().boundsInWindow
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-corners").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            for (x in listOf(bounds.left.roundToInt() + 2, bounds.right.roundToInt() - 3)) {
                for (y in listOf(bounds.top.roundToInt() + 2, bounds.bottom.roundToInt() - 3)) {
                    assertEquals("System screenshot $name at ($x, $y)", Color.Red, Color(bitmap.getPixel(x, y)))
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertCorners(
        expected: Color,
        tag: String = CORNER_HOST_TAG,
    ) {
        val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
        listOf(
            pixels[2, 2],
            pixels[pixels.width - 3, 2],
            pixels[2, pixels.height - 3],
            pixels[pixels.width - 3, pixels.height - 3],
        ).forEach { actual ->
            assertEquals(expected.red, actual.red, 0.01f)
            assertEquals(expected.green, actual.green, 0.01f)
            assertEquals(expected.blue, actual.blue, 0.01f)
        }
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
    }

    private object TestRoute : NavKey

    private object TestPage : NavKey

    private object TestOverlay : NavKey

    private companion object {
        const val CORNER_HOST_TAG = "corner-host"
        const val OVERLAY_CONTENT_TAG = "overlay-content"
    }
}
