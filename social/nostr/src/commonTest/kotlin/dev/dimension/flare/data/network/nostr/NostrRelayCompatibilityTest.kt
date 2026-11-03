package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter as QuartzFilter

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NostrRelayCompatibilityTest {
    @Test
    fun firstQueryDoesNotCancelAHandshakeStillInProgress() =
        runTest {
            var handshakes = 0
            var cancelled = 0
            val engine =
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = StandardTestDispatcher(testScheduler)
                        addHandler {
                            handshakes++
                            try {
                                // A TLS/WebSocket handshake which takes longer than 200 ms.
                                awaitCancellation()
                            } finally {
                                cancelled++
                            }
                        }
                    },
                )
            HttpClient(engine) { install(WebSockets) }.use { http ->
                val client =
                    NostrClient(
                        object : WebsocketBuilder {
                            override fun build(
                                url: NormalizedRelayUrl,
                                out: WebSocketListener,
                            ): WebSocket = NostrWebSocket(url, http, out, backgroundScope)
                        },
                        backgroundScope,
                    )
                try {
                    val relay = NormalizedRelayUrl("wss://relay.example/")
                    client.subscribe("slow-handshake", mapOf(relay to listOf(QuartzFilter(kinds = listOf(1)))))
                    runCurrent()
                    advanceTimeBy(250)
                    runCurrent()
                    assertEquals(1, handshakes, "A pending handshake must not be restarted by the subscription's reconnect pass")
                    assertEquals(0, cancelled)
                } finally {
                    client.close()
                }
            }
            engine.close()
        }

    @Test
    fun publicationWaitsForNip42AuthenticationAndFinalAcknowledgement() =
        runTest {
            QuartzTestRelays(backgroundScope).use { network ->
                network.requireAuthentication = true
                network.acceptedRelays = network.relays.take(3).toSet()
                val auth = network.client.authenticateNostrRelays(backgroundScope, delayedSigner()) { network.relays }
                try {
                    val event = nostrTestEvent()
                    assertEquals(event.id, network.client.publishNostrEvent(event, network.relays))
                    assertEquals(network.acceptedRelays, network.acceptedPublications.map { it.first }.toSet())
                    assertEquals(setOf(event.id), network.acceptedPublications.map { it.second.id }.toSet())
                } finally {
                    auth.destroy()
                }
            }
        }

    @Test
    fun failedAuthenticationAndInsufficientPostAuthAcknowledgementsStillFail() =
        runTest {
            for (acceptAuth in listOf(false, true)) {
                QuartzTestRelays(backgroundScope).use { network ->
                    network.requireAuthentication = true
                    network.acceptAuthentication = acceptAuth
                    network.acceptedRelays = network.relays.take(2).toSet()
                    val auth = network.client.authenticateNostrRelays(backgroundScope, delayedSigner()) { network.relays }
                    try {
                        assertFailsWith<IllegalStateException> {
                            network.client.publishNostrEvent(nostrTestEvent(), network.relays)
                        }
                        assertEquals(
                            if (acceptAuth) 2 else 0,
                            network.acceptedPublications
                                .map { it.first }
                                .toSet()
                                .size,
                        )
                    } finally {
                        auth.destroy()
                    }
                }
            }
        }

    @Test
    fun publicationStopsWaitingWhenCancelledDuringAuthentication() =
        runTest {
            QuartzTestRelays(backgroundScope).use { network ->
                network.requireAuthentication = true
                val auth = network.client.authenticateNostrRelays(backgroundScope, delayedSigner()) { network.relays }
                try {
                    val event = nostrTestEvent()
                    val pending = async { network.client.publishNostrEvent(event, network.relays) }
                    runCurrent()
                    advanceTimeBy(100)
                    runCurrent()
                    assertFalse(pending.isCompleted)
                    pending.cancelAndJoin()
                    assertTrue(pending.isCancelled)
                } finally {
                    auth.destroy()
                }
            }
        }

    private fun delayedSigner() =
        object : NostrEventSigner {
            override val canSign = true
            override val canSignWithoutInteraction = true

            override suspend fun sign(template: EventTemplate<out QuartzEvent>): QuartzEvent {
                delay(1_000)
                return nostrTestSigner().sign(template)
            }
        }
}
