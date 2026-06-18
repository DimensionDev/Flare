package dev.dimension.flare.data.network.bluesky

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AtprotoProxyPluginTest {
    @Test
    fun addsAppViewProxyHeaderForAppBskyMethods() =
        runTest {
            assertEquals(
                "did:web:api.bsky.app#bsky_appview",
                proxyHeaderFor("https://pds.test/xrpc/app.bsky.feed.getTimeline"),
            )
        }

    @Test
    fun keepsChatProxyHeaderForChatMethods() =
        runTest {
            assertEquals(
                "did:web:api.bsky.chat#bsky_chat",
                proxyHeaderFor("https://pds.test/xrpc/chat.bsky.convo.listConvos"),
            )
        }

    @Test
    fun doesNotProxyComAtprotoMethods() =
        runTest {
            assertNull(proxyHeaderFor("https://pds.test/xrpc/com.atproto.server.describeServer"))
        }

    private suspend fun proxyHeaderFor(url: String): String? {
        var proxyHeader: String? = null
        val client =
            HttpClient(
                MockEngine { request ->
                    proxyHeader = request.headers["Atproto-Proxy"]
                    respond(
                        content = """{"ok":true}""",
                        headers =
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                            },
                    )
                },
            ) {
                install(AtprotoProxyPlugin)
            }

        try {
            client.get(url)
        } finally {
            client.close()
        }

        return proxyHeader
    }
}
