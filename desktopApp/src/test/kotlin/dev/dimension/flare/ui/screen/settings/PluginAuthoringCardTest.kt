package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginAuthoringCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun localPluginUsesTheCommonItemWithSourceAndRevision() {
        val plugin = AuthoredPlugin("local.ai.example", "Same name", phase = PluginAuthoringPhase.Applied, revision = 7)
        var busy by mutableStateOf(false)
        var uninstalls = 0
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginItem(
                    PluginManagementStateV1(authoredPlugins = listOf(plugin)).items.single(),
                    busy = busy,
                    onCancelUpdate = {},
                    onUninstall = { uninstalls++ },
                    onUpdate = {},
                )
            }
        }
        compose.onNodeWithText(plugin.name).assertIsDisplayed()
        compose.onNodeWithContentDescription(plugin.name).assertExists()
        compose.onNodeWithText(plugin.id).assertDoesNotExist()
        compose.onNodeWithText("Local AI-generated plugin · Revision 7").assertIsDisplayed()
        compose.onNodeWithText("Up to date").assertDoesNotExist()
        compose.onNodeWithText("Allow and continue").assertDoesNotExist()
        compose.onNodeWithText("Enabled").assertDoesNotExist()
        compose.onNodeWithText("Uninstall").assertDoesNotExist()
        compose.onNodeWithContentDescription("More: Same name").performClick()
        compose.onNodeWithText("Uninstall").assertIsDisplayed()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Uninstall").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Uninstall").performClick()
        compose.onNodeWithText("Uninstall").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, uninstalls) }
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
                    PluginItem(
                        plugin = PluginManagementStateV1(authoredPlugins = listOf(currentPlugin)).items.single(),
                        busy = false,
                        onCancelUpdate = {
                            stops++
                            currentPlugin = currentPlugin.copy(phase = PluginAuthoringPhase.Idle)
                        },
                        onUninstall = {},
                        onUpdate = {},
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
        compose.onNodeWithText("Local AI-generated plugin · Revision 1").assertIsDisplayed()
        compose.onNodeWithText("Cancel update").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel update").assertDoesNotExist()
        compose.onNodeWithText("Ready to edit").assertDoesNotExist()
        compose.onNodeWithText("The current version is running").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
