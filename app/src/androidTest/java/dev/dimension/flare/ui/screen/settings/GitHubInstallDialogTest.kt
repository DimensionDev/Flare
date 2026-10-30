package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.model.PluginPermissionType
import dev.dimension.flare.feature.plugin.management.PluginGitHubInstallStateV1
import dev.dimension.flare.feature.plugin.management.PluginInstallReviewV1
import dev.dimension.flare.feature.plugin.management.PluginManagementStateV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubInstallDialogTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PluginManagementStateV1(gitHubInstall = PluginGitHubInstallStateV1()))
    private var visible by mutableStateOf(true)
    private val requests = mutableListOf<String>()
    private var confirmations = 0
    private var cancellations = 0
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val install = context.getString(R.string.plugin_confirm_install)
    private val cancel = context.getString(android.R.string.cancel)
    private val mediaPermission = "• " + context.getString(R.string.plugin_permission_media_network)
    private val url = "https://github.com/example/plugin"
    private val review =
        PluginInstallReviewV1(
            pluginId = "example.plugin",
            platformId = "Example",
            name = "Example",
            version = "1.2.0",
            capabilities = emptyList(),
            warnings = emptyList(),
            sourceUrl = url,
            permissions = listOf(PluginPermission(PluginPermissionType.MediaNetwork, "*")),
        )

    @Test
    fun downloadRetryAndPermissionConfirmationStayInTheSameDialog() {
        show()
        compose.onNodeWithText("Load versions").assertDoesNotExist()
        compose.onNodeWithText(install).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput(url)
        compose.onNodeWithText(install).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(url), requests)
            assertEquals(0, confirmations)
            state = state.copy(gitHubInstall = PluginGitHubInstallStateV1(busy = true))
        }
        compose.onNodeWithText(url).assertIsNotEnabled()
        compose.onNodeWithText(install).assertIsNotEnabled()
        compose.onNodeWithText(cancel).assertIsNotEnabled()
        val progress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        val bounds = compose.onNode(progress and hasAnyAncestor(isDialog())).fetchSemanticsNode().boundsInRoot
        assertTrue("Loading uses a horizontal progress bar", bounds.width > bounds.height * 5)
        compose.onNode(progress and !hasAnyAncestor(isDialog())).assertDoesNotExist()

        compose.runOnIdle { state = state.copy(gitHubInstall = PluginGitHubInstallStateV1(error = "Download failed")) }
        compose.onNodeWithText("Download failed").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains(url)
        compose.onNodeWithText(install).performClick()
        compose.runOnIdle {
            assertEquals(listOf(url, url), requests)
            state = state.copy(gitHubInstall = PluginGitHubInstallStateV1(), pendingInstall = review)
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Example 1.2.0").assertIsDisplayed()
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.onNodeWithText(mediaPermission).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, confirmations) }
        compose.onNodeWithText(install).performClick()
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(2, requests.size)
            state = state.copy(gitHubInstall = PluginGitHubInstallStateV1(error = "Install failed"))
        }
        compose.onNodeWithText("Install failed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(install).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, confirmations)
            assertEquals(0, cancellations)
            visible = false
        }
        compose.onNodeWithText(install).assertDoesNotExist()
    }

    @Test
    fun cancellingPermissionReviewDoesNotConfirmOrDownloadAgain() {
        state = state.copy(pendingInstall = review)
        show()
        compose.onNodeWithText(cancel).performClick()
        compose.runOnIdle {
            assertEquals(1, cancellations)
            assertEquals(0, confirmations)
            assertEquals(emptyList<String>(), requests)
        }
        compose.onNodeWithText("Example 1.2.0").assertDoesNotExist()
    }

    @Test
    fun installationErrorsOnlyAppearInsideTheDialogAndDisappearOnDismiss() {
        show()
        compose.runOnIdle { state = state.copy(gitHubInstall = PluginGitHubInstallStateV1(error = "GitHub rate limited")) }
        compose.onAllNodesWithText("GitHub rate limited").assertCountEquals(1)
        compose.onNodeWithText("GitHub rate limited").assertIsDisplayed()
        compose.onNodeWithText(cancel).performClick()
        compose.onNodeWithText("GitHub rate limited").assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, state.error) }
    }

    private fun show() {
        compose.setContent {
            MaterialTheme {
                PluginManagementList(
                    state = state,
                    onCancelPluginUpdate = {},
                    onUninstall = {},
                    onUpdate = {},
                    onRetryRuntime = {},
                )
                if (visible) {
                    GitHubInstallDialog(
                        state = state,
                        onInstall = { requests += it },
                        onConfirm = { confirmations++ },
                        onUrlChanged = { state = state.copy(gitHubInstall = state.gitHubInstall?.copy(error = null)) },
                        onDismiss = {
                            cancellations++
                            state = state.copy(gitHubInstall = null)
                            visible = false
                        },
                    )
                }
            }
        }
    }
}
