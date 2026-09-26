package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import dev.dimension.flare.common.JSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

// Exercises Quartz's real relay pool without opening sockets or publishing to public relays.
internal class QuartzTestRelays(
    private val scope: CoroutineScope,
) : AutoCloseable {
    val relays = normalizedNostrRelays((1..4).map { "wss://relay$it.example" })
    val requests = Channel<Unit>(Channel.UNLIMITED)
    val storedEvents = mutableListOf<QuartzEvent>()
    var endStoredEvents = true
    var acceptedRelays = relays
    var requireAuthentication = false
    val authenticatedRelays = mutableSetOf<NormalizedRelayUrl>()
    var onPublished: suspend (QuartzEvent) -> Unit = {}
    private val sockets = mutableListOf<Socket>()
    val client =
        NostrClient(
            object : WebsocketBuilder {
                override fun build(
                    url: NormalizedRelayUrl,
                    out: WebSocketListener,
                ): WebSocket = Socket(url, out).also { sockets += it }
            },
            scope,
        )

    suspend fun deliver(event: QuartzEvent) {
        for (socket in sockets.toList()) {
            for ((id, filters) in socket.subscriptions.toMap()) {
                if (filters.any { filter ->
                        filter["#p"]?.jsonArray?.any { pubkey ->
                            event.tags.any { it.firstOrNull() == "p" && it.getOrNull(1) == pubkey.jsonPrimitive.content }
                        } != false
                    }
                ) {
                    socket.event(id, event)
                }
            }
        }
    }

    override fun close() = client.close()

    private inner class Socket(
        val url: NormalizedRelayUrl,
        val out: WebSocketListener,
    ) : WebSocket {
        var connected = false
        val subscriptions = mutableMapOf<String, List<JsonObject>>()

        override fun needsReconnect(): Boolean = !connected

        override fun connect() {
            connected = true
            out.onOpen(0, false)
            if (requireAuthentication) scope.launch { out.onMessage("[\"AUTH\",\"test-challenge\"]") }
        }

        override fun disconnect() {
            connected = false
            subscriptions.clear()
        }

        suspend fun event(
            id: String,
            event: QuartzEvent,
        ) {
            out.onMessage(JsonArray(listOf(JsonPrimitive("EVENT"), JsonPrimitive(id), JSON.parseToJsonElement(event.toJson()))).toString())
        }

        override fun send(msg: String): Boolean {
            if (!connected) return false
            scope.launch {
                val frame = JSON.parseToJsonElement(msg).jsonArray
                when (frame[0].jsonPrimitive.content) {
                    "REQ" -> {
                        val id = frame[1].jsonPrimitive.content
                        subscriptions[id] = frame.drop(2).map { it.jsonObject }
                        if (requireAuthentication && url !in authenticatedRelays) {
                            out.onMessage("[\"CLOSED\",\"$id\",\"auth-required: authenticate first\"]")
                        } else {
                            storedEvents.toList().forEach { event(id, it) }
                            if (endStoredEvents) out.onMessage("[\"EOSE\",\"$id\"]")
                        }
                        requests.trySend(Unit)
                    }

                    "CLOSE" -> {
                        subscriptions.remove(frame[1].jsonPrimitive.content)
                    }

                    "EVENT" -> {
                        val event = QuartzEvent.fromJson(frame[1].toString())
                        out.onMessage("[\"OK\",\"${event.id}\",${url in acceptedRelays},\"test relay\"]")
                        onPublished(event)
                    }

                    "AUTH" -> {
                        val auth = QuartzEvent.fromJson(frame[1].toString())
                        check(auth.kind == 22242 && auth.verify())
                        check(auth.tags.any { it.contentEquals(arrayOf("challenge", "test-challenge")) })
                        check(auth.tags.any { it.contentEquals(arrayOf("relay", url.url)) })
                        authenticatedRelays += url
                        out.onMessage("[\"OK\",\"${auth.id}\",true,\"\"]")
                    }
                }
            }
            return true
        }
    }
}
