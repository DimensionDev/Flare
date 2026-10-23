package dev.dimension.flare.ui.presenter.settings

import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.createSampleUser
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxySettingsStateTest {
    private val profile = ProxyProfile("work", "Work", ProxyProtocol.SOCKS5, "proxy.example", 1080)

    @Test
    fun accountLabelUsesTheVisibleNameAndHandleWithAnOfflineFallback() {
        val user = createSampleUser()
        val account = UiAccount(user.key, "Mastodon")
        val base = state(null)
        val named = state(null, persistentMapOf(user.key to user))
        assertEquals("${user.name.innerText}, ${user.handle.canonical}", named.accountLabel(account))
        assertEquals("${account.platformDisplayName}, ${account.accountKey}", base.accountLabel(account))
    }

    @Test
    fun successOnlyAppliesToTheConnectionThatWasTested() {
        val state = state(profile)
        assertTrue(state.isTested(profile))
        assertTrue(state.isTested(profile.copy(name = "Renamed")))
        assertFalse(state.isTested(profile.copy(host = "other.example")))
        assertFalse(state.isTested(profile.copy(port = 1081)))
        assertFalse(state.isTested(profile.copy(protocol = ProxyProtocol.HTTPS)))
        assertFalse(state.isTested(profile.copy(id = "another")))
        assertFalse(state(null).isTested(profile))
    }

    @Test
    fun testingUnsavedEditsDoesNotMarkTheSavedProfileSuccessful() {
        val edited = profile.copy(host = "draft.example")
        val state = state(edited)
        assertTrue(state.isTested(edited))
        assertFalse(state.isTested(profile))
    }

    private fun state(
        result: ProxyProfile?,
        profiles: ImmutableMap<MicroBlogKey, UiProfile> = persistentMapOf(),
    ): ProxySettingsState =
        object : ProxySettingsState {
            override val settings = null
            override val accounts = persistentListOf<UiAccount>()
            override val accountProfiles = profiles
            override val editorError = null
            override val busy = false
            override val error = null
            override val tested = result

            override fun select(
                accountKey: MicroBlogKey?,
                policy: ProxyPolicy,
            ) = error("Not used")

            override fun save(
                profile: ProxyProfile,
                onSaved: () -> Unit,
            ) = error("Not used")

            override fun remove(id: String) = error("Not used")

            override fun clearEditorError() = error("Not used")

            override fun test(
                profile: ProxyProfile,
                fromEditor: Boolean,
            ) = error("Not used")
        }
}
