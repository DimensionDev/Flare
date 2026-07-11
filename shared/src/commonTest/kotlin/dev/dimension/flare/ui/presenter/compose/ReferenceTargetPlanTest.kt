package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceTargetPlanTest {
    @Test
    fun nativeQuoteWithoutMediaConfigDoesNotBlockCrossPlatformReferenceImage() {
        val nativeTarget =
            target(
                platformType = PlatformType.Mastodon,
                requiresShareImage = false,
                composeConfig = ComposeConfig(media = null),
            )
        val crossPlatformTarget =
            target(
                platformType = PlatformType.Bluesky,
                requiresShareImage = true,
                composeConfig = composeConfig(maxMediaCount = 1),
            )
        val targets = listOf(nativeTarget, crossPlatformTarget)

        assertNull(nativeTarget.composeConfig.merge(crossPlatformTarget.composeConfig).media)
        assertTrue(targets.hasReferenceShareImageCapacity(userMediaCount = 0))
    }

    @Test
    fun everyShareImageTargetMustHaveMediaSupportAndCapacity() {
        val targets =
            listOf(
                target(
                    platformType = PlatformType.Bluesky,
                    requiresShareImage = true,
                    composeConfig = composeConfig(maxMediaCount = 2),
                ),
                target(
                    platformType = PlatformType.Nostr,
                    requiresShareImage = true,
                    composeConfig = ComposeConfig(media = null),
                ),
            )

        assertFalse(targets.hasReferenceShareImageCapacity(userMediaCount = 0))
    }

    @Test
    fun referenceImageConsumesOneSlotOnlyOnShareImageTargets() {
        val targets =
            listOf(
                target(
                    platformType = PlatformType.Mastodon,
                    requiresShareImage = false,
                    composeConfig = ComposeConfig(media = null),
                ),
                target(
                    platformType = PlatformType.Bluesky,
                    requiresShareImage = true,
                    composeConfig = composeConfig(maxMediaCount = 2),
                ),
            )

        assertTrue(targets.hasReferenceShareImageCapacity(userMediaCount = 1))
        assertFalse(targets.hasReferenceShareImageCapacity(userMediaCount = 2))
    }

    @Test
    fun unavailableLiveMetadataDoesNotErasePersistedDraftMetadata() {
        val sourceAccountKey = MicroBlogKey("source", "mastodon.social")
        val reference =
            ComposeData.ReferenceStatus(
                composeStatus = ComposeStatus.Quote(MicroBlogKey("post", "mastodon.social")),
                sourceAccountKey = sourceAccountKey,
                sourcePlatform = PlatformType.Mastodon,
                shareUrl = "https://mastodon.social/@source/post",
            )

        val enriched =
            ComposeData(content = "", referenceStatus = reference)
                .withReferenceMetadata(ReferenceMetadata())

        assertEquals(reference, enriched.referenceStatus)
    }

    private fun target(
        platformType: PlatformType,
        requiresShareImage: Boolean,
        composeConfig: ComposeConfig,
    ): ReferenceTargetPlan =
        ReferenceTargetPlan(
            account =
                UiAccount(
                    accountKey = MicroBlogKey(id = platformType.name, host = "example.com"),
                    platformType = platformType,
                ),
            requiresShareImage = requiresShareImage,
            composeConfig = composeConfig,
        )

    private fun composeConfig(maxMediaCount: Int): ComposeConfig =
        ComposeConfig(
            media =
                ComposeConfig.Media(
                    maxCount = maxMediaCount,
                    canSensitive = true,
                    altTextMaxLength = 1_000,
                    allowMediaOnly = true,
                ),
        )
}
