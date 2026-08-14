package dev.dimension.flare.feature.plugin.host

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorPluginHttpTransportTest {
    @Test
    fun rejectsDeclaredOversizedResponseBeforeReadingIt() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = "ignored",
                        headers = headersOf(HttpHeaders.ContentLength, (MAX_RESPONSE_BYTES + 1).toString()),
                    )
                }
            val transport = KtorPluginHttpTransport(engine)
            try {
                assertFailsWith<IllegalArgumentException> {
                    transport.execute(
                        PluginTransportRequestV1(
                            method = "GET",
                            url = "https://instance.example/large",
                            headers = emptyMap(),
                            body = null,
                            timeoutMillis = 30_000,
                        ),
                    )
                }
                Unit
            } finally {
                transport.close()
            }
        }

    @Test
    fun rejectsOversizedResponseWithoutContentLengthWhileStreaming() =
        runBlocking {
            val engine = MockEngine { respond(content = ByteArray(MAX_RESPONSE_BYTES + 1), headers = headersOf()) }
            val transport = KtorPluginHttpTransport(engine)
            try {
                assertFailsWith<IllegalArgumentException> {
                    transport.execute(
                        PluginTransportRequestV1(
                            method = "GET",
                            url = "https://instance.example/chunked-large",
                            headers = emptyMap(),
                            body = null,
                            timeoutMillis = 30_000,
                        ),
                    )
                }
                Unit
            } finally {
                transport.close()
            }
        }

    @Test
    fun streamsMultipartAssetIntoRequestBody() =
        runBlocking {
            val asset = BufferAsset("streamed-image".encodeToByteArray())
            var captured = ""
            val engine =
                MockEngine { request ->
                    val content = request.body as OutgoingContent.WriteChannelContent
                    captured =
                        coroutineScope {
                            val channel = ByteChannel(autoFlush = true)
                            val writer =
                                async {
                                    content.writeTo(channel)
                                    channel.close()
                                }
                            val value = channel.readRemaining().readText()
                            writer.await()
                            value
                        }
                    respond("ok")
                }
            val transport = KtorPluginHttpTransport(engine)
            try {
                val response =
                    transport.execute(
                        PluginTransportRequestV1(
                            method = "POST",
                            url = "https://instance.example/upload",
                            headers = emptyMap(),
                            body =
                                PluginTransportBodyV1.Multipart(
                                    listOf(
                                        PluginTransportMultipartPartV1.Text("description", "alt", null),
                                        PluginTransportMultipartPartV1.Asset("file", asset, "image.png", "image/png"),
                                    ),
                                ),
                            timeoutMillis = 30_000,
                        ),
                    )
                assertEquals("ok", response.body.decodeToString())
                assertEquals(1, asset.openCount)
                assertContains(captured, "name=\"description\"")
                assertContains(captured, "name=\"file\"; filename=\"image.png\"")
                assertContains(captured, "streamed-image")
            } finally {
                transport.close()
            }
        }
}
