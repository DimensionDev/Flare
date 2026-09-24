package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

internal class NostrRelayClient(
    signer: NostrEventSigner? = null,
    relays: () -> Set<NormalizedRelayUrl> = { emptySet() },
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val httpClient = ktorClient { install(WebSockets) }
    val client: INostrClient =
        NostrClient(
            websocketBuilder =
                object : WebsocketBuilder {
                    override fun build(
                        url: NormalizedRelayUrl,
                        out: WebSocketListener,
                    ): WebSocket = NostrWebSocket(url, httpClient, out, scope)
                },
            parentScope = scope,
        )

    init {
        if (signer?.canSign == true) client.authenticateNostrRelays(scope, signer, relays)
    }

    override fun close() {
        client.close()
        scope.cancel()
        httpClient.close()
    }
}

internal fun INostrClient.authenticateNostrRelays(
    scope: CoroutineScope,
    signer: NostrEventSigner,
    relays: () -> Set<NormalizedRelayUrl>,
): RelayAuthenticator =
    RelayAuthenticator(this, scope) { relay, template, interactive ->
        if (relay !in relays() || !signer.canSign || (!interactive && !signer.canSignWithoutInteraction)) {
            emptyList()
        } else {
            val event = signer.sign(EventTemplate(template.createdAt, template.kind, template.tags, template.content))
            listOf(event as RelayAuthEvent)
        }
    }

internal fun normalizedNostrRelays(relays: List<String>): Set<NormalizedRelayUrl> =
    relays.map { requireNotNull(RelayUrlNormalizer.normalizeOrNull(it)) { "Invalid Nostr relay URL: $it" } }.toSet()

internal suspend fun INostrClient.fetchNostrEvents(
    relays: Set<NormalizedRelayUrl>,
    filters: List<Filter>,
    timeout: Duration = 1.minutes,
): List<QuartzEvent> {
    if (relays.isEmpty() || filters.isEmpty()) return emptyList()
    val queries = filters.map { it.toQuartz() }
    val events = linkedMapOf<String, QuartzEvent>()
    // Keep partial results when the hard deadline expires, including if a relay never sends EOSE.
    withTimeoutOrNull(timeout) {
        fetchAllWithHooks(
            filters = relays.associateWith { queries },
            idleTimeoutMs = timeout.inWholeMilliseconds,
        ) { _, event ->
            if (event.id !in events && queries.any { it.match(event) } && event.verify()) {
                events[event.id] = event
                true
            } else {
                false
            }
        }
    }
    return events.values.sortedWith(compareByDescending<QuartzEvent> { it.createdAt }.thenBy { it.id })
}

internal suspend fun INostrClient.publishNostrEvent(
    event: QuartzEvent,
    relays: Set<NormalizedRelayUrl>,
): String {
    require(relays.isNotEmpty()) { "No valid relay URLs available for publishing" }
    require(event.verify()) { "Cannot publish an invalid Nostr event" }
    val results = publishAndCollectResults(event, relays)
    val required = minOf(3, relays.size)
    val accepted = results.values.count { it.accepted }
    check(accepted >= required) {
        "Failed to publish event to enough relays: $accepted/$required succeeded. " +
            results.filterValues { !it.accepted }.entries.joinToString { (relay, result) -> "${relay.url}: ${result.message}" }
    }
    return event.id
}

// Quartz creates a fresh adapter for every dial. Each adapter reports at most one terminal event.
internal class NostrWebSocket(
    private val url: NormalizedRelayUrl,
    private val httpClient: HttpClient,
    private val out: WebSocketListener,
    parentScope: CoroutineScope,
) : WebSocket {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val started = MutableStateFlow(false)
    private val ended = MutableStateFlow(false)
    private val connected = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.UNLIMITED)

    override fun needsReconnect(): Boolean = !connected.value

    override fun connect() {
        if (ended.value || !started.compareAndSet(false, true)) return
        scope.launch {
            try {
                httpClient.webSocket(urlString = url.url) {
                    val session = this
                    try {
                        if (ended.value) return@webSocket
                        connected.value = true
                        out.onOpen(0, false)
                        coroutineScope {
                            val writer = launch { for (message in messages) session.send(Frame.Text(message)) }
                            try {
                                for (frame in session.incoming) {
                                    if (frame is Frame.Text && !ended.value) out.onMessage(frame.readText())
                                }
                            } finally {
                                writer.cancel()
                            }
                        }
                        val reason = session.closeReason.await()
                        if (ended.compareAndSet(false, true)) out.onClosed(reason?.code?.toInt() ?: 1000, reason?.message.orEmpty())
                    } finally {
                        session.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (ended.compareAndSet(false, true)) out.onFailure(e, null, null)
            } finally {
                connected.value = false
                messages.close()
                scope.cancel()
            }
        }
    }

    override fun disconnect() {
        ended.value = true
        connected.value = false
        messages.close()
        scope.cancel()
    }

    override fun send(msg: String): Boolean = connected.value && !ended.value && messages.trySend(msg).isSuccess
}
