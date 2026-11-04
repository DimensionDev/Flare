package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.JSON
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NostrPublicEventCacheTest {
    @Test
    fun readsCacheProtocolAndRejectsForgedUnrelatedAndUnsignedEvents() =
        runTest {
            val event = nostrTestEvent("archived post")
            val other = nostrTestEvent(author = 1)
            val wire = NostrWebSocketTest.TestSession(backgroundScope.coroutineContext)
            client(wire, testScheduler).use { http ->
                val cache = NostrPublicEventCache { method, parameters -> requestPrimalEvents(method, parameters, http) }
                val pending = async { cache.fetch(listOf(Filter(authors = listOf(event.pubKey), kinds = listOf(1)))) }
                val request = JSON.parseToJsonElement((wire.outgoing.receive() as Frame.Text).readText()).jsonArray
                val subscription = request[1].jsonPrimitive.content
                assertEquals("REQ", request[0].jsonPrimitive.content)
                assertEquals(
                    "feed",
                    request[2]
                        .jsonObject
                        .getValue("cache")
                        .jsonArray[0]
                        .jsonPrimitive.content,
                )
                wire.incoming.send(Frame.Text("not json"))
                wire.incoming.send(Frame.Text("[{},{}]"))
                wire.incoming.send(Frame.Text("[\"EOSE\",\"unrelated-subscription\"]"))
                wire.incoming.send(Frame.Text("[\"EVENT\",\"$subscription\",{\"kind\":10000105,\"content\":\"stats\"}]"))
                val forged = event.toJson().replace("archived post", "forged post")
                wire.incoming.send(Frame.Text("[\"EVENT\",\"$subscription\",$forged]"))
                for (item in listOf(event, event, other)) wire.incoming.send(Frame.Text("[\"EVENT\",\"$subscription\",${item.toJson()}]"))
                wire.incoming.send(Frame.Text("[\"EOSE\",\"$subscription\"]"))
                assertEquals(listOf(event.id), pending.await().map { it.id })
                assertEquals(listOf(event.id), cache.find(listOf(Filter(ids = listOf(event.id))))?.map { it.id })
            }
            wire.cancel()
        }

    @Test
    fun timeoutFailsInsteadOfReturningAnEmptyFeedAndCancellationPropagates() =
        runTest {
            for (cancel in listOf(false, true)) {
                val wire = NostrWebSocketTest.TestSession(backgroundScope.coroutineContext)
                client(wire, testScheduler).use { http ->
                    val pending =
                        async { runCatching { requestPrimalEvents("feed", JsonObject(emptyMap()), http) } }
                    wire.outgoing.receive()
                    if (cancel) {
                        pending.cancelAndJoin()
                        assertTrue(pending.isCancelled)
                    } else {
                        advanceTimeBy(8_001)
                        runCurrent()
                        assertFailsWith<IllegalStateException> { pending.await().getOrThrow() }
                    }
                    assertTrue(wire.coroutineContext[Job]?.isCancelled == true)
                }
                wire.cancel()
            }
        }

    private fun client(
        wire: NostrWebSocketTest.TestSession,
        scheduler: TestCoroutineScheduler,
    ) = HttpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(scheduler)
                addHandler {
                    HttpResponseData(
                        HttpStatusCode.SwitchingProtocols,
                        GMTDate(),
                        headersOf(),
                        HttpProtocolVersion.HTTP_1_1,
                        wire,
                        respondOk().callContext,
                    )
                }
            },
        ),
    ) { install(WebSockets) }
}
