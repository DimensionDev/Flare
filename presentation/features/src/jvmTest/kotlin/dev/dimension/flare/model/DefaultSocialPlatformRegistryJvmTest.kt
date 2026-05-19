package dev.dimension.flare.model

import kotlin.test.Test
import kotlin.test.assertContains

class DefaultSocialPlatformRegistryJvmTest {
    @Test
    fun nonWebRegistryRegistersNostrPlugin() {
        assertContains(defaultSocialPlatformRegistry.loginPlatformTypes, PlatformType.Nostr)
    }

    @Test
    fun nonWebRegistryIncludesNostrTimelineSpecs() {
        val specIds = defaultSocialPlatformRegistry.requireSpec(PlatformType.Nostr).timelineSpecs.map { it.id }

        assertContains(specIds, "common.home")
    }
}
