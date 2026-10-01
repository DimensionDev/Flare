package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.NostrSignerCredential
import dev.dimension.flare.data.platform.effectiveSigner
import dev.dimension.flare.data.platform.normalized
import dev.dimension.flare.data.platform.signerStableId
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NostrSignerContractTest {
    private val me = nostrTestSigner().pubKey
    private val other = nostrTestSigner(1).pubKey
    private val nsec =
        rustNostrKeyFixtures
            .first()
            .getValue("nsec")
            .jsonPrimitive.content
    private val noAmber = UnsupportedAmberSignerBridge("Unavailable")
    private val template = EventTemplate<QuartzEvent>(1700000000, 1, arrayOf(arrayOf("t", "nostr")), "original")

    @Test
    fun everyCredentialRoundTripsWithoutLosingIdentityRelaysOrMediaServer() {
        val key = MicroBlogKey(me, "nostr")
        val signers =
            listOf(
                null,
                NostrSignerCredential.LocalKey(nsec),
                NostrSignerCredential.Amber(me, "amber.package", other),
                NostrSignerCredential.Bunker("bunker://$other?relay=wss%3A%2F%2Frelay.example", me, "wss://relay.example", nsec),
            )
        for (signer in signers) {
            val credential =
                NostrCredential(
                    relays = listOf("wss://relay.example"),
                    mediaServerUrl = "https://upload.example/",
                    signer = signer,
                ).normalized(key)
            val restored = JSON.decodeFromString<NostrCredential>(JSON.encodeToString(credential))
            assertEquals(credential, restored)
            assertEquals(me, restored.pubkeyHex)
            assertEquals(credential.signerStableId(key), restored.copy(relays = emptyList()).signerStableId(key))
            if (signer !is NostrSignerCredential.LocalKey) {
                assertFailsWith<IllegalArgumentException> {
                    NostrService.exportAccount(
                        restored,
                    )
                }
            }
        }
        val migrated = NostrCredential(legacyNsec = nsec).normalized(key)
        assertEquals(NostrSignerCredential.LocalKey(nsec), migrated.effectiveSigner)
        val explicit = NostrSignerCredential.Amber(me)
        assertEquals(explicit, migrated.copy(signer = explicit).effectiveSigner)
    }

    @Test
    fun publicKeyImportsAcceptNprofileButRejectCorruptOrTrailingIdentifiers() =
        runTest {
            val nprofile = NProfile.create(me, relay = null)
            assertEquals(me, NostrService.importAccount("nostr:$nprofile").pubkeyHex)
            for (value in listOf("", "not a key", "npub1invalid", nostrBech32PublicKey(me) + "suffix", nprofile + "suffix")) {
                assertFailsWith<Exception> { NostrService.importAccount(value) }
                assertNull(parsePublicKeyHex(value))
            }
        }

    @Test
    fun amberRejectsChangesToEverySignedFieldAndInvalidSignatures() =
        runTest {
            val variants: List<suspend () -> QuartzEvent> =
                listOf(
                    { nostrTestSigner().sign(EventTemplate(template.createdAt + 1, template.kind, template.tags, template.content)) },
                    { nostrTestSigner().sign(EventTemplate(template.createdAt, 7, template.tags, template.content)) },
                    { nostrTestSigner().sign(EventTemplate(template.createdAt, template.kind, emptyArray(), template.content)) },
                    { nostrTestSigner().sign(EventTemplate(template.createdAt, template.kind, template.tags, "different")) },
                    { nostrTestSigner(1).sign(template) },
                    { nostrTestSigner().sign(template).let { QuartzEvent.fromJson(it.toJson().replace(it.sig, "0".repeat(128))) } },
                )
            for (variant in variants) {
                val bridge =
                    object : AmberSignerBridge {
                        override fun isAvailable() = true

                        override suspend fun connect(): AmberConnection = error("Must reuse saved authorization")

                        override suspend fun getPublicKey(credential: NostrSignerCredential.Amber) = me

                        override suspend fun signEvent(
                            credential: NostrSignerCredential.Amber,
                            unsignedEventJson: String,
                        ) = variant().toJson()
                    }
                nostrEventSigner(NostrSignerCredential.Amber(me), me, bridge).use { signer ->
                    assertTrue(signer.canSign)
                    assertFalse(signer.canSignWithoutInteraction)
                    assertFailsWith<IllegalArgumentException> { signer.sign(template) }
                }
            }
        }

    @Test
    fun unavailableAmberAndMismatchedLocalKeyCannotSignForTheAccount() =
        runTest {
            nostrEventSigner(NostrSignerCredential.Amber(me), me, noAmber).use {
                assertFalse(it.canSign)
                assertFailsWith<IllegalStateException> { it.sign(template) }
            }
            nostrEventSigner(NostrSignerCredential.LocalKey(nsec), other, noAmber).use {
                assertFailsWith<IllegalArgumentException> { it.sign(template) }
            }
        }

    @Test
    fun relayAuthOnlyUsesConfiguredAccountsAndDoesNotRepromptExternalSigners() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val allowed = network.relays.first()
                var calls = 0
                val signer =
                    object : NostrEventSigner {
                        override val canSign = true
                        override val canSignWithoutInteraction = false

                        override suspend fun sign(template: EventTemplate<out QuartzEvent>): QuartzEvent {
                            calls++
                            return nostrTestSigner().sign(template)
                        }
                    }
                val auth = network.client.authenticateNostrRelays(this, signer) { setOf(allowed) }
                val request = RelayAuthEvent.build(allowed, "challenge")
                assertTrue(auth.signWithAllLoggedInUsers(network.relays.last(), request, true).isEmpty())
                assertTrue(auth.signWithAllLoggedInUsers(allowed, request, false).isEmpty())
                assertEquals(0, calls)
                val event = auth.signWithAllLoggedInUsers(allowed, request, true).single()
                assertTrue(event.verify())
                assertEquals(me, event.pubKey)
                assertEquals(1, calls)
            }
        }

    @Test
    fun fetchEnforcesKindsTagsTimeAndIdFiltersAndCancellationCleansSubscriptions() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val tags = listOf(listOf("p", other))
                val valid = nostrTestEvent(time = 10, tags = tags)
                network.storedEvents +=
                    listOf(
                        valid,
                        nostrTestEvent(time = 9, tags = tags),
                        nostrTestEvent(time = 11, tags = tags),
                        nostrTestEvent(time = 10, kind = 7, tags = tags),
                        nostrTestEvent(time = 10, tags = listOf(listOf("p", me))),
                    )
                val filter = Filter(authors = listOf(me), kinds = listOf(1), tags = mapOf("p" to listOf(other)), since = 10, until = 10)
                assertEquals(listOf(valid.id), network.client.fetchNostrEvents(network.relays, listOf(filter)).map { it.id })
                assertEquals(
                    listOf(valid.id),
                    network.client.fetchNostrEvents(network.relays, listOf(Filter(ids = listOf(valid.id)))).map { it.id },
                )
                assertTrue(network.client.fetchNostrEvents(emptySet(), listOf(filter)).isEmpty())
                assertTrue(network.client.fetchNostrEvents(network.relays, emptyList()).isEmpty())
                network.endStoredEvents = false
                val fetch = async { network.client.fetchNostrEvents(network.relays, listOf(filter)) }
                runCurrent()
                fetch.cancelAndJoin()
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }

    @Test
    fun publishingRejectsInvalidEventsAndAnEmptyRelaySetBeforeSending() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val valid = nostrTestEvent()
                val invalid = QuartzEvent.fromJson(valid.toJson().replace(valid.sig, "0".repeat(128)))
                assertFailsWith<IllegalArgumentException> { network.client.publishNostrEvent(valid, emptySet()) }
                assertFailsWith<IllegalArgumentException> { network.client.publishNostrEvent(invalid, network.relays) }
                assertTrue(network.published.isEmpty())
            }
        }

    @Test
    fun initialBunkerPairingSendsUriSecretAndResolvesTheUsersIdentity() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val bunker = nostrTestSigner(1)
                val transport = nostrTestSigner(2)
                val methods = linkedMapOf<String, String>()
                network.onPublished = { event ->
                    assertEquals(transport.pubKey, event.pubKey)
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    val method = request.getValue("method").jsonPrimitive.content
                    val id = request.getValue("id").jsonPrimitive.content
                    methods[id] = method
                    val result =
                        when (method) {
                            "connect" -> {
                                val params = request.getValue("params").jsonArray
                                assertEquals(bunker.pubKey, params[0].jsonPrimitive.content)
                                assertEquals("pairing-secret", params[1].jsonPrimitive.content)
                                "ack"
                            }

                            "get_public_key" -> {
                                me
                            }

                            else -> {
                                error("Unexpected command $method")
                            }
                        }
                    network.deliver(NostrConnectEvent.create(BunkerResponse(id, result, null), event.pubKey, bunker))
                }
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(other, network.relays.take(1), "pairing-secret"),
                        secret = "2".repeat(64),
                    )
                withContext(Dispatchers.Default) {
                    NostrBunkerSession(credential, suppliedClient = network.client).use { assertEquals(me, it.publicKey(connect = true)) }
                }
                assertEquals(listOf("connect", "get_public_key"), methods.values.toList())
                assertEquals(0, network.client.registrySizes().liveRequests)
            }
        }

    @Test
    fun bunkerRejectsIdentityChangesAndSignerRefusals() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val bunker = nostrTestSigner(1)
                var refusal = false
                network.onPublished = { event ->
                    val request = JSON.parseToJsonElement(bunker.decrypt(event.content, event.pubKey)).jsonObject
                    network.deliver(
                        NostrConnectEvent.create(
                            BunkerResponse(
                                request.getValue("id").jsonPrimitive.content,
                                if (refusal) null else other,
                                if (refusal) "denied" else null,
                            ),
                            event.pubKey,
                            bunker,
                        ),
                    )
                }
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(other, network.relays.take(1), null),
                        me,
                        secret = nsec,
                    )
                withContext(Dispatchers.Default) {
                    NostrBunkerSession(credential, suppliedClient = network.client).use {
                        assertFailsWith<IllegalArgumentException> { it.publicKey() }
                    }
                    refusal = true
                    NostrBunkerSession(credential, suppliedClient = network.client).use {
                        assertFailsWith<SignerExceptions.ManuallyUnauthorizedException> { it.sign(template) }
                    }
                }
            }
        }

    @Test
    fun bunkerAndQrDeadlinesReleaseSubscriptionsAndClosedBunkerCannotRestart() =
        runTest {
            QuartzTestRelays(this).use { network ->
                val credential =
                    NostrSignerCredential.Bunker(
                        NostrConnectURI.buildBunker(other, network.relays.take(1), null),
                        me,
                        secret = nsec,
                    )
                val session = NostrBunkerSession(credential, suppliedClient = network.client)
                assertFailsWith<TimeoutCancellationException> { session.publicKey() }
                session.close()
                assertFailsWith<IllegalStateException> { session.sign(template) }
                assertEquals(0, network.client.registrySizes().liveRequests)
                NostrQrLogin(network.relays.map { it.url }, network.client).use { login ->
                    assertFailsWith<TimeoutCancellationException> { login.awaitAccount() }
                }
                assertEquals(0, network.client.registrySizes().liveRequests)
                assertTrue(testScheduler.currentTime >= 130.seconds.inWholeMilliseconds)
            }
        }
}
