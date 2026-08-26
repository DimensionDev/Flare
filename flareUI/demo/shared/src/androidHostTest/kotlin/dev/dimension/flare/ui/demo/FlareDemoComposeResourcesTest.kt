package dev.dimension.flare.ui.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.resources.moko.AndroidComposeMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.AndroidMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
public class FlareDemoComposeResourcesTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rendersAndUpdatesGeneratedResources() {
        val widgetSystem =
            createAndroidComposeWidgetSystem(AndroidComposeMokoResourcesRendererPlugin)
        composeRule.setContent {
            val context = LocalContext.current
            val resolver = remember(context) { AndroidMokoResourceResolver(context) }
            MaterialTheme {
                FlareComposeHost(widgetSystem = widgetSystem) {
                    ProvideMokoResources(resolver) {
                        FlareDemoContent()
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("demo-image")
            .assertContentDescriptionEquals("Flare resource image")
        composeRule
            .onNodeWithTag("demo-count")
            .assertTextEquals("Count: 0")
        composeRule
            .onNodeWithTag("demo-updates")
            .assertTextEquals("0 updates")
        composeRule
            .onNodeWithTag("demo-increment")
            .assertHeightIsAtLeast(40.dp)

        val incrementBounds = composeRule.onNodeWithTag("demo-increment").getUnclippedBoundsInRoot()
        val resetBounds = composeRule.onNodeWithTag("demo-reset").getUnclippedBoundsInRoot()
        assertEquals(12f, (resetBounds.left - incrementBounds.right).value, 0.1f)

        composeRule
            .onNodeWithTag("demo-increment")
            .performClick()

        composeRule
            .onNodeWithTag("demo-count")
            .assertTextEquals("Count: 1")
        composeRule
            .onNodeWithTag("demo-updates")
            .assertTextEquals("1 update")
    }
}
