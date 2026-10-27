package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.createSampleUser
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
            state.busy = true
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
    fun referencedProfileCannotBeDeletedFromMenu() {
        val state = State(ProxySettings(profiles = listOf(profile), global = ProxyPolicy(ProxyMode.PROXY, profile.id)))
        show(state)
        compose.onNodeWithContentDescription("More: Office").performScrollTo().performClick()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.runOnIdle { assertNull(state.removed) }
    }

    @Test
    fun editorKeepsDraftDuringFailureAndCancelDiscardsIt() {
        val state = State()
        show(state)
        compose.onNodeWithText("Office").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Work")
        compose.runOnIdle { state.busy = true }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle {
            state.busy = false
            state.editorError = ProxySettingsError.REQUEST_FAILED
        }
        compose.onAllNodesWithText("Proxy operation failed. Please try again.").assertCountEquals(1)
        option("Proxy operation failed. Please try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Name").performScrollTo().assertTextContains("Work")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(state.saved) }
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Office").performClick()
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Name").assertTextContains("Office")
    }

    @Test
    fun editorSavesCurrentFieldsAndClosesOnSuccess() {
        val state = State()
        show(state)
        compose.onNodeWithText("Office").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Work")
        compose.onNodeWithText("Port").performScrollTo().performTextReplacement("8443")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(profile.copy(name = "Work", port = 8443), state.saved) }
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
        state.error = ProxySettingsError.INVALID_POLICY
        show(state)
        compose.onNodeWithText("Select an available proxy policy").assertIsDisplayed()
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
            object : ProxySettingsState by State() {
                override val settings = null
                override val error = ProxySettingsError.REQUEST_FAILED

                override fun retry() {
                    retries++
                }
            }
        show(state)
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add proxy").assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    private fun show(state: ProxySettingsState) {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(),
            ) {
                FlareTheme(darkTheme = false, dynamicColor = false) {
                    ProxySettingsContent(state, onBack = {}, beforeConnect = {
                        permissionRequests++
                        pendingPermission = it
                    })
                }
            }
        }
    }

    private inner class State(
        initialSettings: ProxySettings = ProxySettings(profiles = listOf(profile)),
    ) : ProxySettingsState {
        override val settings = initialSettings
        override val accounts =
            persistentListOf(UiAccount(account, "mastodon", "Mastodon"), UiAccount(MicroBlogKey("bob", "nostr"), "nostr", "Nostr"))
        override var accountProfiles by mutableStateOf(persistentMapOf<MicroBlogKey, UiProfile>())
        override var editorError by mutableStateOf<ProxySettingsError?>(null)
        override var busy by mutableStateOf(false)
        override var error by mutableStateOf<ProxySettingsError?>(null)
        override var tested by mutableStateOf<ProxyProfile?>(null)
        var saved: ProxyProfile? = null
        var removed: String? = null
        var selection: Pair<MicroBlogKey?, ProxyPolicy>? = null

        override fun retry() = Unit

        override fun select(
            accountKey: MicroBlogKey?,
            policy: ProxyPolicy,
        ) {
            selection = accountKey to policy
        }

        override fun save(
            profile: ProxyProfile,
            onSaved: () -> Unit,
        ) {
            saved = profile
            onSaved()
        }

        override fun remove(id: String) {
            removed = id
        }

        override fun clearEditorError() {
            editorError = null
        }

        override fun test(
            profile: ProxyProfile,
            fromEditor: Boolean,
        ) {
            assertEquals(true, fromEditor)
            tested = profile
        }
    }
}
