package dev.dimension.flare.feature.plugin.authoring

import dev.dimension.flare.feature.plugin.installer.FppLimits
import dev.dimension.flare.feature.plugin.installer.pluginIconFixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginDocumentationTest {
    @Test
    fun downloadTimeoutIsAReportedFailureInsteadOfCancellingTheApplicationJob() =
        runTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    fetchPluginIcon("https://example.com/slow") {
                        HttpClient(MockEngine) {
                            engine {
                                dispatcher = StandardTestDispatcher(testScheduler)
                                addHandler { awaitCancellation() }
                            }
                        }
                    }
                }
            assertEquals("Resource request timed out", failure.message)
        }

    @Test
    fun exposesHeadIconsRelativeToTheFinalPageAndBaseWithoutSvgCandidates() =
        runBlocking {
            val result =
                fetchPluginDocumentation("https://example.com/start") {
                    HttpClient(
                        MockEngine { request ->
                            if (request.url.encodedPath == "/start") {
                                respond("", HttpStatusCode.Found, headersOf("Location", "/docs/index"))
                            } else {
                                assertEquals("/docs/index", request.url.encodedPath)
                                respond(
                                    """
                                    <head><base href="../assets/"><link rel="icon" href="logo.webp" sizes="64x64" type="image/webp">
                                    <link rel="icon" href="logo.svg" type="image/svg+xml"></head><body>API description</body>
                                    """.trimIndent(),
                                    headers = headersOf("Content-Type", "text/html; charset=utf-8"),
                                )
                            }
                        },
                    ) { followRedirects = false }
                }
            assertTrue(result.contains("https://example.com/assets/logo.webp"))
            assertTrue(result.contains("64x64"))
            assertFalse(result.contains("logo.svg"))
            assertTrue(result.endsWith("API description"))
        }

    @Test
    fun downloadsOriginalIconBytesWithoutCredentialsAndWithBoundedRedirects() =
        runBlocking {
            val icon = pluginIconFixtures.getValue("ico")
            var requests = 0
            val result =
                fetchPluginIcon("https://example.com/favicon.ico") {
                    HttpClient(
                        MockEngine { request ->
                            requests++
                            assertFalse(request.headers.contains("Cookie"))
                            assertFalse(request.headers.contains("Authorization"))
                            if (request.url.host == "example.com") {
                                respond("", HttpStatusCode.Found, headersOf("Location", "https://cdn.example.com/icon"))
                            } else {
                                respond(icon, headers = headersOf("Content-Type", "application/octet-stream"))
                            }
                        },
                    ) { followRedirects = false }
                }
            assertEquals(2, requests)
            assertContentEquals(icon, result)
            var redirects = 0
            assertFailsWith<IllegalArgumentException> {
                fetchPluginIcon("https://example.com/loop") {
                    HttpClient(
                        MockEngine {
                            redirects++
                            respond("", HttpStatusCode.Found, headersOf("Location", "/loop"))
                        },
                    ) {
                        followRedirects = false
                    }
                }
            }
            assertEquals(4, redirects)
        }

    @Test
    fun refusesSvgMislabelledHtmlAndOversizedIconsEvenWithoutContentLength() =
        runBlocking {
            listOf(
                "image/svg+xml" to "<svg/>".encodeToByteArray(),
                "image/png" to "<html/>".encodeToByteArray(),
                "image/png" to ByteArray(FppLimits.MAX_ICON_BYTES.toInt() + 1),
            ).forEach { (type, bytes) ->
                assertFailsWith<IllegalArgumentException> {
                    fetchPluginIcon(
                        "https://example.com/icon",
                    ) { HttpClient(MockEngine { respond(bytes, headers = headersOf("Content-Type", type)) }) }
                }
            }
        }

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
