package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class TestNetKoinRegistrationTest {
    @Test
    fun moduleDependencyAutomaticallyContributesPlatformSpec() =
        kotlinx.coroutines.test.runTest {
            val application = startKoin<TestNetKoinApplication>()
            try {
                val runtimeData = application.koin.get<PlatformRuntimeData>()
                val registry = application.koin.get<PlatformRegistry>()
                val loginRegistry = application.koin.get<LoginPlatformRegistry>()

                assertEquals(setOf("Mastodon", "TestNet"), runtimeData.platformSpecs.map { it.platformId }.toSet())
                assertIs<TestNetPlatformSpec>(registry.require("TestNet"))
                assertEquals(PlatformMetadata("Test Network", UiIcon.World, listOf("testnet")), registry.metadataOrFallback("TestNet"))

                val detected = loginRegistry.detectPlatformId("testnet.example")
                assertEquals("TestNet", detected.platformId)
                assertEquals("Test Network", detected.platformDisplayName)
                assertEquals(listOf(LoginMethodType.Password), detected.loginMethods.map { it.type })

                val source = registry.require("TestNet").createDataSource(TestDataSourceContext)
                assertSame(TestNetDataSource, source)
            } finally {
                stopKoin()
            }
        }
}

@KoinApplication(configurations = ["testnet"])
internal class TestNetKoinApplication

@Module
@Configuration("testnet")
internal class TestNetKoinModule {
    @Single(binds = [PlatformSpec::class])
    fun defaultGuestSpec(): TestDefaultGuestPlatformSpec = TestDefaultGuestPlatformSpec()

    @Single(binds = [PlatformSpec::class])
    fun testNetSpec(): TestNetPlatformSpec = TestNetPlatformSpec()

    @Single
    fun runtimeData(platformSpecs: List<PlatformSpec>): PlatformRuntimeData = PlatformRuntimeData(platformSpecs, emptyList())

    @Single
    fun platformRegistry(data: PlatformRuntimeData): PlatformRegistry = PlatformRegistry(data)

    @Single
    fun loginPlatformRegistry(platformRegistry: PlatformRegistry): LoginPlatformRegistry = LoginPlatformRegistry(platformRegistry)
}

internal class TestNetPlatformSpec :
    EmptyTestPlatformSpec("TestNet"),
    LoginPlatformProvider {
    override val metadata: PlatformMetadata = PlatformMetadata("Test Network", UiIcon.World, listOf("testnet"))
    override val detector: PlatformDetector =
        object : PlatformDetector {
            override suspend fun detect(host: String): NodeDetection? =
                host.takeIf { it == "testnet.example" }?.let {
                    NodeDetection(
                        host = it,
                        software = "testnet",
                        compatibleMode = false,
                    )
                }
        }
    override val methods: List<LoginMethodSpec> =
        listOf(LoginMethodSpec(LoginMethodType.Password, UiStrings.PasswordLogin))

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                UiInstance(
                    name = metadata.displayName,
                    description = null,
                    iconUrl = null,
                    domain = "testnet.example",
                    platformId = platformId,
                    bannerUrl = null,
                    usersCount = 0,
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = error("Not used")

    override fun createHandler(context: LoginContext): LoginMethodHandler = error("Not used")

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = TestNetDataSource
}

internal class TestDefaultGuestPlatformSpec : EmptyTestPlatformSpec("Mastodon") {
    override val isDefaultGuest: Boolean = true
}

internal abstract class EmptyTestPlatformSpec(
    final override val platformId: String,
) : PlatformSpec {
    override val metadata: PlatformMetadata = PlatformMetadata(platformId, UiIcon.World)
    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = TestNetDataSource

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = TestNetDataSource
}

private object TestDataSourceContext : PlatformDataSourceContext {
    override val accountKey: MicroBlogKey = MicroBlogKey("user", "testnet.example")

    override fun <T : Any> credential(serializer: KSerializer<T>): T = error("Not used")

    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = error("Not used")

    override suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    ) = Unit
}

private object TestNetDataSource : MicroblogDataSource {
    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun searchUser(query: String): RemoteLoader<UiProfile> = error("Not used")

    override fun discoverUsers(): RemoteLoader<UiProfile> = error("Not used")

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = error("Not used")

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}
