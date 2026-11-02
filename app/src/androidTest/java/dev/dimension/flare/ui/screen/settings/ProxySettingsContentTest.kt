package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.settings.ProxyEditorState
import dev.dimension.flare.ui.presenter.settings.ProxySettingsState
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class ProxySettingsContentTest {
    @get:Rule val compose = createComposeRule()
    private val mediaQueryIntegration = ComposeUiFlags.isMediaQueryIntegrationEnabled

    @Before fun setUp() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    }

    @After fun tearDown() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = mediaQueryIntegration
    }

    private val profile = ProxyProfile("office", "Office", ProxyProtocol.HTTPS, "proxy.example", 443)
    private val account = MicroBlogKey("alice", "example.social")
    private var permissionRequests = 0
    private var pendingPermission: (() -> Unit)? = null

    @Test
    fun policyRowsAreHighlightedOnlyWhileTheirDropdownIsOpen() {
        val state = State()
        show(state)
        val globalRow = policyRow("Global")
        globalRow.assertIsOff().performClick()
        globalRow.assertIsOn()
        popupOption("Direct connection").performClick()
        globalRow.assertIsOff()

        val accountRow = policyRow(account.toString())
        accountRow.performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("Proxy: ${state.accountLabel(state.accounts.first())}").performClick()
        accountRow.assertIsOn()
        pressBack()
        accountRow.assertIsOff()
    }

    @Test
    fun selectingProxyWaitsForPermissionAndDirectDoesNot() {
        val state = State()
        show(state)
        compose.onNodeWithContentDescription("Global").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        popupOption("Follow global").assertDoesNotExist()
        popupOption("Office").performClick()
        compose.runOnIdle {
            assertEquals(1, permissionRequests)
            assertNull(state.selection)
            pendingPermission?.invoke()
            assertEquals(null to ProxyPolicy(ProxyMode.PROXY, profile.id), state.selection)
        }
        compose.onNodeWithContentDescription("Global").performClick()
        popupOption("Direct connection").performClick()
        compose.runOnIdle {
            assertEquals(1, permissionRequests)
            assertEquals(null to ProxyPolicy(ProxyMode.DIRECT), state.selection)
        }
        compose.onNodeWithContentDescription("Global").performClick()
        popupOption("System settings").performClick()
        compose.runOnIdle {
            assertEquals(1, permissionRequests)
            assertEquals(null to ProxyPolicy(ProxyMode.SYSTEM), state.selection)
            state.operation = UiState.Loading()
        }
        compose.onNodeWithContentDescription("Global").assertIsNotEnabled()
    }

    @Test
    fun accountInheritanceUsesEffectiveProxyAndKeepsAccountScope() {
        val state = State(ProxySettings(profiles = listOf(profile), global = ProxyPolicy(ProxyMode.PROXY, profile.id)))
        show(state)
        compose.onNodeWithText(account.toString()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Proxy: ${state.accountLabel(state.accounts.first())}").performClick()
        popupOption("Follow global").performClick()
        compose.runOnIdle {
            assertEquals(1, permissionRequests)
            assertNull(state.selection)
            pendingPermission?.invoke()
            assertEquals(account to ProxyPolicy(), state.selection)
        }
        compose.onNodeWithText("bob@nostr").assertDoesNotExist()
    }

    @Test
    fun profileMenuKeepsInUseDeletionDisabled() {
        val state = State(ProxySettings(profiles = listOf(profile), global = ProxyPolicy(ProxyMode.PROXY, profile.id)))
        show(state)
        compose.onNodeWithText("Edit proxy").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithContentDescription("More: Office").performClick()
        popupOption("Edit proxy").assertIsEnabled()
        popupOption("Delete").assertIsNotEnabled()
        popupOption("Test connection").assertDoesNotExist()
        compose.onNodeWithText("Reassign global and account settings before deleting this proxy.").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(state.removed)
            state.settings = UiState.Success(state.settings.takeSuccess()!!.copy(global = ProxyPolicy(ProxyMode.DIRECT)))
        }
        popupOption("Delete").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(profile.id, state.removed) }
        popupOption("Edit proxy").assertDoesNotExist()
    }

    @Test
    fun connectionTestStateOnlyUpdatesTheEditor() {
        var pageReads = 0
        val page =
            object : ProxySettingsState by State() {
                override val settings: UiState<ProxySettings>
                    get() {
                        pageReads++
                        return UiState.Success(ProxySettings(profiles = listOf(profile)))
                    }
            }
        val editor = EditorState()
        show(page, editor)
        compose.onNodeWithContentDescription("More: Office").performClick()
        popupOption("Edit proxy").performClick()
        var initialPageReads = 0
        compose.runOnIdle {
            initialPageReads = pageReads
            editor.testState = UiState.Loading()
        }
        compose.onNodeWithContentDescription("Global").assertIsEnabled()
        val progress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        compose.onNode(progress and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(progress and !hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.runOnIdle {
            editor.testState = UiState.Error(IllegalStateException())
        }
        option("Proxy operation failed. Please try again.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            editor.testState = UiState.Success(profile)
        }
        compose.onAllNodesWithText("Connection succeeded").assertCountEquals(1)
        compose.runOnIdle { assertEquals(initialPageReads, pageReads) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Connection succeeded").assertDoesNotExist()
        compose.onNode(progress).assertDoesNotExist()
    }

    @Test
    fun editorKeepsDraftDuringFailureAndCancelDiscardsIt() {
        val editor = EditorState()
        show(State(), editor)
        compose.onNodeWithContentDescription("More: Office").performClick()
        popupOption("Edit proxy").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Work")
        compose.runOnIdle { editor.saveState = UiState.Loading() }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle {
            editor.saveState = UiState.Error(IllegalStateException())
        }
        compose.onAllNodesWithText("Proxy operation failed. Please try again.").assertCountEquals(1)
        option("Proxy operation failed. Please try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Name").performScrollTo().assertTextContains("Work")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(editor.saved) }
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
        compose.onNodeWithContentDescription("More: Office").performClick()
        popupOption("Edit proxy").performClick()
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Name").assertTextContains("Office")
    }

    @Test
    fun editorSavesCurrentFieldsAndClosesOnSuccess() {
        val editor = EditorState()
        show(State(), editor)
        compose.onNodeWithContentDescription("More: Office").performClick()
        popupOption("Edit proxy").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Work")
        compose.onNodeWithText("Port").performScrollTo().performTextReplacement("8443")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(profile.copy(name = "Work", port = 8443), editor.saved) }
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    private fun option(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun popupOption(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isPopup()))

    private fun policyRow(text: String) = compose.onNode(isToggleable() and hasAnyDescendant(hasText(text)), useUnmergedTree = true)

    @Test
    fun accountRowShowsProfileAndKeepsItsOwnDropdown() {
        val state = State()
        val user = createSampleUser().copy(key = account, avatar = null)
        state.accountProfiles = persistentMapOf(account to user)
        state.operation = UiState.Error(IllegalStateException())
        show(state)
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(user.handle.canonical).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(user.name.raw).assertIsDisplayed()
        compose.onNodeWithContentDescription("Proxy: ${state.accountLabel(state.accounts.first())}").performClick()
        popupOption("Direct connection").performClick()
        compose.runOnIdle {
            assertEquals(account to ProxyPolicy(ProxyMode.DIRECT), state.selection)
            assertEquals(0, permissionRequests)
        }
    }

    @Test
    fun settingsReadFailureShowsRetryInsteadOfAnIndefiniteSpinner() {
        var retries = 0
        val state =
            object : ProxySettingsState by (State().apply { settings = UiState.Error(IllegalStateException()) }) {
                override fun retry() {
                    retries++
                }
            }
        show(state)
        option("Proxy operation failed. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add proxy").assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    private fun show(
        state: ProxySettingsState,
        editorState: EditorState = EditorState(),
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(),
            ) {
                FlareTheme(darkTheme = false, dynamicColor = false) {
                    ProxySettingsContent(state, onBack = {}, beforeConnect = {
                        permissionRequests++
                        pendingPermission = it
                    }, editorState = {
                        DisposableEffect(editorState) {
                            onDispose {
                                editorState.saveState = null
                                editorState.testState = null
                            }
                        }
                        remember(editorState.saveState, editorState.testState) { editorState }
                    })
                }
            }
        }
    }

    private inner class State(
        initialSettings: ProxySettings = ProxySettings(profiles = listOf(profile)),
    ) : ProxySettingsState {
        override var settings by mutableStateOf<UiState<ProxySettings>>(UiState.Success(initialSettings))
        override val accounts =
            persistentListOf(UiAccount(account, "mastodon", "Mastodon"), UiAccount(MicroBlogKey("bob", "nostr"), "nostr", "Nostr"))
        override var accountProfiles by mutableStateOf(persistentMapOf<MicroBlogKey, UiProfile>())
        override var operation by mutableStateOf<UiState<Unit>?>(null)
        var removed: String? = null
        var selection: Pair<MicroBlogKey?, ProxyPolicy>? = null

        override fun retry() = Unit

        override fun select(
            accountKey: MicroBlogKey?,
            policy: ProxyPolicy,
        ) {
            selection = accountKey to policy
        }

        override fun remove(id: String) {
            removed = id
        }
    }

    private class EditorState : ProxyEditorState {
        override var saveState by mutableStateOf<UiState<Unit>?>(null)
        override var testState by mutableStateOf<UiState<ProxyProfile>?>(null)
        var saved: ProxyProfile? = null

        override fun save(profile: ProxyProfile) {
            saved = profile
            saveState = UiState.Success(Unit)
        }

        override fun test(profile: ProxyProfile) {
            testState = UiState.Success(profile)
        }
    }
}
