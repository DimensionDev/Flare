package dev.dimension.flare.ui.presenter.login

import kotlin.test.Test
import kotlin.test.assertEquals

class BlueskyHandleTest {
    @Test
    fun appendsLegacyAvailableUserDomainWithLeadingDot() {
        assertEquals(
            "alice.bsky.social",
            "alice".withBlueskyUserDomain(".bsky.social"),
        )
    }

    @Test
    fun appendsAvailableUserDomainWithoutLeadingDot() {
        assertEquals(
            "alice.t4tkiss.ing",
            "alice".withBlueskyUserDomain("t4tkiss.ing"),
        )
    }
}
