package dev.dimension.compose.nativekit.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.foundation.Column
import dev.dimension.compose.nativekit.foundation.HorizontalAlignment
import dev.dimension.compose.nativekit.foundation.NativeButton
import dev.dimension.compose.nativekit.foundation.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class NativeKitComposeHostTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rendersFoundationAndComposeOnlyContent() {
        composeRule.setContent {
            MaterialTheme {
                NativeKitComposeHost(widgetSystem = createAndroidComposeWidgetSystem()) {
                    var count by remember { mutableIntStateOf(0) }
                    Column(
                        spacing = 12f,
                        horizontalAlignment = HorizontalAlignment.End,
                    ) {
                        Text(
                            text = "Count $count",
                            modifier = NativeKitModifier(testTag = "count"),
                        )
                        AndroidCompose {
                            BasicText(
                                text = "Compose only $count",
                                modifier = Modifier.testTag("compose-only"),
                            )
                        }
                        NativeButton(
                            label = "Increment",
                            modifier = NativeKitModifier(testTag = "increment"),
                            onClick = { count += 1 },
                        )
                    }
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
            .assertHeightIsAtLeast(40.dp)

        val countBounds = composeRule.onNodeWithTag("count").getUnclippedBoundsInRoot()
        val composeBounds = composeRule.onNodeWithTag("compose-only").getUnclippedBoundsInRoot()
        assertTrue(countBounds.left > composeBounds.left)
        assertEquals(12f, (composeBounds.top - countBounds.bottom).value, 0.1f)

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
