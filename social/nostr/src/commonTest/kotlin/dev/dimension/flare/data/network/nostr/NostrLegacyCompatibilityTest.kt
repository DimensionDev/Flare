package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.effectiveSigner
import dev.dimension.flare.data.platform.normalized
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

class NostrLegacyCompatibilityTest {
    private val noAmber = UnsupportedAmberSignerBridge("Unavailable")

    @Test
    fun rustKeysKeepIdentityAfterImportPersistenceExportAndSigning() =
        runTest {
            for (vector in rustNostrKeyFixtures) {
                val nsec = vector.getValue("nsec").jsonPrimitive.content
                val npub = vector.getValue("npub").jsonPrimitive.content
                val pubkey = vector.getValue("pubkey").jsonPrimitive.content
                val hex = vector.getValue("hex").jsonPrimitive.content
                val oldEvent = QuartzEvent.fromJson(vector.getValue("event").toString())
                assertTrue(oldEvent.verify(), "Quartz must verify the old Rust signature")
                for (input in listOf(nsec, hex, hex.uppercase(), "nostr:$nsec")) {
                    val imported = assertIs<NostrService.ImportedAccount.LocalKey>(NostrService.importAccount(input))
                    assertEquals(pubkey, imported.pubkeyHex)
                    assertEquals(npub, imported.npub)
                    assertEquals(nsec, imported.nsec)
                }
                val legacy =
                    JSON.decodeFromString<NostrCredential>(
                        """{"pubkeyHex":"$pubkey","nsec":"$nsec","relays":["wss://relay1.example"]}""",
                    )
                val restored =
                    JSON.decodeFromString<NostrCredential>(
                        JSON.encodeToString(legacy.normalized(MicroBlogKey(pubkey, "nostr"))),
                    )
                for (credential in listOf(legacy, restored)) {
                    assertEquals(legacy.relays, credential.relays)
                    val exported = assertIs<NostrService.ImportedAccount.LocalKey>(NostrService.exportAccount(credential))
                    assertEquals(nsec, exported.nsec)
                    assertEquals(npub, exported.npub)
                    nostrEventSigner(credential.effectiveSigner, pubkey, noAmber).use { signer ->
                        val signed = signer.sign(EventTemplate(oldEvent.createdAt, oldEvent.kind, oldEvent.tags, oldEvent.content))
                        assertEquals(oldEvent.id, signed.id, "The same key and event must keep the Rust event ID")
                        assertEquals(oldEvent.pubKey, signed.pubKey)
                        assertTrue(signed.verify())
                    }
                }
                val readOnly = assertIs<NostrService.ImportedAccount.ReadOnly>(NostrService.importAccount(npub))
                assertEquals(pubkey, readOnly.pubkeyHex)
                nostrEventSigner(null, pubkey, noAmber).use { signer ->
                    assertFalse(signer.canSign)
                    assertFailsWith<IllegalStateException> {
                        signer.sign(EventTemplate(oldEvent.createdAt, oldEvent.kind, oldEvent.tags, oldEvent.content))
                    }
                }
            }
        }

    @Test
    fun existingRustEventsRemainVerifiableAndQueryable() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val events = rustNostrEventFixtures.values.toList()
                events.forEach { assertTrue(it.verify(), "Invalid legacy event kind ${it.kind}") }
                network.storedEvents += events
                val fetched = network.client.fetchNostrEvents(network.relays, listOf(Filter(ids = events.map { it.id })))
                assertEquals(events.map { it.id }.toSet(), fetched.map { it.id }.toSet())
                fetched.forEach { assertEquals(it.id, Event.fromJson(it.toJson()).id) }
            }
        }

    @Test
    fun serviceActionsPreserveRustEventContentsAndTags() =
        runTest {
            val target = rustNostrEventFixtures.getValue("target")
            val article = rustNostrEventFixtures.getValue("article")
            val targetKey = MicroBlogKey(target.id, "nostr")
            val articleKey = MicroBlogKey(article.id, "nostr")
            val content = rustNostrEventFixtures.getValue("note").content
            val key = rustNostrKeyFixtures.first()
            val pubkey = key.getValue("pubkey").jsonPrimitive.content
            val nsec = key.getValue("nsec").jsonPrimitive.content
            val actions =
                listOf<Pair<String, suspend NostrService.() -> Unit>>(
                    "note" to { composeNote(content, contentWarning = "warning") },
                    "reply" to { composeReply(targetKey, content) },
                    "quote" to { composeQuote(targetKey, content) },
                    "follow" to { follow(target.pubKey) },
                    "unfollow" to { unfollow(target.pubKey) },
                    "block" to { block(target.pubKey) },
                    "unblock" to { unblock(target.pubKey) },
                    "mute" to { mute(target.pubKey) },
                    "unmute" to { unmute(target.pubKey) },
                    "repost" to { repost(targetKey) },
                    "genericRepost" to { repost(articleKey) },
                    "reaction" to { react(targetKey) },
                    "genericReaction" to { react(articleKey) },
                    "report" to { report(targetKey) },
                    "delete" to { deleteStatus(targetKey) },
                )
            for ((name, action) in actions) {
                QuartzTestRelays(this).use { network ->
                    network.storedEvents += listOf(target, article)
                    if (name.startsWith("un")) network.storedEvents += rustNostrEventFixtures.getValue(name.removePrefix("un"))
                    val published = mutableListOf<QuartzEvent>()
                    network.onPublished = { published += it }
                    NostrService(
                        cache =
                            object : NostrCache {
                                override suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile> = emptyMap()

                                override suspend fun getPost(
                                    accountKey: MicroBlogKey,
                                    statusKey: MicroBlogKey,
                                ): UiTimelineV2.Post? = null
                            },
                        accountKey = MicroBlogKey(pubkey, "nostr"),
                        credential = JSON.decodeFromString<NostrCredential>("""{"pubkeyHex":"$pubkey","nsec":"$nsec"}"""),
                        amberSignerBridge = noAmber,
                        initialRelays = network.relays.map { it.url },
                        suppliedClient = network.client,
                    ).use { it.action() }
                    val actual = published.distinctBy { it.id }.single()
                    val expected = rustNostrEventFixtures.getValue(name)
                    assertEquals(expected.pubKey, actual.pubKey, "$name author")
                    assertEquals(expected.kind, actual.kind, "$name kind")
                    assertTrue(actual.verify(), "$name signature")
                    if (name.endsWith("Repost") || name == "repost") {
                        assertEquals(JSON.parseToJsonElement(expected.content), JSON.parseToJsonElement(actual.content), "$name content")
                    } else {
                        assertEquals(expected.content, actual.content, "$name content")
                    }

                    fun tags(event: QuartzEvent) =
                        event.tags
                            .map { if (event.kind in listOf(6, 16) && it.first() == "e") it.take(2) else it.toList() }
                            .sortedBy { it.joinToString("\u0000") }
                    assertEquals(tags(expected), tags(actual), "$name tags")
                }
            }
        }
}
