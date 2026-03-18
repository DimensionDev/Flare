package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import sh.christian.ozone.BlueskyJson
import kotlin.test.Test
import kotlin.test.assertEquals

class BlueskyAuthPluginTest {
    @Test
    fun expiredRequestRefreshesSuccessfully() =
        runTest {
            val credentialFlow =
                MutableStateFlow<UiAccount.Bluesky.Credential>(
                    credential(
                        accessToken = "access-old",
                        refreshToken = "refresh-old",
                    ),
                )
            var refreshCalls = 0

            val client =
                createClient(
                    credentialFlow = credentialFlow,
                    onTokensChanged = { credentialFlow.value = it },
                ) { path, authorization ->
                    when (path) {
                        "/xrpc/com.atproto.server.refreshSession" -> {
                            refreshCalls += 1
                            assertEquals("Bearer refresh-old", authorization)
                            jsonResponse(
                                HttpStatusCode.OK,
                                """
                                {
                                  "accessJwt": "access-new",
                                  "refreshJwt": "refresh-new",
                                  "handle": "alice.bsky.social",
                                  "did": "did:plc:alice"
                                }
                                """.trimIndent(),
                            )
                        }

                        else ->
                            when (authorization) {
                                "Bearer access-old" ->
                                    jsonResponse(
                                        HttpStatusCode.Unauthorized,
                                        """{"error":"ExpiredToken","message":"token expired"}""",
                                    )

                                "Bearer access-new" ->
                                    jsonResponse(
                                        HttpStatusCode.OK,
                                        """{"ok":true}""",
                                    )

                                else -> error("Unexpected authorization header: $authorization")
                            }
                    }
                }

            val response = client.get("https://bsky.social/xrpc/app.bsky.feed.getTimeline").bodyAsText()

            assertEquals("""{"ok":true}""", response)
            assertEquals(1, refreshCalls)
            assertEquals("access-new", credentialFlow.value.accessToken)
            assertEquals("refresh-new", credentialFlow.value.refreshToken)
        }

    @Test
    fun concurrentExpiredRequestsReuseRefreshedCredential() =
        runTest {
            val credentialFlow =
                MutableStateFlow<UiAccount.Bluesky.Credential>(
                    credential(
                        accessToken = "access-old",
                        refreshToken = "refresh-old",
                    ),
                )
            val secondExpiredResponseStarted = CompletableDeferred<Unit>()
            var expiredCalls = 0
            var refreshCalls = 0

            val client =
                createClient(
                    credentialFlow = credentialFlow,
                    onTokensChanged = { credentialFlow.value = it },
                ) { path, authorization ->
                    when (path) {
                        "/xrpc/com.atproto.server.refreshSession" -> {
                            assertEquals("Bearer refresh-old", authorization)
                            refreshCalls += 1
                            when (refreshCalls) {
                                1 -> {
                                    jsonResponse(
                                        HttpStatusCode.OK,
                                        """
                                        {
                                          "accessJwt": "access-new",
                                          "refreshJwt": "refresh-new",
                                          "handle": "alice.bsky.social",
                                          "did": "did:plc:alice"
                                        }
                                        """.trimIndent(),
                                    )
                                }

                                else -> error("Unexpected refresh call count: $refreshCalls")
                            }
                        }

                        else ->
                            when (authorization) {
                                "Bearer access-old" -> {
                                    expiredCalls += 1
                                    if (expiredCalls == 1) {
                                        secondExpiredResponseStarted.await()
                                    } else if (expiredCalls == 2) {
                                        secondExpiredResponseStarted.complete(Unit)
                                    }
                                    jsonResponse(
                                        HttpStatusCode.Unauthorized,
                                        """{"error":"ExpiredToken","message":"token expired"}""",
                                    )
                                }

                                "Bearer access-new" ->
                                    jsonResponse(
                                        HttpStatusCode.OK,
                                        """{"ok":true}""",
                                    )

                                else -> error("Unexpected authorization header: $authorization")
                            }
                    }
                }

            val results =
                supervisorScope {
                    val requestOne =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            client.get("https://bsky.social/xrpc/app.bsky.feed.getTimeline").bodyAsText()
                        }
                    val requestTwo =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            client.get("https://bsky.social/xrpc/app.bsky.actor.getProfile").bodyAsText()
                        }
                    listOf(
                        runCatching { requestOne.await() },
                        runCatching { requestTwo.await() },
                    )
                }

            assertEquals(1, refreshCalls)
            assertEquals(2, results.count { it.isSuccess })
            assertEquals(listOf("""{"ok":true}""", """{"ok":true}"""), results.map { it.getOrThrow() })
            assertEquals("access-new", credentialFlow.value.accessToken)
            assertEquals("refresh-new", credentialFlow.value.refreshToken)
        }

    @Test
    fun credentialFlowChangeUsesNewCredential() =
        runTest {
            val credentialFlow =
                MutableStateFlow<UiAccount.Bluesky.Credential>(
                    credential(
                        accessToken = "access-old",
                        refreshToken = "refresh-old",
                    ),
                )

            val client =
                createClient(
                    credentialFlow = credentialFlow,
                    onTokensChanged = { credentialFlow.value = it },
                ) { path, authorization ->
                    when (authorization) {
                        "Bearer access-old" ->
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"ok":"old"}""",
                            )

                        "Bearer access-new" ->
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"ok":"new"}""",
                            )

                        else -> error("Unexpected authorization header: $authorization")
                    }
                }

            // First request uses old credential
            val responseOne = client.get("https://bsky.social/xrpc/app.bsky.feed.getTimeline").bodyAsText()
            assertEquals("""{"ok":"old"}""", responseOne)

            // Change credential flow to new credential
            credentialFlow.value =
                credential(
                    accessToken = "access-new",
                    refreshToken = "refresh-new",
                )

            // Second request uses new credential
            val responseTwo = client.get("https://bsky.social/xrpc/app.bsky.feed.getTimeline").bodyAsText()
            assertEquals("""{"ok":"new"}""", responseTwo)
        }

    private fun credential(
        accessToken: String,
        refreshToken: String,
    ) = UiAccount.Bluesky.Credential.BlueskyCredential(
        baseUrl = "https://bsky.social",
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

    private fun createClient(
        credentialFlow: MutableStateFlow<UiAccount.Bluesky.Credential>,
        onTokensChanged: suspend (UiAccount.Bluesky.Credential) -> Unit,
        handler: suspend MockRequestHandleScope.(path: String, authorization: String?) -> HttpResponseData,
    ): HttpClient =
        HttpClient(
            MockEngine { request: HttpRequestData ->
                handler(
                    request.url.encodedPath,
                    request.headers[HttpHeaders.Authorization],
                )
            },
        ) {
            install(ContentNegotiation) {
                json(BlueskyJson)
            }
            install(BlueskyAuthPlugin) {
                accountKey = MicroBlogKey("did:plc:alice", "bsky.social")
                authTokenFlow = credentialFlow
                this.onAuthTokensChanged = onTokensChanged
            }
        }

    private fun MockRequestHandleScope.jsonResponse(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = body,
        status = status,
        headers =
            Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            },
    )
}
