package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.unavailableAccountService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformRegistryTest {
    @Test
    fun anOpenTimelineUsesTheNewSpecAndRecoversAfterReattachment() =
        runTest {
            val builtIn = spec("Mastodon", isDefaultGuest = true)
            val data = PlatformRuntimeData(listOf(builtIn), emptyList())
            val registry = PlatformRegistry(data)
            val resolver =
                TimelineResolver(data, unavailableAccountService())

            fun timeline(loader: RemoteLoader<UiTimelineV2>) =
                TimelineSpec(
                    id = "plugin:test:home",
                    title =
                        UiText
                            .Raw("Home"),
                    icon =
                        IconType
                            .Material(UiIcon.Home),
                    serializer = TimelineSpec.AccountBasedData.serializer(),
                    targetId = { it.accountKey.toString() },
                    loaderFactory =
                        dev.dimension.flare.data.model.tab
                            .TimelineLoaderFactory { _, _ -> flowOf(loader) },
                )

            fun platform(timeline: TimelineSpec<*>) =
                object : PlatformSpec by spec("TestPlugin") {
                    override val timelineSpecs = persistentListOf(timeline)
                }
            val first =
                dev.dimension.flare.data.datasource.microblog.paging
                    .notSupported<UiTimelineV2>()
            val second =
                dev.dimension.flare.data.datasource.microblog.paging
                    .notSupported<UiTimelineV2>()
            val original = timeline(first)
            registry.replaceExternal("TestPlugin", platform(original))
            val emissions =
                kotlinx.coroutines.channels
                    .Channel<RemoteLoader<UiTimelineV2>>(
                        Channel.UNLIMITED,
                    )
            val target = original.target(TimelineSpec.AccountBasedData(MicroBlogKey("alice", "example.com")))
            val job = backgroundScope.launch { resolver.resolveLoader(target).collect { emissions.send(it) } }
            assertSame(first, emissions.receive())
            registry.replaceExternal("TestPlugin", platform(timeline(second)))
            assertSame(second, emissions.receive())
            registry.replaceExternal("TestPlugin", null)
            assertTrue(emissions.receive() !== second)
            registry.replaceExternal("TestPlugin", platform(original))
            assertSame(first, emissions.receive())
            job.cancel()
        }

    @Test
    fun replacesOnlyExternalPlatformsAndCanRemoveThemAgain() {
        val builtIn = spec("Mastodon", isDefaultGuest = true)
        val original = spec("TestPlugin")
        val data =
            PlatformRuntimeData(
                listOf(builtIn),
                emptyList(),
                listOf(
                    object : PlatformSpecSource {
                        override fun load(occupiedPlatformIds: Set<String>) = listOf(original)
                    },
                ),
            )
        val registry = PlatformRegistry(data)
        val replacement = spec("TestPlugin")
        registry.replaceExternal("TestPlugin", replacement)
        assertSame(replacement, registry.require("TestPlugin"))
        assertSame(builtIn, registry.defaultGuest)
        assertTrue(data.platforms.value.any { it === replacement })
        assertFailsWith<IllegalArgumentException> { registry.replaceExternal("Mastodon", spec("Mastodon")) }
        assertFailsWith<IllegalArgumentException> { registry.replaceExternal("testplugin", spec("testplugin")) }
        assertFailsWith<IllegalArgumentException> { registry.replaceExternal("TestPlugin", spec("Other")) }
        assertSame(replacement, registry.require("TestPlugin"))
        registry.replaceExternal("TestPlugin", null)
        assertFalse(registry.isRegistered("TestPlugin"))
        registry.replaceExternal("TestPlugin", original)
        assertSame(original, registry.require("TestPlugin"))
    }

    @Test
    fun acceptsValidIdsWithoutNormalizingThem() {
        val ids = listOf("Mastodon", "xQt", "TestNet_2", "A-b")
        val registry = registry(ids.mapIndexed { index, id -> spec(id, isDefaultGuest = index == 0) })

        assertEquals(ids.sorted(), registry.all.map { it.platformId })
        assertSame(registry.get("Mastodon"), registry.require("Mastodon"))
        assertNull(registry.get("mastodon"))
        assertFalse(registry.isRegistered("mastodon"))
    }

    @Test
    fun rejectsInvalidIds() {
        listOf("", "1Mastodon", "has space", "slash/id", "dot.id", "emoji😀").forEach { id ->
            assertFailsWith<IllegalArgumentException>(message = id) {
                registry(listOf(spec(id, isDefaultGuest = true)))
            }
        }
    }

    @Test
    fun rejectsExactAndCaseInsensitiveDuplicates() {
        listOf(
            listOf("Mastodon", "Mastodon"),
            listOf("Mastodon", "mastodon"),
        ).forEach { ids ->
            assertFailsWith<IllegalArgumentException> {
                registry(ids.mapIndexed { index, id -> spec(id, isDefaultGuest = index == 0) })
            }
        }
    }

    @Test
    fun requiresExactlyOneDefaultGuestPlatform() {
        assertFailsWith<IllegalArgumentException> {
            registry(listOf(spec("One"), spec("Two")))
        }
        assertFailsWith<IllegalArgumentException> {
            registry(
                listOf(
                    spec("One", isDefaultGuest = true),
                    spec("Two", isDefaultGuest = true),
                ),
            )
        }
    }

    @Test
    fun fallbackAndCapabilitiesAreRegistryDriven() {
        val relay = spec("RelayNet", isDefaultGuest = true, capabilities = setOf(PlatformCapability.RelayManagement))
        val registry = registry(listOf(relay))

        assertEquals(relay.metadata, registry.metadataOrFallback("RelayNet"))
        assertEquals(PlatformMetadata("UnknownNet", UiIcon.World.asType()), registry.metadataOrFallback("UnknownNet"))
        assertTrue(registry.supports("RelayNet", PlatformCapability.RelayManagement))
        assertFalse(registry.supports("RelayNet", PlatformCapability.MxgaFiltering))
        assertFalse(registry.supports("UnknownNet", PlatformCapability.RelayManagement))
        assertSame(relay, registry.defaultGuest)
    }

    @Test
    fun unsupportedPlatformExceptionCanCarryAccountContext() {
        val accountKey = MicroBlogKey(id = "alice", host = "testnet.example")
        val exception = UnsupportedPlatformException("TestNet", accountKey)

        assertEquals("TestNet", exception.platformId)
        assertEquals(accountKey, exception.accountKey)
    }

    @Test
    fun mergesStaticSourcesOnceAndKeepsBuiltInIds() {
        val builtIn = spec("Mastodon", isDefaultGuest = true)
        var loads = 0
        val source =
            object : PlatformSpecSource {
                override fun load(occupiedPlatformIds: Set<String>): List<PlatformSpec> {
                    loads += 1
                    assertEquals(setOf("Mastodon"), occupiedPlatformIds)
                    return listOf(spec("mastodon"), spec("Pixelfed"))
                }
            }
        val data = PlatformRuntimeData(listOf(builtIn), emptyList(), listOf(source))

        assertEquals(listOf("Mastodon", "Pixelfed"), data.platformSpecs.map { it.platformId })
        assertEquals(1, loads)
        assertEquals(listOf("Mastodon", "Pixelfed"), PlatformRegistry(data).all.map { it.platformId })
        assertEquals(1, loads)
    }

    @Test
    fun sourcePlatformCannotReplaceDefaultGuest() {
        val builtIn = spec("Mastodon", isDefaultGuest = true)
        val source =
            object : PlatformSpecSource {
                override fun load(occupiedPlatformIds: Set<String>): List<PlatformSpec> = listOf(spec("Pixelfed", isDefaultGuest = true))
            }
        val registry = PlatformRegistry(PlatformRuntimeData(listOf(builtIn), emptyList(), listOf(source)))

        assertSame(builtIn, registry.defaultGuest)
        assertFalse(registry.require("Pixelfed").isDefaultGuest)
    }

    private fun registry(specs: List<PlatformSpec>): PlatformRegistry =
        PlatformRegistry(
            PlatformRuntimeData(
                platformSpecs = specs,
                extraTimelineSpecs = emptyList(),
            ),
        )

    private fun spec(
        platformId: String,
        isDefaultGuest: Boolean = false,
        capabilities: Set<PlatformCapability> = emptySet(),
    ): PlatformSpec =
        object : PlatformSpec {
            override val platformId: String = platformId
            override val metadata: PlatformMetadata = PlatformMetadata(platformId, UiIcon.World.asType())
            override val isDefaultGuest: Boolean = isDefaultGuest
            override val capabilities: Set<PlatformCapability> = capabilities
            override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

            override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

            override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("Not used")

            override fun guestDataSource(
                host: String,
                locale: String,
            ): MicroblogDataSource = error("Not used")
        }
}
