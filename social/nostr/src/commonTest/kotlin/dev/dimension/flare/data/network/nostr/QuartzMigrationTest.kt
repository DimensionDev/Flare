package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.NostrSignerCredential
import dev.dimension.flare.data.platform.effectiveSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QuartzMigrationTest {
    private val secret = "0".repeat(63) + "1"
    private val pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    private val noAmber = UnsupportedAmberSignerBridge("Unavailable")

    private fun localSigner(key: String = secret) = NostrSignerInternal(KeyPair(key.hexToByteArray()))

    private fun template(content: String = "迁移\n\"quoted\"") =
        EventTemplate<QuartzEvent>(1700000000, 1, arrayOf(arrayOf("t", "nostr")), content)

    @Test
    fun legacyCredentialsKeepIdentityAndCanonicalEventId() =
        runTest {
            val nsec = secret.hexToByteArray().toNsec()
            val legacy =
                JSON.decodeFromString<NostrCredential>(
                    """
                    {"pubkeyHex":"$pubkey","nsec":"$nsec","relays":["wss://relay.example"]}
                    """.trimIndent(),
                )
            val imported = NostrService.importAccount(nsec)
            assertEquals(pubkey, imported.pubkeyHex)
            assertEquals(nsec, NostrService.exportAccount(legacy).let { assertIs<NostrService.ImportedAccount.LocalKey>(it).nsec })
            nostrEventSigner(legacy.effectiveSigner, pubkey, noAmber).use {
                val signed = it.sign(template())
                // SHA-256 of the canonical NIP-01 JSON, computed independently of Quartz.
                assertEquals("5968247a7ccac242db5141680db385619e782e2b9722a735028c06c8a27b61fe", signed.id)
                assertTrue(signed.verify())
            }
        }

    @Test
    fun rejectsInvalidPrivateKeysAndTrailingKeyInput() =
        runTest {
            assertFailsWith<Exception> { NostrService.importAccount("0".repeat(64)) }
            assertFailsWith<Exception> { NostrService.importAccount("f".repeat(64)) }
            assertFailsWith<Exception> { NostrService.importAccount(secret.hexToByteArray().toNsec() + "suffix") }
        }

    @Test
    fun amberMustSignTheRequestedEventForTheSavedAccount() =
        runTest {
            var modifyContent = false
            val bridge =
                object : AmberSignerBridge {
                    override fun isAvailable() = true

                    override suspend fun connect(): AmberConnection = error("Unexpected reauthorization")

                    override suspend fun getPublicKey(credential: NostrSignerCredential.Amber) = pubkey

                    override suspend fun signEvent(
                        credential: NostrSignerCredential.Amber,
                        unsignedEventJson: String,
                    ): String {
                        val json = JSON.parseToJsonElement(unsignedEventJson).jsonObject
                        assertEquals(pubkey, json["pubkey"]?.jsonPrimitive?.content)
                        assertEquals("amber.package", credential.packageName)
                        val event = EventTemplate.fromJson(unsignedEventJson)
                        return localSigner().sign(if (modifyContent) template("changed by signer") else event).toJson()
                    }
                }
            nostrEventSigner(NostrSignerCredential.Amber(pubkey, "amber.package"), pubkey, bridge).use { signer ->
                assertEquals(template().content, signer.sign(template()).content)
                modifyContent = true
                assertFailsWith<IllegalArgumentException> { signer.sign(template()) }
            }
        }

    @Test
    fun fetchDeduplicatesAndRejectsInvalidOrUnrequestedEvents() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val valid = localSigner().sign(template())
                val invalid = QuartzEvent.fromJson(valid.toJson().replace("nostr", "forged"))
                val unrelated = localSigner("2".repeat(64)).sign(template("unrequested author"))
                network.storedEvents += listOf(invalid, valid, unrelated, valid)
                val events = network.client.fetchNostrEvents(network.relays, listOf(Filter(authors = listOf(pubkey), kinds = listOf(1))))
                assertEquals(listOf(valid.id), events.map { it.id })
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }

    @Test
    fun timeoutKeepsReceivedEventsAndClosesSubscription() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.endStoredEvents = false
                val event = localSigner().sign(template())
                network.storedEvents += event
                assertEquals(
                    listOf(event.id),
                    network.client
                        .fetchNostrEvents(
                            network.relays,
                            listOf(Filter(kinds = listOf(1))),
                            timeout = 1000.milliseconds,
                        ).map { it.id },
                )
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }

    @Test
    fun authenticatesConfiguredRelaysAndRetriesTheirSubscriptions() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.requireAuthentication = true
                val event = localSigner().sign(template())
                network.storedEvents += event
                nostrEventSigner(NostrSignerCredential.LocalKey(secret), pubkey, noAmber).use { signer ->
                    network.client.authenticateNostrRelays(this, signer) { network.relays }
                    assertEquals(
                        listOf(event.id),
                        network.client.fetchNostrEvents(network.relays, listOf(Filter(kinds = listOf(1)))).map { it.id },
                    )
                    assertEquals(network.relays, network.authenticatedRelays)
                }
            }
        }

    @Test
    fun publishRequiresThreeDistinctRelayAcknowledgementsOrAllWhenFewer() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val event = localSigner().sign(template())
                network.acceptedRelays = network.relays.take(2).toSet()
                assertFailsWith<IllegalStateException> { network.client.publishNostrEvent(event, network.relays) }
                assertEquals(event.id, network.client.publishNostrEvent(event, network.acceptedRelays))
                network.acceptedRelays = network.relays.take(3).toSet()
                val second = localSigner().sign(template("second"))
                assertEquals(second.id, network.client.publishNostrEvent(second, network.relays))
            }
        }

    @Test
    fun restoredBunkerReusesTransportKeyWithoutConnectingAgain() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val bunker = localSigner("2".repeat(64))
                val transport = localSigner("3".repeat(64))
                val commands = linkedMapOf<String, String>()
                network.onPublished = { event ->
                    assertEquals(transport.pubKey, event.pubKey)
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    val method = request.getValue("method").jsonPrimitive.content
                    // A relay can replay a publish on connection; count logical NIP-46 requests by ID.
                    commands[request.getValue("id").jsonPrimitive.content] = method
                    val result =
                        when (method) {
                            "get_public_key" -> {
                                pubkey
                            }

                            "sign_event" -> {
                                localSigner()
                                    .sign(
                                        EventTemplate.fromJson(
                                            request
                                                .getValue("params")
                                                .jsonArray[0]
                                                .jsonPrimitive.content,
                                        ),
                                    ).toJson()
                            }

                            else -> {
                                error("Unexpected re-pairing: $method")
                            }
                        }
                    network.deliver(
                        NostrConnectEvent.create(
                            BunkerResponse(request.getValue("id").jsonPrimitive.content, result, null),
                            event.pubKey,
                            bunker,
                        ),
                    )
                }
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(bunker.pubKey, network.relays.take(1), "old-pairing-secret"),
                        userPubkeyHex = pubkey,
                        secret = "3".repeat(64).hexToByteArray().toNsec(),
                    )
                // Quartz remote signers process responses on Dispatchers.Default, so use its real clock here.
                withContext(Dispatchers.Default) {
                    repeat(2) {
                        NostrBunkerSession(credential, suppliedClient = network.client).use { session ->
                            assertEquals(pubkey, session.publicKey())
                            assertEquals(pubkey, session.sign(template()).pubKey)
                        }
                    }
                }
                assertEquals(listOf("get_public_key", "sign_event", "get_public_key", "sign_event"), commands.values.toList())
            }
        }

    @Test
    fun qrLoginChecksPairingSecretAndPersistsTransportIdentity() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val bunker = localSigner("2".repeat(64))
                val login = NostrQrLogin(network.relays.take(1).map { it.url }, network.client)
                val offer = assertNotNull(NostrConnectURI.parseNostrConnect(login.connectUri))
                network.onPublished = { event ->
                    assertEquals(offer.clientPubKey, event.pubKey)
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    network.deliver(
                        NostrConnectEvent.create(
                            BunkerResponse(request.getValue("id").jsonPrimitive.content, pubkey, null),
                            event.pubKey,
                            bunker,
                        ),
                    )
                }
                val pending = async(Dispatchers.Default) { login.awaitAccount() }
                // Wait until Quartz has opened the offer subscription before delivering signer replies.
                val delivery =
                    launch {
                        network.requests.receive()
                        network.deliver(
                            NostrConnectEvent.create(BunkerResponse("connect", "wrong-secret", null), offer.clientPubKey, bunker),
                        )
                        kotlinx.coroutines.delay(100)
                        assertFalse(pending.isCompleted)
                        network.deliver(NostrConnectEvent.create(BunkerResponse("connect", offer.secret, null), offer.clientPubKey, bunker))
                    }
                try {
                    val account = pending.await()
                    val saved = assertIs<NostrSignerCredential.Bunker>(account.signerCredential)
                    assertEquals(pubkey, account.pubkeyHex)
                    assertEquals(offer.clientPubKey, NostrService.importAccount(assertNotNull(saved.secret)).pubkeyHex)
                    assertEquals(bunker.pubKey, assertNotNull(NostrConnectURI.parseBunker(saved.uri)).remoteSignerPubKey)
                } finally {
                    delivery.cancelAndJoin()
                    pending.cancelAndJoin()
                    login.close()
                }
            }
        }

    @Test
    fun closingQrLoginCancelsPendingSubscription() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val login = NostrQrLogin(network.relays.take(1).map { it.url }, network.client)
                val pending = async { login.awaitAccount() }
                runCurrent()
                login.close()
                runCurrent()
                assertTrue(pending.isCancelled)
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }
}
