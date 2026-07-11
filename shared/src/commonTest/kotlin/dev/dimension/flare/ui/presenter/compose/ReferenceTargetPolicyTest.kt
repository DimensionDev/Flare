package dev.dimension.flare.ui.presenter.compose

import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceTargetPolicyTest {
    @Test
    fun newPostDoesNotTreatSelectedAccountAsReferenceSource() {
        val accountKey = MicroBlogKey("source", "mastodon.social")

        val metadata =
            initialReferenceMetadata(
                accountType = AccountType.Specific(accountKey),
                composeStatus = null,
            )

        assertNull(metadata.sourceAccountKey)
    }

    @Test
    fun referenceUsesSelectedAccountAsSource() {
        val accountKey = MicroBlogKey("source", "mastodon.social")

        val metadata =
            initialReferenceMetadata(
                accountType = AccountType.Specific(accountKey),
                composeStatus = ComposeStatus.Quote(MicroBlogKey("post", "mastodon.social")),
            )

        assertEquals(accountKey, metadata.sourceAccountKey)
    }

    @Test
    fun crossPlatformReferenceAllowsEveryComposeTarget() {
        val sourceAccountKey = MicroBlogKey("source", "mastodon.social")

        assertTrue(
            isReferenceTargetSelectable(
                enableCrossPlatformReference = true,
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = sourceAccountKey,
                targetAccount = account(PlatformType.Bluesky, "bsky.social"),
            ),
        )
        assertTrue(
            isReferenceTargetSelectable(
                enableCrossPlatformReference = true,
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = sourceAccountKey,
                targetAccount = account(PlatformType.Mastodon, "other.instance"),
            ),
        )
    }

    @Test
    fun disabledCrossPlatformReferenceKeepsNativeCompatibilityFilter() {
        assertFalse(
            isReferenceTargetSelectable(
                enableCrossPlatformReference = false,
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = MicroBlogKey("source", "mastodon.social"),
                targetAccount = account(PlatformType.Bluesky, "bsky.social"),
            ),
        )
    }

    @Test
    fun differentPlatformRequiresShareImage() {
        assertTrue(
            requiresReferenceShareImage(
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = MicroBlogKey("source", "mastodon.social"),
                targetAccount = account(PlatformType.Bluesky, "bsky.social"),
            ),
        )
    }

    @Test
    fun sameFederatedHostUsesNativeReference() {
        assertFalse(
            requiresReferenceShareImage(
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = MicroBlogKey("source", "MASTODON.SOCIAL"),
                targetAccount = account(PlatformType.Mastodon, "mastodon.social"),
            ),
        )
    }

    @Test
    fun differentFederatedHostRequiresShareImage() {
        assertTrue(
            requiresReferenceShareImage(
                sourcePlatform = PlatformType.Misskey,
                sourceAccountKey = MicroBlogKey("source", "misskey.io"),
                targetAccount = account(PlatformType.Misskey, "misskey.example"),
            ),
        )
    }

    @Test
    fun missingFederatedSourceAccountFailsSafeToShareImage() {
        assertTrue(
            requiresReferenceShareImage(
                sourcePlatform = PlatformType.Mastodon,
                sourceAccountKey = null,
                targetAccount = account(PlatformType.Mastodon, "mastodon.social"),
            ),
        )
    }

    @Test
    fun nonFederatedSamePlatformUsesNativeReference() {
        assertFalse(
            requiresReferenceShareImage(
                sourcePlatform = PlatformType.Bluesky,
                sourceAccountKey = MicroBlogKey("source", "source.example"),
                targetAccount = account(PlatformType.Bluesky, "target.example"),
            ),
        )
    }

    @Test
    fun unknownSourcePlatformFailsSafeInsteadOfSendingNativeReference() {
        val data =
            ComposeData(
                content = "reference",
                referenceStatus =
                    ComposeData.ReferenceStatus(
                        composeStatus = ComposeStatus.Quote(MicroBlogKey("post", "unknown.example")),
                        sourcePlatform = null,
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            data.forTarget(
                account = account(PlatformType.Mastodon, "mastodon.social"),
                newPostConfig =
                    ComposeConfig(
                        media =
                            ComposeConfig.Media(
                                maxCount = 4,
                                canSensitive = true,
                                altTextMaxLength = 1_000,
                                allowMediaOnly = true,
                            ),
                    ),
            )
        }
    }

    private fun account(
        platformType: PlatformType,
        host: String,
    ): UiAccount =
        UiAccount(
            accountKey = MicroBlogKey(platformType.name, host),
            platformType = platformType,
        )
}
