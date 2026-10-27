package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.presenter.settings.ProxySettingsState
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProxySettingsContentTest {
    @get:Rule val compose = createComposeRule()
    private val profile = ProxyProfile("office", "Office", ProxyProtocol.HTTPS, "proxy.example", 443)
    private val account = MicroBlogKey("alice", "example.social")

    @Test
    fun editorTestsCurrentDraftAndOnlySavesOnConfirmation() {
        val state = State()
        show(state)
        compose.onNodeWithText("Add proxy").performClick()
        compose.onNodeWithContentDescription("Name").performTextReplacement("Work")
        compose.onNodeWithContentDescription("Hostname or IP address").performTextReplacement("proxy.example")
        compose.onNodeWithText("HTTPS").performClick()
        compose.onNodeWithText("SOCKS5").performClick()
        compose.onNodeWithContentDescription("Port").performTextReplacement("1080")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(state.saved)
            assertEquals(ProxyProtocol.SOCKS5, state.tested?.protocol)
            assertEquals(1080, state.tested?.port)
        }
        compose.onNodeWithText("Connection succeeded").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Hostname or IP address").performScrollTo().performTextReplacement("other.example")
        compose.onNodeWithText("Connection succeeded").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("other.example", state.saved?.host) }
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun cancelAndEscapeDoNotSaveOrChangeExistingProfile() {
        val state = State(ProxySettings(profiles = listOf(profile)))
        show(state)
        compose.onNodeWithText("Edit proxy").performClick()
        compose.onNodeWithContentDescription("Name").performTextReplacement("Changed")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(state.saved)
            assertEquals(profile, state.settings.profiles.single())
        }
        compose.onNodeWithText("Edit proxy").performClick()
        compose
            .onNodeWithContentDescription("Name")
            .assertTextContains("Office")
            .performClick()
            .performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.runOnIdle { assertNull(state.saved) }
    }

    @Test
    fun policySelectionKeepsGlobalAndAccountScopesSeparate() {
        val state = State(ProxySettings(profiles = listOf(profile)))
        show(state)
        compose.onNodeWithText("System settings").performClick()
        compose.onNodeWithText("Direct connection").performClick()
        compose.runOnIdle { assertEquals(null to ProxyPolicy(ProxyMode.DIRECT), state.selection) }
        compose.onNodeWithText("Follow global").performScrollTo().performClick()
        compose.onAllNodesWithText("Office")[1].performClick()
        compose.runOnIdle { assertEquals(account to ProxyPolicy(ProxyMode.PROXY, profile.id), state.selection) }
        compose.onNodeWithText("bob@nostr").assertDoesNotExist()
    }

    @Test
    fun referencedProxyCannotBeDeleted() {
        val state = State(ProxySettings(profiles = listOf(profile), global = ProxyPolicy(ProxyMode.PROXY, profile.id)))
        show(state)
        compose.onNodeWithContentDescription("Delete").assertIsNotEnabled()
        compose.runOnIdle { state.settings = state.settings.copy(global = ProxyPolicy(ProxyMode.DIRECT)) }
        compose.onNodeWithContentDescription("Delete").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(profile.id, state.removed) }
    }

    @Test
    fun editorPreservesInputAndShowsFailureAfterBusyState() {
        val state = State()
        show(state)
        compose.onNodeWithText("Add proxy").performClick()
        compose.onNodeWithContentDescription("Name").performTextReplacement("Work")
        compose.runOnIdle { state.busy = true }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Name").assertIsNotEnabled()
        compose.runOnIdle {
            state.busy = false
            state.editorError = ProxySettingsError.REQUEST_FAILED
        }
        compose.onAllNodesWithText("Proxy operation failed. Please try again.").assertCountEquals(1)
        compose.onNodeWithText("Proxy operation failed. Please try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Name").performScrollTo().assertTextContains("Work")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Add proxy").performClick()
        compose.onNodeWithText("Proxy operation failed. Please try again.").assertDoesNotExist()
    }

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
        compose.onNodeWithContentDescription(state.accountLabel(state.accounts.first())).performClick()
        compose.onNodeWithText("Direct connection").performClick()
        compose.runOnIdle { assertEquals(account to ProxyPolicy(ProxyMode.DIRECT), state.selection) }
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
            FlareTheme(isDarkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance()) {
                        ProxySettingsContent(state)
                    }
                }
            }
        }
    }

    private inner class State(
        initialSettings: ProxySettings = ProxySettings(),
    ) : ProxySettingsState {
        override var settings by mutableStateOf(initialSettings)
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
