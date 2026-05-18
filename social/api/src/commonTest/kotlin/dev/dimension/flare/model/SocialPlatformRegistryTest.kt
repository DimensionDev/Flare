package dev.dimension.flare.model

import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nodeinfo.detectPlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SocialPlatformRegistryTest {
    @Test
    fun exposesOnlyRegisteredPlatformTypes() {
        val registry =
            SocialPlatformRegistry(
                listOf(
                    fakePlugin(PlatformType.Mastodon),
                    fakePlugin(PlatformType.Misskey),
                ),
            )

        assertEquals(
            listOf(
                PlatformType.Mastodon,
                PlatformType.Misskey,
            ),
            registry.loginPlatformTypes,
        )
        assertFailsWith<IllegalArgumentException> {
            registry.requireSpec(PlatformType.Bluesky)
        }
    }

    @Test
    fun keepsFirstPluginForDuplicatePlatformTypes() {
        val first = fakePlugin(PlatformType.Mastodon, displayName = "first")
        val second = fakePlugin(PlatformType.Mastodon, displayName = "second")
        val registry = SocialPlatformRegistry(listOf(first, second))

        assertSame(first.spec, registry.requireSpec(PlatformType.Mastodon))
        assertEquals("first", registry.requireSpec(PlatformType.Mastodon).metadata.displayName)
    }

    @Test
    fun recommendedInstancesComeFromRegisteredFirstPlugins() =
        runTest {
            val registry =
                SocialPlatformRegistry(
                    listOf(
                        fakePlugin(
                            type = PlatformType.Mastodon,
                            recommendedInstances = { listOf(fakeInstance(PlatformType.Mastodon, "mastodon.example")) },
                        ),
                        fakePlugin(
                            type = PlatformType.Mastodon,
                            recommendedInstances = { listOf(fakeInstance(PlatformType.Mastodon, "duplicate.example")) },
                        ),
                        fakePlugin(
                            type = PlatformType.Bluesky,
                            recommendedInstances = { listOf(fakeInstance(PlatformType.Bluesky, "bsky.example")) },
                        ),
                    ),
                )

            assertEquals(
                listOf(
                    fakeInstance(PlatformType.Mastodon, "mastodon.example"),
                    fakeInstance(PlatformType.Bluesky, "bsky.example"),
                ),
                registry.recommendedInstances(),
            )
        }

    @Test
    fun detectsPlatformWithRegisteredDetectorsOnly() =
        runTest {
            val detector =
                RecordingDetector(
                    result =
                        NodeData(
                            host = "example.com",
                            platformType = PlatformType.Mastodon,
                            software = "mastodon",
                            compatibleMode = false,
                        ),
                )
            val registry =
                SocialPlatformRegistry(
                    listOf(
                        fakePlugin(
                            type = PlatformType.Mastodon,
                            detector = detector,
                        ),
                    ),
                )

            assertEquals(
                NodeData(
                    host = "example.com",
                    platformType = PlatformType.Mastodon,
                    software = "mastodon",
                    compatibleMode = false,
                ),
                registry.detectPlatformType("https://example.com/"),
            )
            assertEquals(listOf("example.com"), detector.hosts)
        }

    private fun fakePlugin(
        type: PlatformType,
        displayName: String = type.name,
        detector: PlatformDetector = RecordingDetector(null),
        recommendedInstances: suspend () -> List<UiInstance> = { emptyList() },
    ): SocialPlatformPlugin =
        object : SocialPlatformPlugin {
            override val spec: SocialPlatformSpec =
                FakeSocialPlatformSpec(
                    type = type,
                    displayName = displayName,
                    detector = detector,
                )

            override fun createDataSource(account: UiAccount): MicroblogDataSource? = FakeMicroblogDataSource

            override suspend fun recommendedInstances(): List<UiInstance> = recommendedInstances()
        }

    private fun fakeInstance(
        type: PlatformType,
        domain: String,
    ): UiInstance =
        UiInstance(
            name = domain,
            description = domain,
            iconUrl = null,
            domain = domain,
            type = type,
            bannerUrl = null,
            usersCount = 0,
        )

    private data class FakeSocialPlatformSpec(
        override val type: PlatformType,
        private val displayName: String,
        override val detector: PlatformDetector,
    ) : SocialPlatformSpec {
        override val metadata: PlatformTypeMetadata =
            PlatformTypeMetadata(
                displayName = displayName,
                icon = UiIcon.Mastodon,
            )

        override fun agreementUrl(host: String): String? = null

        override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
            persistentListOf()

        override suspend fun instanceMetadata(host: String): UiInstanceMetadata = error("unused")

        override fun guestDataSource(
            host: String,
            locale: String,
        ): MicroblogDataSource = FakeMicroblogDataSource
    }

    private class RecordingDetector(
        private val result: NodeData?,
    ) : PlatformDetector {
        val hosts = mutableListOf<String>()

        override suspend fun detect(host: String): NodeData? {
            hosts += host
            return result
        }
    }

    private data object FakeMicroblogDataSource : MicroblogDataSource {
        override fun homeTimeline(): RemoteLoader<UiTimelineV2> = notSupported()

        override fun userTimeline(
            userKey: MicroBlogKey,
            mediaOnly: Boolean,
        ): RemoteLoader<UiTimelineV2> = notSupported()

        override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

        override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

        override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

        override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

        override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

        override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

        override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

        override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

        override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
    }
}
