package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import kotlin.test.Test
import kotlin.test.assertEquals

class UiHandleTest {
    @Test
    fun displayDefaultsToCanonicalHandle() {
        val handle = UiHandle(" @alice@remote.example ", " @remote.example ")

        assertEquals("@alice@remote.example", handle.display)
        assertEquals(handle.canonical, handle.display)
    }

    @Test
    fun displayOverridePreservesCanonicalIdentityAndExplicitEmptyText() {
        val handle = UiHandle("bsky.app", "bsky.social", displayOverride = "@bsky.app")

        assertEquals("@bsky.app", handle.display)
        assertEquals("bsky.app", handle.normalizedRaw)
        assertEquals("bsky.social", handle.normalizedHost)
        assertEquals("@bsky.app@bsky.social", handle.canonical)
        assertEquals("", handle.copy(displayOverride = "").display)
    }

    @Test
    fun displayOverrideSurvivesStorage() {
        val handle = UiHandle("bsky.app", "bsky.social", displayOverride = "@bsky.app")
        val restored = handle.encodeProtobuf().decodeProtobuf<UiHandle>()

        assertEquals(handle, restored)
        assertEquals("@bsky.app", restored.display)
        assertEquals(handle.canonical, restored.canonical)
    }
}
