package dev.dimension.flare.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlareComposeHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFoundationWidgetsAndRecomposesAfterAnEvent() {
        val widgetSystem = createAndroidComposeWidgetSystem()
        composeRule.setContent {
            FlareComposeHost(widgetSystem = widgetSystem) {
                var count by remember { mutableIntStateOf(0) }
                Column {
                    Text(
                        text = "Count $count",
                        modifier = FlareModifier.testTag("count"),
                    )
                    NativeButton(
                        label = "Increment",
                        modifier = FlareModifier.testTag("increment"),
                        onClick = { count += 1 },
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("count")
            .assertTextEquals("Count 0")
        composeRule
            .onNodeWithTag("increment")
            .performClick()
        composeRule
            .onNodeWithTag("count")
            .assertTextEquals("Count 1")
    }
}
