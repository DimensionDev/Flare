package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal class BasicKtorWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> HttpClient,
    val out: WebSocketListener,
) : WebSocket {
    companion object {
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("BasicKtorWebSocket", "Coroutine Exception: ${throwable.message}", throwable)
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
    private var socketJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null

    override fun needsReconnect() = socketJob == null || socketJob?.isActive == false

    override fun connect() {
        if (socketJob?.isActive == true) return

        socketJob =
            scope.launch {
                try {
                    val startTime = Clock.System.now().toEpochMilliseconds() // 或者 System.currentTimeMillis()

                    httpClient(url).webSocket(urlString = url.url) {
                        session = this
                        val endTime = Clock.System.now().toEpochMilliseconds()

                        val extensions = call.response.headers["Sec-WebSocket-Extensions"]
                        val compression = extensions?.contains("permessage-deflate") ?: false

                        out.onOpen((endTime - startTime).toInt(), compression)

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        out.onMessage(text)
                                    }
                                    else -> {
                                    }
                                }
                            }

                            val reason = closeReason.await()
                            out.onClosed(reason?.code?.toInt() ?: 1000, reason?.message ?: "Closed normally")
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                } catch (e: CancellationException) {
                    out.onClosed(1000, "Cancelled")
                } catch (t: Throwable) {
                    out.onFailure(t, null, t.message)
                } finally {
                    session = null
                }
            }
    }

    override fun disconnect() {
        scope.launch {
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnect"))
            } catch (e: Exception) {
            }
            socketJob?.cancel()
            session = null
        }
    }

    override fun send(msg: String): Boolean {
        val currentSession = session
        if (currentSession != null && currentSession.isActive) {
            scope.launch {
                try {
                    currentSession.send(Frame.Text(msg))
                } catch (e: Exception) {
                    Log.e("BasicKtorWebSocket", "Failed to send message", e)
                }
            }
            return true
        }
        return false
    }

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> HttpClient,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = BasicKtorWebSocket(url, httpClient, out)
    }
}
