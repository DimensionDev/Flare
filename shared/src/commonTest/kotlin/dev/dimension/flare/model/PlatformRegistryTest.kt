package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformRegistryTest {
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
        assertEquals(PlatformMetadata("UnknownNet", UiIcon.World), registry.metadataOrFallback("UnknownNet"))
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
            override val metadata: PlatformMetadata = PlatformMetadata(platformId, UiIcon.World)
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
