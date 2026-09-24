package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NostrWebSocketTest {
    @Test
    fun preservesFrameOrderAndReportsRemoteClosure() =
        runTest {
            val wire = TestSession(backgroundScope.coroutineContext)
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    HttpResponseData(
                        HttpStatusCode.SwitchingProtocols,
                        GMTDate(),
                        headersOf(),
                        HttpProtocolVersion.HTTP_1_1,
                        wire,
                        respondOk().callContext,
                    )
                }
            HttpClient(engine) { install(WebSockets) }.use { http ->
                val listener = Listener()
                val socket = NostrWebSocket(NormalizedRelayUrl("wss://relay.example"), http, listener, backgroundScope)
                try {
                    socket.connect()
                    socket.connect()
                    listener.opened.await()
                    assertEquals(1, requests)
                    assertTrue(socket.send("first"))
                    assertTrue(socket.send("second"))
                    assertEquals("first", (wire.outgoing.receive() as Frame.Text).readText())
                    assertEquals("second", (wire.outgoing.receive() as Frame.Text).readText())
                    wire.incoming.send(Frame.Text("reply one"))
                    wire.incoming.send(Frame.Text("reply two"))
                    assertEquals("reply one", listener.messages.receive())
                    assertEquals("reply two", listener.messages.receive())
                    wire.closeReason.complete(CloseReason(1000, "done"))
                    wire.incoming.close()
                    assertEquals(1000 to "done", listener.closed.await())
                    assertFalse(socket.send("after close"))
                    assertFalse(listener.failed.isCompleted)
                } finally {
                    socket.disconnect()
                    wire.cancel()
                }
            }
            engine.close()
        }

    @Test
    fun disconnectCancelsHandshakeWithoutReportingFailure() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            val engine =
                MockEngine {
                    started.complete(Unit)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        finished.complete(Unit)
                    }
                }
            HttpClient(engine) { install(WebSockets) }.use { http ->
                val listener = Listener()
                val socket = NostrWebSocket(NormalizedRelayUrl("wss://relay.example"), http, listener, backgroundScope)
                socket.connect()
                started.await()
                socket.disconnect()
                finished.await()
                assertFalse(socket.send("after disconnect"))
                assertFalse(listener.closed.isCompleted)
                assertFalse(listener.failed.isCompleted)
                assertTrue(socket.needsReconnect())
            }
            engine.close()
        }

    private class Listener : WebSocketListener {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Pair<Int, String>>()
        val failed = CompletableDeferred<Throwable>()
        val messages = Channel<String>(Channel.UNLIMITED)

        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) {
            opened.complete(Unit)
        }

        override suspend fun onMessage(text: String) {
            messages.send(text)
        }

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            closed.complete(code to reason)
        }

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {
            failed.complete(t)
        }
    }

    @OptIn(InternalAPI::class)
    private class TestSession(
        context: CoroutineContext,
    ) : DefaultWebSocketSession {
        override val coroutineContext = context + Job(context[Job])
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override var pingIntervalMillis = 0L
        override var timeoutMillis = 0L
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val outgoing = Channel<Frame>(Channel.UNLIMITED)
        override val extensions = emptyList<WebSocketExtension<*>>()
        override val closeReason = CompletableDeferred<CloseReason?>()

        override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) = Unit

        override suspend fun flush() = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun terminate() {
            cancel()
        }
    }
}
