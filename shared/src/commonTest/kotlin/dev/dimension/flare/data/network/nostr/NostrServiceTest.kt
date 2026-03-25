package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NostrServiceTest {
    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun exportAccountKeepsPrivateAndPublicKeysConsistent() {
        val generated = NostrService.generateAccount()
        val exported =
            NostrService.exportAccount(
                UiAccount.Nostr.Credential(
                    nsec = generated.nsec,
                ),
            )

        assertMatchesKeyPair(exported)
        assertEquals(generated.pubkeyHex, exported.pubkeyHex)
        assertEquals(generated.npub, exported.npub)
    }

    @Test
    fun importAccountAcceptsSecretOnly() {
        val imported =
            NostrService.importAccount(
                secretKeyInput = SECRET_KEY_HEX,
            )

        assertMatchesKeyPair(imported)
    }

    private fun assertMatchesKeyPair(account: NostrService.ImportedAccount) {
        assertEquals(64, account.pubkeyHex.length)
        assertTrue(account.npub.startsWith("npub1"))
        val normalizedSecret = assertNotNull(account.nsec)
        assertTrue(normalizedSecret.isNotBlank())

        val reImported =
            NostrService.importAccount(
                secretKeyInput = normalizedSecret,
            )
        assertEquals(account.pubkeyHex, reImported.pubkeyHex)
        assertEquals(account.npub, bech32PublicKey(account.pubkeyHex))
    }

    @Test
    fun timelineMapsParentsMediaQuoteAndRepost() {
        val events =
            listOf(
                ROOT_EVENT_JSON,
                REPLY_EVENT_JSON,
                QUOTE_EVENT_JSON,
                REPOST_EVENT_JSON,
            ).map {
                Event.fromJson(it)
            }

        val eventGraph = events.associateBy { it.id }
        val service = createService()
        val timeline =
            try {
                service.run {
                    events.toUiTimeline(
                        profiles = emptyMap<String, UiProfile>(),
                        eventsById = eventGraph,
                    )
                }
            } finally {
                service.close()
            }

        val root = timeline.first { it.statusKey.id == ROOT_EVENT_ID }
        assertEquals(1, root.images.size)
        val rootImage = assertIs<UiMedia.Image>(root.images.first())
        assertEquals("https://image.nostr.build/0e2f4411fcc7be6fdc0ef68f2ee58d24f8cdcea0e1475299555c5321e4f4fd02.jpg", rootImage.url)
        assertEquals(1440f, rootImage.width)
        assertEquals(1080f, rootImage.height)

        val reply = timeline.first { it.statusKey.id == REPLY_EVENT_ID }
        assertEquals(listOf(ROOT_EVENT_ID), reply.parents.map { it.statusKey.id })
        assertEquals(listOf(ROOT_EVENT_ID), reply.references.filter { it.type == ReferenceType.Reply }.map { it.statusKey.id })
        assertEquals(
            1,
            reply.parents
                .first()
                .images.size,
        )

        val quote = timeline.first { it.statusKey.id == QUOTE_EVENT_ID }
        assertEquals(listOf(ROOT_EVENT_ID), quote.quote.map { it.statusKey.id })
        assertEquals(listOf(ROOT_EVENT_ID), quote.references.filter { it.type == ReferenceType.Quote }.map { it.statusKey.id })

        val repost = timeline.first { it.statusKey.id == REPOST_EVENT_ID }
        assertNotNull(repost.internalRepost)
        assertEquals(ROOT_EVENT_ID, repost.internalRepost.statusKey.id)
        assertNotNull(repost.message)
        assertEquals(
            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
            assertIs<UiTimelineV2.Message.Type.Localized>(repost.message.type).data,
        )
    }

    @Test
    fun quoteTagArrayUsesEventWhenAvailable() {
        val target = Event.fromJson(ROOT_EVENT_JSON)
        val service = createService()

        val tag =
            try {
                service.quoteTagArray(
                    target = target,
                    statusKey = MicroBlogKey(ROOT_EVENT_ID, NostrService.NOSTR_HOST),
                    cachedAuthorPubKey = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                )
            } finally {
                service.close()
            }

        assertContentEquals(
            arrayOf("q", ROOT_EVENT_ID, "", target.pubKey),
            tag,
        )
    }

    @Test
    fun quoteTagArrayFallsBackToCachedAuthorWhenTargetMissing() {
        val service = createService()
        val tag =
            try {
                service.quoteTagArray(
                    target = null,
                    statusKey = MicroBlogKey(ROOT_EVENT_ID, NostrService.NOSTR_HOST),
                    cachedAuthorPubKey = "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac",
                )
            } finally {
                service.close()
            }

        assertContentEquals(
            arrayOf(
                "q",
                ROOT_EVENT_ID,
                "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac",
            ),
            tag,
        )
    }

    @Test
    fun resolveMetadataFallsBackToOlderParsableEvent() {
        val service = createService()
        val metadata =
            try {
                service.resolveMetadata(
                    listOf(
                        Event.fromJson(INVALID_LATEST_METADATA_EVENT_JSON) as MetadataEvent,
                        Event.fromJson(VALID_OLDER_METADATA_EVENT_JSON) as MetadataEvent,
                    ),
                )
            } finally {
                service.close()
            }

        assertNotNull(metadata)
        assertEquals("alice", metadata.name)
        assertEquals("https://example.com/avatar.png", metadata.picture)
    }

    private companion object {
        fun createService(): NostrService =
            NostrService(
                cache =
                    object : NostrCache {
                        override suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile> = emptyMap()

                        override suspend fun getPost(
                            accountKey: MicroBlogKey,
                            statusKey: MicroBlogKey,
                        ): UiTimelineV2.Post? = null
                    },
                accountKey = MicroBlogKey("nostr-test", NostrService.NOSTR_HOST),
                credential = UiAccount.Nostr.Credential(nsec = NostrService.generateAccount().nsec),
            )

        const val SECRET_KEY_HEX = "1111111111111111111111111111111111111111111111111111111111111111"
        const val ROOT_EVENT_ID = "1b14014e85b5a3f554dc92198ce118d83562147ca08a98e4bb07b00d003108f7"
        const val ROOT_EVENT_PUBKEY = "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac"
        const val REPLY_EVENT_ID = "b355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23d"
        const val QUOTE_EVENT_ID = "c355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23e"
        const val REPOST_EVENT_ID = "d355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23f"
        const val VALID_OLDER_METADATA_EVENT_JSON =
            """{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754503000,"kind":0,"tags":[],"content":"{\"name\":\"alice\",\"picture\":\"https://example.com/avatar.png\"}","sig":"25524fbbf5c22e0d4f953bd8688e753bb9f36efd93b907e4dc34dc30256ddc365924ecf7529dc33e49b84d4b744f5f98b6f97c586bf63b5c0dc5c26f215f7580"}"""
        const val INVALID_LATEST_METADATA_EVENT_JSON =
            """{"id":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754504000,"kind":0,"tags":[],"content":"{","sig":"35524fbbf5c22e0d4f953bd8688e753bb9f36efd93b907e4dc34dc30256ddc365924ecf7529dc33e49b84d4b744f5f98b6f97c586bf63b5c0dc5c26f215f7580"}"""
        val ROOT_EVENT_JSON =
            """{"id":"1b14014e85b5a3f554dc92198ce118d83562147ca08a98e4bb07b00d003108f7","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754504013,"kind":1,"tags":[["imeta","url https://image.nostr.build/0e2f4411fcc7be6fdc0ef68f2ee58d24f8cdcea0e1475299555c5321e4f4fd02.jpg","dim 1440x1080"],["r","https://image.nostr.build/0e2f4411fcc7be6fdc0ef68f2ee58d24f8cdcea0e1475299555c5321e4f4fd02.jpg"]],"content":"test","sig":"25524fbbf5c22e0d4f953bd8688e753bb9f36efd93b907e4dc34dc30256ddc365924ecf7529dc33e49b84d4b744f5f98b6f97c586bf63b5c0dc5c26f215f7580"}"""
        val REPLY_EVENT_JSON =
            """{"id":"b355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23d","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754504024,"kind":1,"tags":[["e","1b14014e85b5a3f554dc92198ce118d83562147ca08a98e4bb07b00d003108f7","","root"]],"content":"reply","sig":"d7dcdd1617c5a2bb7788476cd3499de0437bea86129b30eb41d3c7749fec2603ec3fc042ce3f867917069af36b6fa50daf008c82379cc726e04ce1c82003e645"}"""
        val QUOTE_EVENT_JSON =
            """{"id":"c355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23e","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754504030,"kind":1,"tags":[["q","1b14014e85b5a3f554dc92198ce118d83562147ca08a98e4bb07b00d003108f7"]],"content":"quoting root","sig":"e7dcdd1617c5a2bb7788476cd3499de0437bea86129b30eb41d3c7749fec2603ec3fc042ce3f867917069af36b6fa50daf008c82379cc726e04ce1c82003e646"}"""
        val REPOST_EVENT_JSON =
            """{"id":"d355c1f0b68a9162cc466d3602ad6b93ec05993aeade4c7edc1f6eca8e3ae23f","pubkey":"$ROOT_EVENT_PUBKEY","created_at":1754504040,"kind":6,"tags":[["p","$ROOT_EVENT_PUBKEY"],["e","1b14014e85b5a3f554dc92198ce118d83562147ca08a98e4bb07b00d003108f7"]],"content":"","sig":"f7dcdd1617c5a2bb7788476cd3499de0437bea86129b30eb41d3c7749fec2603ec3fc042ce3f867917069af36b6fa50daf008c82379cc726e04ce1c82003e647"}"""
    }
}
