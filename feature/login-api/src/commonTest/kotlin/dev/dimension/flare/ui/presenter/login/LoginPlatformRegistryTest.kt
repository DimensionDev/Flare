package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
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
                LoginRuntimeData(
                    providers =
                        listOf(
                            testProvider(PlatformType.Mastodon),
                            testProvider(PlatformType.Mastodon),
                        ),
                ),
            )
        }
    }

    @Test
    fun providerCanBeFoundByPlatformType() {
        val mastodon = testProvider(PlatformType.Mastodon)
        val bluesky = testProvider(PlatformType.Bluesky)

        val registry = LoginPlatformRegistry(LoginRuntimeData(listOf(mastodon, bluesky)))

        assertSame(mastodon, registry.get(PlatformType.Mastodon))
        assertSame(bluesky, registry.require(PlatformType.Bluesky))
    }

    @Test
    fun methodsAreSortedByPriorityDescending() {
        val registry =
            LoginPlatformRegistry(
                LoginRuntimeData(
                    listOf(
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
                    LoginRuntimeData(
                        listOf(
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
                    ),
                )

            val detected = registry.detectPlatformType("https://example.social/")

            assertEquals(PlatformType.Misskey, detected.platformType)
            assertEquals("example.social", detected.host)
            assertEquals("misskey", detected.software)
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
}
