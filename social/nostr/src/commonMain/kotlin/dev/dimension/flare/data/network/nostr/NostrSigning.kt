package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.platform.NostrSignerCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter as QuartzFilter

internal fun parseNostrSecret(raw: String): ByteArray? {
    val value = raw.removePrefix("nostr:").trim()
    return when {
        value.startsWith("nsec1", ignoreCase = true) -> {
            value.bechToBytes().also { require(it.size == 32) { "Invalid Nostr private key" } }
        }

        value.length == 64 && value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } -> {
            value.hexToByteArray()
        }

        else -> {
            null
        }
    }?.also { KeyPair(it) }
}

internal interface NostrEventSigner : AutoCloseable {
    val canSign: Boolean
    val canSignWithoutInteraction: Boolean

    suspend fun sign(template: EventTemplate<out QuartzEvent>): QuartzEvent

    override fun close() = Unit
}

internal fun nostrEventSigner(
    credential: NostrSignerCredential?,
    pubkeyHex: String,
    amber: AmberSignerBridge,
): NostrEventSigner {
    val local =
        (credential as? NostrSignerCredential.LocalKey)?.let {
            NostrSignerInternal(KeyPair(requireNotNull(parseNostrSecret(it.nsec))))
        }
    val bunker = (credential as? NostrSignerCredential.Bunker)?.let { NostrBunkerSession(it, pubkeyHex) }
    return object : NostrEventSigner {
        override val canSignWithoutInteraction: Boolean = credential is NostrSignerCredential.LocalKey

        override val canSign: Boolean
            get() =
                when (credential) {
                    null -> false
                    is NostrSignerCredential.Amber -> amber.isAvailable()
                    else -> true
                }

        override suspend fun sign(template: EventTemplate<out QuartzEvent>): QuartzEvent {
            check(canSign) { "This Nostr account is read-only. Connect a signer to publish events." }
            val signed =
                when (credential) {
                    is NostrSignerCredential.LocalKey -> {
                        requireNotNull(local).sign(template)
                    }

                    is NostrSignerCredential.Bunker -> {
                        requireNotNull(bunker).sign(template)
                    }

                    is NostrSignerCredential.Amber -> {
                        val unsigned =
                            buildJsonObject {
                                JSON.parseToJsonElement(template.toJson()).jsonObject.forEach { (key, value) -> put(key, value) }
                                put("pubkey", pubkeyHex)
                                put("id", EventHasher.hashId(pubkeyHex, template.createdAt, template.kind, template.tags, template.content))
                            }
                        QuartzEvent.fromJson(amber.signEvent(credential, unsigned.toString()))
                    }

                    null -> {
                        error("No Nostr signer")
                    }
                }
            require(
                signed.pubKey == pubkeyHex && signed.createdAt == template.createdAt &&
                    signed.kind == template.kind && signed.content == template.content &&
                    signed.tags.contentDeepEquals(template.tags) && signed.verify(),
            ) {
                "Signer returned an invalid or different Nostr event"
            }
            return signed
        }

        override fun close() {
            bunker?.close()
        }
    }
}

internal class NostrBunkerSession(
    private val credential: NostrSignerCredential.Bunker,
    private val expectedPubkey: String? = credential.userPubkeyHex,
    suppliedClient: INostrClient? = null,
) : AutoCloseable {
    private val localSigner = NostrSignerInternal(KeyPair(requireNotNull(parseNostrSecret(requireNotNull(credential.secret)))))
    private val transport = if (suppliedClient == null) NostrRelayClient() else null
    private val client = suppliedClient ?: requireNotNull(transport).client
    private val mutex = Mutex()
    private val remote = MutableStateFlow<NostrSignerRemote?>(null)
    private val closed = MutableStateFlow(false)

    private suspend fun signer(): NostrSignerRemote =
        mutex.withLock {
            check(!closed.value) { "Bunker session closed" }
            remote.value ?: run {
                val uri = credential.uri.substringBefore("://").lowercase() + "://" + credential.uri.substringAfter("://")
                val bunker = NostrConnectURI.parseBunker(uri)
                val relays =
                    bunker?.relays ?: normalizedNostrRelays(
                        io.ktor.http
                            .Url(uri)
                            .parameters
                            .getAll("relay")
                            .orEmpty(),
                    )
                require(relays.isNotEmpty()) { "Bunker requires at least one relay" }
                val remotePubkey =
                    bunker?.remoteSignerPubKey ?: run {
                        // Older credentials could persist the client offer instead of the resolved bunker URI.
                        val offer = io.ktor.http.Url(uri)
                        require(uri.startsWith("nostrconnect://") && offer.host == localSigner.pubKey) {
                            "NostrConnect URI does not match the saved client key"
                        }
                        awaitNostrConnectSigner(client, localSigner, relays, offer.parameters["secret"], expectedPubkey = expectedPubkey)
                    }
                NostrSignerRemote(
                    signer = localSigner,
                    remotePubkey = remotePubkey,
                    relays = relays,
                    client = client,
                    // credential.secret is the persisted CLIENT private key, not the bunker's pairing secret.
                    secret = bunker?.secret,
                ).also {
                    expectedPubkey?.let(it::bindUserPubkey)
                    it.openSubscription()
                    remote.value = it
                    if (closed.value) {
                        it.closeSubscription()
                        error("Bunker session closed")
                    }
                }
            }
        }

    suspend fun publicKey(connect: Boolean = false): String =
        withTimeout(10.seconds) {
            val signer = signer()
            if (connect) signer.connect()
            signer.getPublicKey().also { require(expectedPubkey == null || it == expectedPubkey) { "Bunker account identity changed" } }
        }

    suspend fun sign(template: EventTemplate<out QuartzEvent>): QuartzEvent = withTimeout(10.seconds) { signer().sign(template) }

    override fun close() {
        closed.value = true
        remote.value?.closeSubscription()
        transport?.close()
    }
}

internal class NostrQrLogin(
    relays: List<String>,
    suppliedClient: INostrClient? = null,
) : NostrService.Companion.PendingQrLogin {
    private val keys = KeyPair()
    private val localSigner = NostrSignerInternal(keys)
    private val relayUrls = normalizedNostrRelays(relays)
    private val pairingSecret = Uuid.random().toString()
    private val createdAt = Clock.System.now().epochSeconds
    private val transport = if (suppliedClient == null) NostrRelayClient() else null
    private val client = suppliedClient ?: requireNotNull(transport).client
    private val closed = CompletableDeferred<Unit>()
    override val connectUri: String =
        NostrConnectURI.buildNostrConnect(
            clientPubKey = keys.pubKey.toHexKey(),
            relays = relayUrls,
            secret = pairingSecret,
            name = "Flare",
        )

    override suspend fun awaitAccount(): NostrService.ImportedAccount.RemoteSigner =
        coroutineScope {
            val account = async { awaitPairing() }
            try {
                select {
                    closed.onAwait { throw CancellationException("NostrConnect session closed") }
                    account.onAwait { it }
                }
            } finally {
                account.cancel()
            }
        }

    private suspend fun awaitPairing(): NostrService.ImportedAccount.RemoteSigner =
        withTimeout(2.minutes) {
            val remotePubkey = awaitNostrConnectSigner(client, localSigner, relayUrls, pairingSecret, since = createdAt)
            val credential =
                NostrSignerCredential.Bunker(
                    uri = NostrConnectURI.buildBunker(remotePubkey, relayUrls, secret = null),
                    signerRelay = relayUrls.first().url,
                    secret = requireNotNull(keys.privKey).toNsec(),
                )
            NostrBunkerSession(credential, suppliedClient = client).use { session ->
                val pubkey = session.publicKey()
                NostrService.ImportedAccount.RemoteSigner(pubkey, nostrBech32PublicKey(pubkey), credential.copy(userPubkeyHex = pubkey))
            }
        }

    override fun close() {
        closed.complete(Unit)
        transport?.close()
    }
}

private suspend fun awaitNostrConnectSigner(
    client: INostrClient,
    signer: NostrSignerInternal,
    relays: Set<NormalizedRelayUrl>,
    secret: String?,
    since: Long? = null,
    expectedPubkey: String? = null,
): String {
    val incoming = Channel<QuartzEvent>(Channel.UNLIMITED)
    val subId = Uuid.random().toString()
    val filter = QuartzFilter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(signer.pubKey)), since = since)
    try {
        client.subscribe(
            subId,
            relays.associateWith { listOf(filter) },
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: QuartzEvent,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<QuartzFilter>?,
                ) {
                    incoming.trySend(event)
                }
            },
        )
        for (event in incoming) {
            if (!filter.match(event) || !event.verify()) continue
            try {
                val response = JSON.parseToJsonElement(signer.decrypt(event.content, event.pubKey)).jsonObject
                val result = response["result"]?.jsonPrimitive?.content
                val error = response["error"]
                if (error != null && error != JsonNull && error.jsonPrimitive.content.isNotEmpty()) continue
                if ((secret != null && result == secret) || (secret == null && event.pubKey == expectedPubkey && result == "ack")) {
                    return event.pubKey
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Other relay traffic cannot complete this pairing session.
            }
        }
        error("NostrConnect session closed")
    } finally {
        client.unsubscribe(subId)
        incoming.close()
    }
}
