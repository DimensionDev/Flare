package dev.dimension.flare.data.network.bluesky

import kotlin.test.Test
import kotlin.test.assertEquals

class TranquilDelegationRepairTest {
    @Test
    fun mergeDelegationScopesAppendsMissingScopes() {
        assertEquals(
            listOf(
                "atproto",
                "repo:*?action=create",
                "transition:generic",
                "transition:chat.bsky",
            ),
            mergeDelegationScopes(
                grantedScopes = "atproto repo:*?action=create",
                scopesToAdd = FLARE_BLUESKY_DELEGATION_SCOPES,
            ),
        )
    }

    @Test
    fun mergeDelegationScopesDoesNotDuplicateScopes() {
        assertEquals(
            listOf(
                "atproto",
                "transition:generic",
                "transition:chat.bsky",
            ),
            mergeDelegationScopes(
                grantedScopes = "atproto transition:generic transition:chat.bsky",
                scopesToAdd = FLARE_BLUESKY_DELEGATION_SCOPES,
            ),
        )
    }
}
