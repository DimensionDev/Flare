package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginAuthoringCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun localPluginShowsIdentityWithoutApprovalOrEnableControls() {
        val plugin = AuthoredPlugin("local.ai.example", "Same name", phase = PluginAuthoringPhase.Applied, revision = 7)
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginAuthoringCard(plugin, busy = false, onCancelUpdate = {}, onUninstall = {})
            }
        }
        compose.onNodeWithText(plugin.id).assertIsDisplayed()
        compose.onNodeWithText("Revision 7").assertIsDisplayed()
        compose.onNodeWithText("Allow and continue").assertDoesNotExist()
        compose.onNodeWithText("Enabled").assertDoesNotExist()
    }

    @Test
    fun revalidationCanBeStoppedWithoutAnAgentRun() {
        var currentPlugin by mutableStateOf(
            AuthoredPlugin("test.plugin", "Test plugin", phase = PluginAuthoringPhase.Validating, revision = 1, running = true),
        )
        var stops = 0
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                Box(Modifier.width(300.dp)) {
                    PluginAuthoringCard(
                        plugin = currentPlugin,
                        busy = false,
                        onCancelUpdate = {
                            stops++
                            currentPlugin = currentPlugin.copy(phase = PluginAuthoringPhase.Idle)
                        },
                        onUninstall = {},
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(
                "Cancel update",
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("Cancel update").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel update").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
