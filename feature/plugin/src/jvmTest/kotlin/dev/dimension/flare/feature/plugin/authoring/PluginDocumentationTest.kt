package dev.dimension.flare.feature.plugin.authoring

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginDocumentationTest {
    @Test
    fun returnsBoundedTextWithoutExecutingPageScriptsOrSendingCredentials() =
        runBlocking<Unit> {
            val result =
                fetchPluginDocumentation("https://docs.example.com/api") {
                    HttpClient(
                        MockEngine { request ->
                            assertFalse(request.headers.contains("Cookie"))
                            assertFalse(request.headers.contains("Authorization"))
                            respond(
                                "<html><style>hidden</style><script>run()</script><p>API description</p></html>",
                                headers = headersOf("Content-Type", "text/html"),
                            )
                        },
                    )
                }
            assertTrue(result.startsWith("Untrusted website documentation"))
            assertTrue(result.endsWith("API description"))
            assertFalse(result.contains("run()"))
            assertFalse(result.contains("hidden"))
        }

    @Test
    fun refusesOversizedBodiesWithoutContentLength() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> {
                fetchPluginDocumentation("https://docs.example.com/api") {
                    HttpClient(MockEngine { respond("x".repeat(512 * 1024 + 1), headers = headersOf("Content-Type", "text/plain")) })
                }
            }
        }

    @Test
    fun redirectsAndPrivateOrCredentialUrlsCannotBeRead() =
        runBlocking<Unit> {
            var requests = 0
            val factory = {
                HttpClient(
                    MockEngine {
                        requests++
                        respond("", HttpStatusCode.Found, headersOf("Location", "https://localhost/private"))
                    },
                ) { followRedirects = false }
            }
            assertFailsWith<IllegalArgumentException> { fetchPluginDocumentation("https://docs.example.com/api", factory) }
            assertEquals(1, requests)
            listOf(
                "http://docs.example.com/api",
                "https://127.0.0.1/api",
                "https://localhost/api",
                "https://user:secret@docs.example.com/api",
            ).forEach {
                assertFailsWith<IllegalArgumentException> { fetchPluginDocumentation(it, factory) }
            }
            assertEquals(1, requests)
        }
}
