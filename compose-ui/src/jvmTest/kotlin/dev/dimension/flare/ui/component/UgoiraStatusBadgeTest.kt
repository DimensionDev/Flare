package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.ugoira_loading
import dev.dimension.flare.compose.ui.ugoira_play
import io.github.composefluent.FluentTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UgoiraStatusBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preparationStaysInTheBadgeUntilTheFirstFrameAndRetryDoesNotOpenTheViewer() {
        var active by mutableStateOf(false)
        var hasFrame by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var progress by mutableFloatStateOf(0f)
        var retries = 0
        var viewerOpens = 0
        var playDescription = ""
        var loadingDescription = ""
        composeRule.setContent {
            FluentTheme {
                playDescription = stringResource(Res.string.ugoira_play)
                loadingDescription = stringResource(Res.string.ugoira_loading)
                Box(Modifier.size(300.dp).testTag("media").clickable { viewerOpens++ }) {
                    UgoiraStatusBadge(
                        active,
                        hasFrame,
                        failed,
                        progress,
                        onRetry = {
                            retries++
                            failed = false
                            hasFrame = false
                            progress = 0f
                        },
                        modifier = Modifier.align(Alignment.BottomStart).testTag("badge"),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(playDescription, useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { active = true }
        composeRule.onNodeWithContentDescription(loadingDescription, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("0%").assertDoesNotExist()
        composeRule.runOnIdle { progress = 0.42f }
        val spinner = composeRule.onNodeWithContentDescription(loadingDescription, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val percentage =
            composeRule
                .onNodeWithText("42%", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(percentage.left >= spinner.right, "Spinner: $spinner, percentage: $percentage")
        composeRule.runOnIdle { progress = 1f }
        composeRule.onNodeWithText("100%").assertIsDisplayed()
        composeRule.runOnIdle { hasFrame = true }
        composeRule.onNodeWithTag("badge").assertDoesNotExist()
        // A later decode failure must still offer retry, even after displaying a frame.
        composeRule.runOnIdle { failed = true }
        composeRule.onNodeWithTag("badge").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, viewerOpens)
        }
        composeRule.onNodeWithContentDescription(loadingDescription, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("media").performTouchInput { click(Offset(150f, 60f)) }
        composeRule.runOnIdle { assertEquals(1, viewerOpens) }
    }
}
