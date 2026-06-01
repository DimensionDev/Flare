package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class LoginPlatformRegistryTest {
    @Test
    fun duplicatePlatformProvidersFailFast() {
        assertFailsWith<IllegalArgumentException> {
            LoginPlatformRegistry(
                testRuntimeData(
                    testProvider(PlatformType.Mastodon),
                    testProvider(PlatformType.Mastodon),
                ),
            )
        }
    }

    @Test
    fun providerCanBeFoundByPlatformType() {
        val mastodon = testLoginPlatformSpec(testProvider(PlatformType.Mastodon))
        val bluesky = testLoginPlatformSpec(testProvider(PlatformType.Bluesky))

        val registry = LoginPlatformRegistry(testRuntimeData(listOf(mastodon, bluesky)))

        assertSame(mastodon, registry.get(PlatformType.Mastodon))
        assertSame(bluesky, registry.require(PlatformType.Bluesky))
    }

    @Test
    fun methodsAreSortedByPriorityDescending() {
        val registry =
            LoginPlatformRegistry(
                testRuntimeData(
                    testProvider(
                        platformType = PlatformType.Bluesky,
                        methods =
                            listOf(
                                LoginMethodSpec(LoginMethodType.Password, UiStrings.PasswordLogin, priority = 0),
                                LoginMethodSpec(LoginMethodType.OAuth, UiStrings.OAuthLogin, priority = 20),
                                LoginMethodSpec(LoginMethodType.WebCookie, UiStrings.WebCookieLogin, priority = 10),
                            ),
                        ),
                ),
            )

        assertEquals(
            listOf(LoginMethodType.OAuth, LoginMethodType.WebCookie, LoginMethodType.Password),
            registry.methods(PlatformType.Bluesky).map { it.type },
        )
    }

    @Test
    fun detectionUsesLoginProviderDetectorsByPriority() =
        runTest {
            val registry =
                LoginPlatformRegistry(
                    testRuntimeData(
                        testProvider(
                            platformType = PlatformType.Mastodon,
                            detectorPriority = 0,
                            detectedSoftware = "mastodon",
                        ),
                        testProvider(
                            platformType = PlatformType.Misskey,
                            detectorPriority = 10,
                            detectedSoftware = "misskey",
                        ),
                    ),
                )

            val detected = registry.detectPlatformType("https://example.social/")

            assertEquals(PlatformType.Misskey, detected.platformType)
            assertEquals("example.social", detected.host)
            assertEquals("misskey", detected.software)
        }

    @Test
    fun runtimeDataDerivesProvidersFromPlatformSpecs() {
        val mastodon = testProvider(PlatformType.Mastodon)
        val mastodonSpec = testLoginPlatformSpec(mastodon)
        val blueskySpec = testPlatformSpec(PlatformType.Bluesky)

        val registry =
            LoginPlatformRegistry(
                PlatformRuntimeData(
                    platformSpecs = listOf(mastodonSpec, blueskySpec),
                    extraTimelineSpecs = emptyList(),
                ),
            )

        assertSame(mastodonSpec, registry.require(PlatformType.Mastodon))
        assertEquals(null, registry.get(PlatformType.Bluesky))
    }

    private fun testProvider(
        platformType: PlatformType,
        methods: List<LoginMethodSpec> = emptyList(),
        detectorPriority: Int = 0,
        detectedSoftware: String? = null,
    ): LoginPlatformProvider =
        object : LoginPlatformProvider {
            override val platformType: PlatformType = platformType
            override val metadata: PlatformTypeMetadata =
                PlatformTypeMetadata(
                    displayName = platformType.name,
                    icon = UiIcon.Mastodon,
                )
            override val detector: PlatformDetector =
                object : PlatformDetector {
                    override val priority: Int = detectorPriority

                    override suspend fun detect(host: String): NodeData? =
                        detectedSoftware?.let {
                            NodeData(
                                host = host,
                                platformType = platformType,
                                software = it,
                                compatibleMode = false,
                            )
                        }
                }
            override val methods: List<LoginMethodSpec> = methods

            override fun agreementUrl(host: String): String? = null

            override suspend fun recommendInstances(): List<RecommendedInstance> = emptyList()

            override suspend fun instanceMetadata(host: String): UiInstanceMetadata = error("Not used")

            override fun createHandler(context: LoginContext): LoginMethodHandler = error("Not used")
        }

    private fun testRuntimeData(vararg providers: LoginPlatformProvider): PlatformRuntimeData =
        testRuntimeData(providers.map(::testLoginPlatformSpec))

    private fun testRuntimeData(platformSpecs: List<PlatformSpec>): PlatformRuntimeData =
        PlatformRuntimeData(
            platformSpecs = platformSpecs,
            extraTimelineSpecs = emptyList(),
        )

    private interface TestLoginPlatformSpec : PlatformSpec, LoginPlatformProvider

    private fun testLoginPlatformSpec(provider: LoginPlatformProvider): TestLoginPlatformSpec =
        object : TestLoginPlatformSpec,
            LoginPlatformProvider by provider {
            override val type: PlatformType = provider.platformType
            override val metadata: PlatformTypeMetadata = provider.metadata
            override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

            override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

            override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("Not used")

            override fun guestDataSource(
                host: String,
                locale: String,
            ): MicroblogDataSource = error("Not used")
        }

    private fun testPlatformSpec(platformType: PlatformType): PlatformSpec =
        object : PlatformSpec {
            override val type: PlatformType = platformType
            override val metadata: PlatformTypeMetadata =
                PlatformTypeMetadata(
                    displayName = platformType.name,
                    icon = UiIcon.Mastodon,
                )
            override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

            override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

            override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("Not used")

            override fun guestDataSource(
                host: String,
                locale: String,
            ): MicroblogDataSource = error("Not used")
        }
}
