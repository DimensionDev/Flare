package dev.dimension.flare.ui.plugin.badge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposeBadgePluginTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installsExternalComposePrimitiveAndRecomposesAfterAnEvent() {
        val widgetSystem =
            createAndroidComposeWidgetSystem(
                AndroidComposeBadgeRendererPlugin,
            )

        composeRule.setContent {
            FlareComposeHost(widgetSystem = widgetSystem) {
                var count by remember { mutableIntStateOf(0) }
                Badge(
                    text = "Badge $count",
                    modifier = FlareModifier.testTag("badge"),
                    tone = BadgeTone.Positive,
                    onClick = { count += 1 },
                )
            }
        }

        composeRule
            .onNodeWithTag("badge")
            .assertTextEquals("Badge 0")
            .performClick()
        composeRule
            .onNodeWithTag("badge")
            .assertTextEquals("Badge 1")
    }
}
