package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.management.PluginManagementItemV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import dev.dimension.flare.ui.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginManagementMenuTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var state by mutableStateOf(PluginManagementStateV1())
    private var installs = 0
    private var updateChecks = 0

    @Test
    fun githubActionsAppearInThePopupAndDismissAfterSelection() {
        val repository = GitHubPluginRepositoryV1(1, "example", "plugin")
        state =
            state.copy(
                plugins =
                    listOf(
                        PluginManagementItemV1(
                            pluginId = "example.plugin",
                            platformId = "Example",
                            name = "Example",
                            nameText = UiText.Raw("Example"),
                            version = "1.0.0",
                            enabled = true,
                            running = true,
                            pendingRestart = false,
                            source = PluginInstallSourceV1.GitHub(repository, "v1.0.0", "a".repeat(40)),
                            capabilities = emptySet(),
                            iconPath = "",
                            sourceUrl = repository.url,
                            availableUpdate = null,
                        ),
                    ),
            )
        show()
        compose.onNodeWithText(context.getString(R.string.plugin_install_github)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.plugin_check_updates)).assertDoesNotExist()

        more().performClick()
        popupItem(R.string.plugin_install_github).assertIsDisplayed().performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, installs) }

        more().performClick()
        popupItem(R.string.plugin_check_updates).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, updateChecks) }
    }

    @Test
    fun busyStateAndMissingGithubSourcesDisableTheAppropriateControls() {
        state = state.copy(busy = true)
        show()
        more().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false) }
        more().assertIsEnabled().performClick()
        popupItem(R.string.plugin_install_github).assertIsEnabled()
        popupItem(R.string.plugin_check_updates).assertIsNotEnabled()
    }

    private fun show() {
        compose.setContent {
            MaterialTheme {
                PluginManagementMenu(
                    state = state,
                    onGitHubInstall = { installs++ },
                    onCheckUpdates = { updateChecks++ },
                    onRebuildIndex = {},
                    onCleanup = {},
                )
            }
        }
    }

    private fun more() = compose.onNodeWithContentDescription(context.getString(R.string.more))

    private fun popupItem(label: Int) = compose.onNode(hasText(context.getString(label)) and hasAnyAncestor(isPopup()))
}
