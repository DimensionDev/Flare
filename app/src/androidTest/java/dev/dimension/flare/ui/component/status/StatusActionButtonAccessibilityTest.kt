package dev.dimension.flare.ui.component.status

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Heart
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiNumber
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusActionButtonAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionWithCountIsExposedAsOneLabeledButton() {
        var clickCount = 0

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        TimelineAppearance(
                            showNumbers = true,
                            postActionFixedWidth = true,
                        ),
                ) {
                    StatusActionButton(
                        icon = FontAwesomeIcons.Solid.Heart,
                        number = UiNumber(12),
                        onClicked = { clickCount += 1 },
                        contentDescription = "Like",
                        withTextMinWidth = true,
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Like, 12")
            .assertHasClickAction()
            .performClick()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithText("12").assertDoesNotExist()
        composeRule.onNodeWithText("0000").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }
}
