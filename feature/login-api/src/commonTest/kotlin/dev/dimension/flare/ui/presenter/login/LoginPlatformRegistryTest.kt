package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class LoginPlatformRegistryTest {
    @Test
    fun duplicatePlatformProvidersFailFast() {
        assertFailsWith<IllegalArgumentException> {
            testRegistry(
                testProvider("Mastodon"),
                testProvider("Mastodon"),
            )
        }
    }

    @Test
    fun providerCanBeFoundByPlatformId() {
        val mastodon = testLoginPlatformSpec(testProvider("Mastodon"))
        val bluesky = testLoginPlatformSpec(testProvider("Bluesky"))

        val registry = testRegistry(listOf(mastodon, bluesky))

        assertSame(mastodon, registry.get("Mastodon"))
        assertSame(bluesky, registry.require("Bluesky"))
    }

    @Test
    fun methodsAreSortedByPriorityDescending() {
        val registry =
            testRegistry(
                testProvider(
                    platformId = "Bluesky",
                    methods =
                        listOf(
                            LoginMethodSpec(LoginMethodType.Password, UiStrings.PasswordLogin, priority = 0),
                            LoginMethodSpec(LoginMethodType.OAuth, UiStrings.OAuthLogin, priority = 20),
                            LoginMethodSpec(LoginMethodType.WebCookie, UiStrings.WebCookieLogin, priority = 10),
                        ),
                ),
            )

        assertEquals(
            listOf(LoginMethodType.OAuth, LoginMethodType.WebCookie, LoginMethodType.Password),
            registry.methods("Bluesky").map { it.type },
        )
    }

    @Test
    fun detectionUsesLoginProviderDetectorsByPriority() =
        runTest {
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Mastodon",
                        detectorPriority = 0,
                        detectedSoftware = "mastodon",
                    ),
                    testProvider(
                        platformId = "Misskey",
                        detectorPriority = 10,
                        detectedSoftware = "misskey",
                    ),
                )

            val detected = registry.detectPlatformId("https://example.social/")

            assertEquals("Misskey", detected.platformId)
            assertEquals("example.social", detected.host)
            assertEquals("misskey", detected.software)
        }

    @Test
    fun detectionContinuesWhenDetectorFails() =
        runTest {
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Bluesky",
                        detectorPriority = 10,
                        detectorFailure = IllegalStateException("probe failed"),
                    ),
                    testProvider(
                        platformId = "Mastodon",
                        detectorPriority = 0,
                        detectedSoftware = "mastodon",
                    ),
                )

            val detected = registry.detectPlatformId("mstdn.jp")

            assertEquals("Mastodon", detected.platformId)
            assertEquals("mstdn.jp", detected.host)
        }

    @Test
    fun detectionKeepsPriorityWhenLowerPriorityCompletesFirst() =
        runTest {
            val fallbackCompleted = CompletableDeferred<Unit>()
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Misskey",
                        detectorPriority = 70,
                        methods = listOf(LoginMethodSpec(LoginMethodType.Password, UiStrings.PasswordLogin)),
                        detectorBlock = { host ->
                            fallbackCompleted.await()
                            NodeDetection(host, "misskey", compatibleMode = false)
                        },
                    ),
                    testProvider(
                        platformId = "Mastodon",
                        detectorPriority = 60,
                        detectorBlock = { host ->
                            fallbackCompleted.complete(Unit)
                            NodeDetection(host, "misskey", compatibleMode = true)
                        },
                    ),
                )

            val detected = withTimeout(1_000) { registry.detectPlatformId("example.social") }

            assertEquals("Misskey", detected.platformId)
            assertEquals("Misskey", detected.platformDisplayName)
            assertEquals(UiIcon.Mastodon, detected.platformIcon)
            assertEquals(listOf(LoginMethodType.Password), detected.loginMethods.map { it.type })
            assertEquals(false, detected.compatibleMode)
        }

    @Test
    fun detectionTimesOutHungProbeAndReturnsSuccessfulFallback() =
        runTest {
            val hungProbeCancelled = CompletableDeferred<Unit>()
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Misskey",
                        detectorPriority = 70,
                        detectorBlock = {
                            try {
                                awaitCancellation()
                            } finally {
                                hungProbeCancelled.complete(Unit)
                            }
                        },
                    ),
                    testProvider(platformId = "Mastodon", detectedSoftware = "mastodon"),
                )

            val detected = withTimeout(10_001) { registry.detectPlatformId("example.social") }

            assertEquals("Mastodon", detected.platformId)
            hungProbeCancelled.await()
        }

    @Test
    fun detectionCancelsUnneededProbesAfterSuccess() =
        runTest {
            val fallbackStarted = CompletableDeferred<Unit>()
            val fallbackCancelled = CompletableDeferred<Unit>()
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Misskey",
                        detectorPriority = 70,
                        detectorBlock = { host ->
                            fallbackStarted.await()
                            NodeDetection(host, "misskey", compatibleMode = false)
                        },
                    ),
                    testProvider(
                        platformId = "Mastodon",
                        detectorBlock = {
                            fallbackStarted.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                fallbackCancelled.complete(Unit)
                            }
                        },
                    ),
                )

            withTimeout(1_000) {
                assertEquals("Misskey", registry.detectPlatformId("example.social").platformId)
                fallbackCancelled.await()
            }
        }

    @Test
    fun detectionDoesNotSwallowCancellation() =
        runTest {
            val registry =
                testRegistry(
                    testProvider(
                        platformId = "Bluesky",
                        detectorFailure = CancellationException("cancelled"),
                    ),
                    testProvider(
                        platformId = "Mastodon",
                        detectedSoftware = "mastodon",
                    ),
                )

            assertFailsWith<CancellationException> {
                registry.detectPlatformId("mstdn.jp")
            }
        }

    @Test
    fun runtimeDataDerivesProvidersFromPlatformSpecs() {
        val mastodon = testProvider("Mastodon")
        val mastodonSpec = testLoginPlatformSpec(mastodon)
        val blueskySpec = testPlatformSpec("Bluesky")

        val registry =
            testRegistry(listOf(mastodonSpec, blueskySpec))

        assertSame(mastodonSpec, registry.require("Mastodon"))
        assertEquals(null, registry.get("Bluesky"))
    }

    private fun testProvider(
        platformId: String,
        methods: List<LoginMethodSpec> = emptyList(),
        detectorPriority: Int = 0,
        detectedSoftware: String? = null,
        detectorFailure: Throwable? = null,
        detectorBlock: (suspend (String) -> NodeDetection?)? = null,
    ): LoginPlatformProvider =
        object : LoginPlatformProvider {
            override val platformId: String = platformId
            override val metadata: PlatformMetadata =
                PlatformMetadata(
                    displayName = platformId,
                    icon = UiIcon.Mastodon,
                )
            override val detector: PlatformDetector =
                object : PlatformDetector {
                    override val priority: Int = detectorPriority

                    override suspend fun detect(host: String): NodeDetection? {
                        detectorBlock?.let { return it(host) }
                        detectorFailure?.let { throw it }
                        return detectedSoftware?.let {
                            NodeDetection(
                                host = host,
                                software = it,
                                compatibleMode = false,
                            )
                        }
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

    private fun testRegistry(vararg providers: LoginPlatformProvider): LoginPlatformRegistry =
        LoginPlatformRegistry(PlatformRegistry(testRuntimeData(*providers)))

    private fun testRegistry(platformSpecs: List<PlatformSpec>): LoginPlatformRegistry =
        LoginPlatformRegistry(PlatformRegistry(testRuntimeData(platformSpecs)))

    private fun testRuntimeData(platformSpecs: List<PlatformSpec>): PlatformRuntimeData =
        PlatformRuntimeData(
            platformSpecs = listOf(testPlatformSpec("DefaultGuest", isDefaultGuest = true)) + platformSpecs,
            extraTimelineSpecs = emptyList(),
        )

    private interface TestLoginPlatformSpec :
        PlatformSpec,
        LoginPlatformProvider

    private fun testLoginPlatformSpec(provider: LoginPlatformProvider): TestLoginPlatformSpec =
        object :
            TestLoginPlatformSpec,
            LoginPlatformProvider by provider {
            override val platformId: String = provider.platformId
            override val metadata: PlatformMetadata = provider.metadata
            override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

            override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

            override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("Not used")

            override fun guestDataSource(
                host: String,
                locale: String,
            ): MicroblogDataSource = error("Not used")
        }

    private fun testPlatformSpec(
        platformId: String,
        isDefaultGuest: Boolean = false,
    ): PlatformSpec =
        object : PlatformSpec {
            override val platformId: String = platformId
            override val isDefaultGuest: Boolean = isDefaultGuest
            override val metadata: PlatformMetadata =
                PlatformMetadata(
                    displayName = platformId,
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
