package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginAuthoringCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun approvalShowsStableIdentityAndTheExactRevision() {
        val plugin = AuthoredPlugin("local.ai.example", "Same name", phase = PluginAuthoringPhase.AwaitingApproval, revision = 7)
        var approvals = 0
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginAuthoringCard(plugin, busy = false, onApprove = { approvals++ }, onCancelUpdate = {}, onSetEnabled = {})
            }
        }
        compose.onNodeWithText(plugin.id).assertIsDisplayed()
        compose.onNodeWithText("Revision 7").assertIsDisplayed()
        compose.onNodeWithText("Allow and continue").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, approvals) }
    }

    @Test
    fun revalidationCanBeStoppedWithoutAnAgentRun() {
        var currentPlugin by mutableStateOf(
            AuthoredPlugin("test.plugin", "Test plugin", phase = PluginAuthoringPhase.Validating, revision = 1, running = true),
        )
        var stops = 0
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginAuthoringCard(
                    plugin = currentPlugin,
                    busy = false,
                    onApprove = {},
                    onCancelUpdate = {
                        stops++
                        currentPlugin = currentPlugin.copy(phase = PluginAuthoringPhase.Idle)
                    },
                    onSetEnabled = {},
                )
            }
        }

        compose.onNodeWithText("Cancel update").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel update").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
