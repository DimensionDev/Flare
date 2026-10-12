package dev.dimension.flare.ui.route

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceCornerNavEntryDecoratorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var activity: ComponentActivity
    private val enabled = mutableStateOf(true)
    private val shape = mutableStateOf<Shape>(RoundedCornerShape(40.dp))
    private var pageInstances = 0

    @Test
    fun enteringAndLeavingMultiWindowUpdatesPixelsWithoutRecreatingPage() {
        setPageContent()
        assertCorners(Color.Green)

        composeRule.runOnIdle {
            activity.onMultiWindowModeChanged(true, activity.resources.configuration)
        }
        assertCorners(Color.Red)

        composeRule.runOnIdle {
            activity.onMultiWindowModeChanged(false, activity.resources.configuration)
        }
        assertCorners(Color.Green)
        composeRule.runOnIdle { assertEquals(1, pageInstances) }
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
    }

    @Test
    fun changingLayoutSizeAndShapeKeepsPageState() {
        setPageContent()
        assertCorners(Color.Green)

        composeRule.runOnIdle { enabled.value = false }
        assertCorners(Color.Red)
        composeRule.runOnIdle { enabled.value = true }
        assertCorners(Color.Green)

        composeRule.runOnIdle { shape.value = RectangleShape }
        assertCorners(Color.Red)
        composeRule.runOnIdle { assertEquals(1, pageInstances) }
    }

    @Test
    fun dialogEntriesAreNotClipped() {
        setPageContent(metadata = DialogSceneStrategy.dialog())

        assertCorners(Color.Red)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun bottomSheetEntriesAreNotClipped() {
        setPageContent(metadata = BottomSheetSceneStrategy.bottomSheet())

        assertCorners(Color.Red)
    }

    private fun setPageContent(
        metadata: Map<String, Any> = emptyMap(),
        activityOverride: ComponentActivity? = null,
    ) {
        composeRule.setContent {
            activity = activityOverride ?: LocalActivity.current as ComponentActivity
            CompositionLocalProvider(LocalActivity provides activity) {
                val isInMultiWindowMode = rememberIsInMultiWindowMode()
                val decorator =
                    rememberDeviceCornerNavEntryDecorator(
                        enabled = enabled.value && !isInMultiWindowMode,
                        shape = shape.value,
                    )
                val entry =
                    remember {
                        NavEntry<NavKey>(TestRoute, metadata = metadata) {
                            remember { pageInstances++ }
                            Box(Modifier.fillMaxSize().background(Color.Red))
                        }
                    }
                val decoratedEntries = rememberDecoratedNavEntries(listOf(entry), listOf(decorator))
                Box(Modifier.size(120.dp).background(Color.Green).testTag(CORNER_HOST_TAG)) {
                    decoratedEntries.single().Content()
                }
            }
        }
    }

    private fun assertCorners(expected: Color) {
        val pixels = composeRule.onNodeWithTag(CORNER_HOST_TAG).captureToImage().toPixelMap()
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

    private companion object {
        const val CORNER_HOST_TAG = "corner-host"
    }
}
