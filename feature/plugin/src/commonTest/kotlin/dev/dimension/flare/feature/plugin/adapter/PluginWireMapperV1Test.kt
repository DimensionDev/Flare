package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginWireMapperV1Test {
    private val mapper =
        PluginWireMapperV1(
            pluginId = "test.plugin",
            platformId = "Test",
            accountKey = MicroBlogKey("me", "local.example"),
            originHost = "local.example",
            profileAvailable = true,
            postDetailAvailable = true,
            postMutationAvailable = true,
            postDeleteAvailable = true,
            composeAvailable = true,
        )

    @Test
    fun remoteProfileHandleKeepsItsFederatedHost() {
        val profile =
            ProfileV1(
                key = EntityKeyV1("42", "local.example"),
                handle = "@alice@remote.example",
                displayName = "Alice",
            )

        assertEquals("@alice@remote.example", mapper.profile(profile).handle.canonical)
    }

    @Test
    fun localProfileHandleFallsBackToEntityHost() {
        val profile =
            ProfileV1(
                key = EntityKeyV1("42", "local.example"),
                handle = "alice",
                displayName = "Alice",
            )

        assertEquals("@alice@local.example", mapper.profile(profile).handle.canonical)
    }
}
