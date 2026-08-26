package dev.dimension.flare.ui.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class FlareComposeHostTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rendersFoundationAndComposeOnlyContent() {
        composeRule.setContent {
            FlareComposeHost(widgetSystem = createAndroidComposeWidgetSystem()) {
                var count by remember { mutableIntStateOf(0) }
                Column {
                    Text(
                        text = "Count $count",
                        modifier = FlareModifier(testTag = "count"),
                    )
                    AndroidCompose {
                        BasicText(
                            text = "Compose only $count",
                            modifier = Modifier.testTag("compose-only"),
                        )
                    }
                    NativeButton(
                        label = "Increment",
                        modifier = FlareModifier(testTag = "increment"),
                        onClick = { count += 1 },
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("count")
            .assertTextEquals("Count 0")
        composeRule
            .onNodeWithTag("compose-only")
            .assertTextEquals("Compose only 0")
        composeRule
            .onNodeWithTag("increment")
            .performClick()
        composeRule
            .onNodeWithTag("count")
            .assertTextEquals("Count 1")
        composeRule
            .onNodeWithTag("compose-only")
            .assertTextEquals("Compose only 1")
    }
}
