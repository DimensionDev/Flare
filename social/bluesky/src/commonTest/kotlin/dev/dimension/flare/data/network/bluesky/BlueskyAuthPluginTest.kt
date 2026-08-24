package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.model.MicroBlogKey
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class BlueskyAuthPluginTest {
    @Test
    fun expiredRequestRefreshesSuccessfully() =
        runTest {
            val credentialFlow =
                MutableStateFlow<BlueskyCredential>(
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

                        else -> {
                            when (authorization) {
                                "Bearer access-old" -> {
                                    jsonResponse(
                                        HttpStatusCode.Unauthorized,
                                        """{"error":"ExpiredToken","message":"token expired"}""",
                                    )
                                }

                                "Bearer access-new" -> {
                                    jsonResponse(
                                        HttpStatusCode.OK,
                                        """{"ok":true}""",
                                    )
                                }

                                else -> {
                                    error("Unexpected authorization header: $authorization")
                                }
                            }
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
                MutableStateFlow<BlueskyCredential>(
                    credential(
                        accessToken = "access-old",
                        refreshToken = "refresh-old",
                    ),
                )
            val bothExpiredReachedMock = CompletableDeferred<Unit>()
            val expiredCallsMutex = Mutex()
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

                                else -> {
                                    error("Unexpected refresh call count: $refreshCalls")
                                }
                            }
                        }

                        else -> {
                            when (authorization) {
                                "Bearer access-old" -> {
                                    val callIndex = expiredCallsMutex.withLock { ++expiredCalls }
                                    when (callIndex) {
                                        1 -> {
                                            // Park the first request inside the mock until the
                                            // second one also arrives, so both observe an expired
                                            // token before either of them gets to refresh.
                                            withTimeout(5_000) {
                                                bothExpiredReachedMock.await()
                                            }
                                        }

                                        2 -> {
                                            bothExpiredReachedMock.complete(Unit)
                                        }

                                        else -> {
                                            error("Unexpected expired call count: $callIndex")
                                        }
                                    }
                                    jsonResponse(
                                        HttpStatusCode.Unauthorized,
                                        """{"error":"ExpiredToken","message":"token expired"}""",
                                    )
                                }

                                "Bearer access-new" -> {
                                    jsonResponse(
                                        HttpStatusCode.OK,
                                        """{"ok":true}""",
                                    )
                                }

                                else -> {
                                    error("Unexpected authorization header: $authorization")
                                }
                            }
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
                    runCatching { awaitAll(requestOne, requestTwo) }
                }

            assertEquals(1, refreshCalls)
            assertEquals(listOf("""{"ok":true}""", """{"ok":true}"""), results.getOrThrow())
            assertEquals("access-new", credentialFlow.value.accessToken)
            assertEquals("refresh-new", credentialFlow.value.refreshToken)
        }

    @Test
    fun oauthRefreshKeepsResolvedPds() =
        runTest {
            val resolvedPds = "https://pds.example:8443"
            val credentialFlow =
                MutableStateFlow<BlueskyCredential>(
                    BlueskyCredential.OAuthCredential(
                        baseUrl = "https://auth.example",
                        oAuthToken =
                            OAuthToken(
                                accessToken = "access-old",
                                refreshToken = "refresh-old",
                                keyPair = DpopKeyPair.generateKeyPair(),
                                expiresIn = 10.minutes,
                                scopes = listOf(OAuthScope.AtProto),
                                subject = Did("did:plc:alice"),
                                nonce = "nonce-old",
                                clientId = "https://client.example/metadata.json",
                                pdsUrl = resolvedPds,
                            ),
                    ),
                )
            var refreshCalls = 0
            val oauthHttpClient =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/.well-known/oauth-authorization-server" -> {
                                jsonResponse(
                                    HttpStatusCode.OK,
                                    """
                                    {
                                      "issuer": "https://auth.example",
                                      "authorization_endpoint": "https://auth.example/authorize",
                                      "token_endpoint": "https://auth.example/token"
                                    }
                                    """.trimIndent(),
                                )
                            }

                            "/token" -> {
                                refreshCalls += 1
                                jsonResponse(
                                    HttpStatusCode.OK,
                                    """
                                    {
                                      "access_token": "access-new",
                                      "token_type": "DPoP",
                                      "expires_in": 3600,
                                      "refresh_token": "refresh-new",
                                      "scope": "atproto",
                                      "sub": "did:plc:alice"
                                    }
                                    """.trimIndent(),
                                    dpopNonce = "nonce-new",
                                )
                            }

                            else -> {
                                error("Unexpected OAuth request: ${request.url}")
                            }
                        }
                    },
                )
            val oauthApi =
                OAuthApi(
                    httpClient = oauthHttpClient,
                    challengeSelector = { OAuthCodeChallengeMethodS256 },
                )
            var retriedHost: String? = null
            var retriedPort: Int? = null
            val apiClient =
                HttpClient(
                    MockEngine { request ->
                        when (request.headers[HttpHeaders.Authorization]) {
                            "DPoP access-old" -> {
                                jsonResponse(
                                    HttpStatusCode.Unauthorized,
                                    """{"error":"invalid_token","message":"token expired"}""",
                                )
                            }

                            "DPoP access-new" -> {
                                retriedHost = request.url.host
                                retriedPort = request.url.port
                                if (request.url.host == "auth.example") {
                                    jsonResponse(
                                        HttpStatusCode.Unauthorized,
                                        """{"error":"InvalidToken","message":"OAuth tokens are meant for PDS access only"}""",
                                    )
                                } else {
                                    jsonResponse(HttpStatusCode.OK, """{"ok":true}""")
                                }
                            }

                            else -> {
                                error(
                                    "Unexpected authorization header: " +
                                        request.headers[HttpHeaders.Authorization],
                                )
                            }
                        }
                    },
                ) {
                    install(BlueskyAuthPlugin) {
                        accountKey = MicroBlogKey("did:plc:alice", "auth.example")
                        authTokenFlow = credentialFlow
                        onAuthTokensChanged = { credentialFlow.value = it }
                        this.oauthApi = oauthApi
                    }
                }

            try {
                val response =
                    apiClient
                        .get("https://auth.example/xrpc/app.bsky.feed.getTimeline")
                        .bodyAsText()

                assertEquals("""{"ok":true}""", response)
                assertEquals(1, refreshCalls)
                assertEquals("pds.example", retriedHost)
                assertEquals(8443, retriedPort)
                assertEquals(
                    resolvedPds,
                    (credentialFlow.value as BlueskyCredential.OAuthCredential).oAuthToken.pdsUrl,
                )
            } finally {
                apiClient.close()
                oauthHttpClient.close()
            }
        }

    @Test
    fun credentialFlowChangeUsesNewCredential() =
        runTest {
            val credentialFlow =
                MutableStateFlow<BlueskyCredential>(
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
                        "Bearer access-old" -> {
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"ok":"old"}""",
                            )
                        }

                        "Bearer access-new" -> {
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"ok":"new"}""",
                            )
                        }

                        else -> {
                            error("Unexpected authorization header: $authorization")
                        }
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
    ) = BlueskyCredential.Password(
        baseUrl = "https://bsky.social",
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

    private fun createClient(
        credentialFlow: MutableStateFlow<BlueskyCredential>,
        onTokensChanged: suspend (BlueskyCredential) -> Unit,
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
        dpopNonce: String? = null,
    ) = respond(
        content = body,
        status = status,
        headers =
            Headers.build {
                append(HttpHeaders.ContentType, "application/json")
                dpopNonce?.let { append("DPoP-Nonce", it) }
            },
    )
}
