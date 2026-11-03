package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.platform.NostrSignerCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NostrBunkerCompatibilityTest {
    @Test
    fun restoredBunkerIncludesThePubkeyRequiredByLegacyRustSigners() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val user = nostrTestSigner()
                val bunker = nostrTestSigner(1)
                var requestedPubkey: String? = null
                network.onPublished = { event ->
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    val unsigned =
                        request
                            .getValue("params")
                            .jsonArray[0]
                            .jsonPrimitive.content
                    requestedPubkey =
                        JSON
                            .parseToJsonElement(unsigned)
                            .jsonObject["pubkey"]
                            ?.jsonPrimitive
                            ?.content
                    network.deliver(
                        rustBunkerResponse(
                            request.getValue("id").jsonPrimitive.content,
                            user.sign(EventTemplate.fromJson(unsigned)).toJson(),
                            event.pubKey,
                            bunker,
                        ),
                    )
                }
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(bunker.pubKey, network.relays.take(1), null),
                        user.pubKey,
                        secret = "2".repeat(64),
                    )
                withContext(Dispatchers.Default) {
                    NostrBunkerSession(credential, suppliedClient = network.client).use {
                        it.sign(EventTemplate<QuartzEvent>(1700000000, 1, emptyArray(), "migration probe"))
                    }
                }
                assertEquals(user.pubKey, requestedPubkey, "Rust NIP-46 sign_event parser requires pubkey in UnsignedEvent")
            }
        }

    @Test
    fun restoredUppercaseBunkerUriKeepsTheCanonicalRecipient() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val user = nostrTestSigner()
                val bunker = nostrTestSigner(1)
                var requestedRecipient: String? = null
                network.onPublished = { event ->
                    requestedRecipient = event.tags.single { it.first() == "p" }[1]
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    val unsigned =
                        request
                            .getValue("params")
                            .jsonArray[0]
                            .jsonPrimitive.content
                    network.deliver(
                        NostrConnectEvent.create(
                            BunkerResponse(
                                request.getValue("id").jsonPrimitive.content,
                                user.sign(EventTemplate.fromJson(unsigned)).toJson(),
                                null,
                            ),
                            event.pubKey,
                            bunker,
                        ),
                    )
                }
                val credential =
                    NostrSignerCredential.Bunker(
                        "BUNKER://${bunker.pubKey.uppercase()}?relay=WSS%3A%2F%2Frelay1.example",
                        user.pubKey,
                        secret = "2".repeat(64),
                    )
                withContext(Dispatchers.Default) {
                    NostrBunkerSession(credential, suppliedClient = network.client).use {
                        it.sign(EventTemplate<QuartzEvent>(1700000000, 1, emptyArray(), "migration probe"))
                    }
                }
                assertEquals(bunker.pubKey, requestedRecipient, "Legacy Rust URI parsing canonicalized the recipient before publishing")
            }
        }

    @Test
    fun signingTimeoutAndCancellationReleaseTheirResponseSubscriptions() =
        runTest {
            QuartzTestRelays(backgroundScope).use { network ->
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(nostrTestSigner(1).pubKey, network.relays.take(1), null),
                        nostrTestSigner().pubKey,
                        secret = "2".repeat(64),
                    )
                val template = EventTemplate<QuartzEvent>(1700000000, 1, emptyArray(), "no response")
                NostrBunkerSession(credential, suppliedClient = network.client).use { session ->
                    assertFailsWith<TimeoutCancellationException> { session.sign(template) }
                    assertEquals(1, network.client.registrySizes().liveRequests, "Only the reusable signer session remains")
                    val pending = async { session.sign(template) }
                    runCurrent()
                    pending.cancelAndJoin()
                    assertTrue(pending.isCancelled)
                    assertEquals(1, network.client.registrySizes().liveRequests)
                }
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }
}
