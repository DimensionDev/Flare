package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.platform.BlueskyCredential
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class BlueskyAuthRecoveryTest {
    @Test
    fun passwordInvalidTokenRefreshesAndRetries() =
        runTest {
            invalidTokenRecovers(oauth = false)
        }

    @Test
    fun oauthInvalidTokenRefreshesAndRetries() =
        runTest {
            invalidTokenRecovers(oauth = true)
        }

    @Test
    fun unauthorizedWithoutAtprotoErrorBodyRefreshesAndRetries() =
        runTest {
            for (oauth in listOf(false, true)) {
                withSession(oauth, apiResponse = { request ->
                    if (request.headers[HttpHeaders.Authorization]?.endsWith("access-old") == true) {
                        jsonResponse(HttpStatusCode.Unauthorized, "Unauthorized")
                    } else {
                        jsonResponse(HttpStatusCode.OK, """{"ok":true}""")
                    }
                }) {
                    assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                    assertEquals(1, refreshRequests.size)
                    assertEquals(2, requests.size)
                }
            }
        }

    @Test
    fun repeatedUnauthorizedRefreshesOnlyOnceAndPreservesResponse() =
        runTest {
            for (oauth in listOf(false, true)) {
                val errorBody = """{"error":"ExpiredToken","message":"still rejected"}"""
                withSession(oauth, apiResponse = {
                    jsonResponse(HttpStatusCode.Unauthorized, errorBody)
                }) {
                    val response = client.get(TIMELINE)
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    assertEquals(errorBody, response.bodyAsText())
                    assertEquals(1, refreshRequests.size)
                    assertEquals(2, requests.size)
                    assertEquals("access-new-1", credentials.value.accessToken)
                    assertEquals("refresh-new-1", credentials.value.refreshToken)
                }
            }
        }

    @Test
    fun forbiddenResponseDoesNotRefresh() =
        runTest {
            for (oauth in listOf(false, true)) {
                withSession(oauth, apiResponse = {
                    jsonResponse(HttpStatusCode.Forbidden, """{"error":"InsufficientScope"}""")
                }) {
                    assertEquals(HttpStatusCode.Forbidden, client.get(TIMELINE).status)
                    assertEquals(0, refreshRequests.size)
                    assertEquals(1, requests.size)
                    assertEquals("access-old", credentials.value.accessToken)
                }
            }
        }

    @Test
    fun explicitAuthorizationIsNotReplacedOrRefreshed() =
        runTest {
            for (oauth in listOf(false, true)) {
                withSession(oauth, apiResponse = {
                    jsonResponse(HttpStatusCode.Unauthorized, """{"error":"ExpiredToken"}""")
                }) {
                    val response =
                        client.get(TIMELINE) {
                            header(HttpHeaders.Authorization, "Bearer explicit-token")
                        }
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    assertEquals(0, refreshRequests.size)
                    assertEquals(listOf("Bearer explicit-token"), requests.map { it.headers[HttpHeaders.Authorization] })
                    assertEquals("access-old", credentials.value.accessToken)
                }
            }
        }

    @Test
    fun nonceChallengeTakesPriorityOverUnauthorizedRefresh() =
        runTest {
            var attempts = 0
            withSession(apiResponse = {
                if (++attempts == 1) {
                    jsonResponse(HttpStatusCode.Unauthorized, """{"error":"use_dpop_nonce"}""", "pds-nonce-1")
                } else {
                    jsonResponse(HttpStatusCode.OK, """{"ok":true}""", "pds-nonce-2")
                }
            }) {
                assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                assertEquals(0, refreshRequests.size)
                assertEquals(listOf(null, "pds-nonce-1"), requests.map { it.dpopNonce() })
                assertNotEquals(requests[0].headers["DPoP"], requests[1].headers["DPoP"])
                assertEquals("auth-nonce-old", (credentials.value as BlueskyCredential.OAuthCredential).oAuthToken.nonce)
            }
        }

    @Test
    fun nonceChallengeWithoutHeaderDoesNotRefreshTokens() =
        runTest {
            withSession(apiResponse = {
                jsonResponse(HttpStatusCode.Unauthorized, """{"error":"use_dpop_nonce"}""")
            }) {
                assertEquals(HttpStatusCode.Unauthorized, client.get(TIMELINE).status)
                assertEquals(0, refreshRequests.size)
                assertEquals(1, requests.size)
            }
        }

    @Test
    fun headerNonceChallengeRecoversWithoutRefreshingTokens() =
        runTest {
            val challenges =
                listOf(
                    listOf("""DPoP error="use_dpop_nonce""""),
                    listOf("""Bearer realm="example"""", """DPoP error="use_dpop_nonce""""),
                    listOf("""Bearer realm="example, resource", dpop ERROR = "use_dpop_nonce""""),
                )
            for (body in listOf("", "<html>Nonce required</html>")) {
                for (authenticate in challenges) {
                    var attempts = 0
                    withSession(apiResponse = {
                        if (++attempts == 1) {
                            authenticationChallenge(body, authenticate = authenticate)
                        } else {
                            jsonResponse(HttpStatusCode.OK, """{"ok":true}""", "pds-nonce-2")
                        }
                    }) {
                        assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                        assertEquals(0, refreshRequests.size, "Unexpected refresh for $authenticate")
                        assertEquals(listOf("DPoP access-old", "DPoP access-old"), requests.map { it.headers[HttpHeaders.Authorization] })
                        assertEquals(listOf(null, "pds-nonce-1"), requests.map { it.dpopNonce() })
                        assertNotEquals(requests[0].headers["DPoP"], requests[1].headers["DPoP"])
                        assertEquals("refresh-old", credentials.value.refreshToken)
                        assertEquals("auth-nonce-old", (credentials.value as BlueskyCredential.OAuthCredential).oAuthToken.nonce)
                    }
                }
            }
        }

    @Test
    fun headerNonceChallengeRecoversWhenAuthorizationServerIsUnavailable() =
        runTest {
            for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.TooManyRequests)) {
                var attempts = 0
                withSession(oauthTokenStatus = status, apiResponse = {
                    if (++attempts == 1) {
                        authenticationChallenge()
                    } else {
                        jsonResponse(HttpStatusCode.OK, "{}")
                    }
                }) {
                    assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                    assertEquals(0, refreshRequests.size)
                    assertEquals(2, requests.size)
                }
            }
        }

    @Test
    fun nonNonceAuthenticationChallengesStillRefreshTokens() =
        runTest {
            for (authenticate in listOf(
                """DPoP error="invalid_token"""",
                """DPoP error="invalid_token", error_description="use_dpop_nonce"""",
                """Bearer error="use_dpop_nonce"""",
                "DPoP error=\"unterminated",
            )) {
                var attempts = 0
                withSession(apiResponse = {
                    if (++attempts == 1) {
                        authenticationChallenge(authenticate = listOf(authenticate))
                    } else {
                        jsonResponse(HttpStatusCode.OK, "{}")
                    }
                }) {
                    assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                    assertEquals(1, refreshRequests.size, "Expected token refresh for $authenticate")
                    assertEquals(2, requests.size)
                    assertEquals("access-new-1", credentials.value.accessToken)
                }
            }
        }

    @Test
    fun headerNonceChallengeWithoutNonceDoesNotRefreshTokens() =
        runTest {
            withSession(apiResponse = { authenticationChallenge(nonce = null) }) {
                assertEquals(HttpStatusCode.Unauthorized, client.get(TIMELINE).status)
                assertEquals(0, refreshRequests.size)
                assertEquals(1, requests.size)
                assertEquals("access-old", credentials.value.accessToken)
            }
        }

    @Test
    fun headerNonceChallengeRetriesAreBounded() =
        runTest {
            var attempts = 0
            withSession(apiResponse = { authenticationChallenge(nonce = "pds-nonce-${++attempts}") }) {
                assertEquals(HttpStatusCode.Unauthorized, client.get(TIMELINE).status)
                assertEquals(0, refreshRequests.size)
                assertEquals(4, requests.size)
                assertEquals("access-old", credentials.value.accessToken)
                assertEquals("refresh-old", credentials.value.refreshToken)
            }
        }

    @Test
    fun headerNonceChallengeAfterRefreshUsesRotatedCredentials() =
        runTest {
            var attempts = 0
            withSession(apiResponse = {
                when (++attempts) {
                    1 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"InvalidToken"}""", "pds-nonce-1")
                    2 -> authenticationChallenge(nonce = "pds-nonce-2")
                    3 -> jsonResponse(HttpStatusCode.OK, "{}")
                    else -> error("Unexpected request")
                }
            }) {
                assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                assertEquals(1, refreshRequests.size)
                assertEquals(
                    listOf("DPoP access-old", "DPoP access-new-1", "DPoP access-new-1"),
                    requests.map { it.headers[HttpHeaders.Authorization] },
                )
                assertEquals(listOf(null, "pds-nonce-1", "pds-nonce-2"), requests.map { it.dpopNonce() })
                assertEquals("refresh-new-1", credentials.value.refreshToken)
            }
        }

    @Test
    fun successfulResponsesPersistPdsNoncesForFollowingRequests() =
        runTest {
            var attempts = 0
            withSession(apiResponse = {
                jsonResponse(HttpStatusCode.OK, """{"ok":true}""", "pds-nonce-${++attempts}")
            }) {
                repeat(3) {
                    assertEquals("""{"ok":true}""", client.get(TIMELINE).bodyAsText())
                    val persisted = BlueskyJson.encodeToString(BlueskyCredential.serializer(), credentials.value)
                    credentials.value = BlueskyJson.decodeFromString(BlueskyCredential.serializer(), persisted)
                }
                assertEquals(listOf(null, "pds-nonce-1", "pds-nonce-2"), requests.map { it.dpopNonce() })
                assertEquals("auth-nonce-old", (credentials.value as BlueskyCredential.OAuthCredential).oAuthToken.nonce)
                assertEquals(0, refreshRequests.size)
            }
        }

    @Test
    fun refreshKeepsAuthorizationAndPdsNoncesSeparate() =
        runTest {
            var attempts = 0
            withSession(apiResponse = {
                when (++attempts) {
                    1 -> jsonResponse(HttpStatusCode.OK, "{}", "pds-nonce-1")
                    2 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"InvalidToken"}""", "pds-nonce-2")
                    3 -> jsonResponse(HttpStatusCode.OK, "{}")
                    4 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"invalid_token"}""", "pds-nonce-3")
                    5 -> jsonResponse(HttpStatusCode.OK, "{}", "pds-nonce-4")
                    6 -> jsonResponse(HttpStatusCode.OK, "{}")
                    else -> error("Unexpected request")
                }
            }) {
                repeat(4) { assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status) }
                assertEquals(listOf("auth-nonce-old", "auth-nonce-1"), refreshRequests.map { it.dpopNonce() })
                assertEquals(
                    listOf(null, "pds-nonce-1", "pds-nonce-2", "pds-nonce-2", "pds-nonce-3", "pds-nonce-4"),
                    requests.map { it.dpopNonce() },
                )
                assertEquals("auth-nonce-2", (credentials.value as BlueskyCredential.OAuthCredential).oAuthToken.nonce)
            }
        }

    @Test
    fun nonceChallengeAndTokenRefreshCanRecoverInTheSameRequest() =
        runTest {
            var attempts = 0
            withSession(apiResponse = {
                when (++attempts) {
                    1 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"use_dpop_nonce"}""", "pds-nonce-1")
                    2 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"InvalidToken"}""", "pds-nonce-2")
                    3 -> jsonResponse(HttpStatusCode.Unauthorized, """{"error":"use_dpop_nonce"}""", "pds-nonce-3")
                    4 -> jsonResponse(HttpStatusCode.OK, """{"ok":true}""", "pds-nonce-4")
                    else -> error("Unexpected request")
                }
            }) {
                assertEquals(HttpStatusCode.OK, client.get(TIMELINE).status)
                assertEquals(1, refreshRequests.size)
                assertEquals(listOf(null, "pds-nonce-1", "pds-nonce-2", "pds-nonce-3"), requests.map { it.dpopNonce() })
                assertEquals("auth-nonce-old", refreshRequests.single().dpopNonce())
            }
        }

    private suspend fun invalidTokenRecovers(oauth: Boolean) {
        withSession(oauth, apiResponse = { request ->
            if (request.headers[HttpHeaders.Authorization]?.endsWith("access-old") == true) {
                jsonResponse(HttpStatusCode.Unauthorized, """{"error":"InvalidToken"}""")
            } else {
                jsonResponse(HttpStatusCode.OK, """{"ok":true}""")
            }
        }) {
            val response = client.get(TIMELINE)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"ok":true}""", response.bodyAsText())
            assertEquals(1, refreshRequests.size)
            assertEquals(2, requests.size)
            assertEquals("access-new-1", credentials.value.accessToken)
            assertEquals("refresh-new-1", credentials.value.refreshToken)
        }
    }

    private suspend fun withSession(
        oauth: Boolean = true,
        oauthTokenStatus: HttpStatusCode = HttpStatusCode.OK,
        apiResponse: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend Session.() -> Unit,
    ) {
        val original =
            if (oauth) {
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
                            nonce = "auth-nonce-old",
                            clientId = "https://client.example/metadata.json",
                            pdsUrl = "https://pds.example",
                        ),
                    pdsUrlVerified = true,
                )
            } else {
                BlueskyCredential.Password("https://auth.example", "access-old", "refresh-old")
            }
        val credentials = MutableStateFlow(original)
        val requests = mutableListOf<HttpRequestData>()
        val refreshRequests = mutableListOf<HttpRequestData>()
        val oauthClient =
            HttpClient(
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/oauth-authorization-server" -> {
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"issuer":"https://auth.example","authorization_endpoint":"https://auth.example/authorize","token_endpoint":"https://auth.example/token"}""",
                            )
                        }

                        "/token" -> {
                            refreshRequests += request
                            if (oauthTokenStatus != HttpStatusCode.OK) {
                                return@MockEngine jsonResponse(oauthTokenStatus, """{"error":"temporarily_unavailable"}""")
                            }
                            val version = refreshRequests.size
                            jsonResponse(
                                HttpStatusCode.OK,
                                """{"access_token":"access-new-$version","token_type":"DPoP","expires_in":3600,"refresh_token":"refresh-new-$version","scope":"atproto","sub":"did:plc:alice"}""",
                                "auth-nonce-$version",
                            )
                        }

                        else -> {
                            error("Unexpected OAuth endpoint: ${request.url}")
                        }
                    }
                },
            )
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("refreshSession")) {
                        refreshRequests += request
                        val version = refreshRequests.size
                        jsonResponse(
                            HttpStatusCode.OK,
                            """{"accessJwt":"access-new-$version","refreshJwt":"refresh-new-$version","handle":"alice.bsky.social","did":"did:plc:alice"}""",
                        )
                    } else {
                        requests += request
                        apiResponse(request)
                    }
                },
            ) {
                install(ContentNegotiation) { json(BlueskyJson) }
                install(BlueskyAuthPlugin) {
                    authTokenFlow = credentials
                    onAuthTokensChanged = { credentials.value = it }
                    oauthApi = OAuthApi(oauthClient, challengeSelector = { OAuthCodeChallengeMethodS256 })
                }
            }
        try {
            Session(client, credentials, requests, refreshRequests).block()
        } finally {
            client.close()
            oauthClient.close()
        }
    }

    private class Session(
        val client: HttpClient,
        val credentials: MutableStateFlow<BlueskyCredential>,
        val requests: List<HttpRequestData>,
        val refreshRequests: List<HttpRequestData>,
    )

    private fun HttpRequestData.dpopNonce(): String? {
        val proof = assertNotNull(headers["DPoP"])
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(proof.split('.')[1])
        return BlueskyJson
            .parseToJsonElement(payload.decodeToString())
            .jsonObject["nonce"]
            ?.jsonPrimitive
            ?.content
    }

    private fun MockRequestHandleScope.jsonResponse(
        status: HttpStatusCode,
        body: String,
        nonce: String? = null,
    ) = respond(
        content = body,
        status = status,
        headers =
            Headers.build {
                append(HttpHeaders.ContentType, "application/json")
                nonce?.let { append("DPoP-Nonce", it) }
            },
    )

    private fun MockRequestHandleScope.authenticationChallenge(
        body: String = "",
        nonce: String? = "pds-nonce-1",
        authenticate: List<String> = listOf("""DPoP error="use_dpop_nonce""""),
    ) = respond(
        content = body,
        status = HttpStatusCode.Unauthorized,
        headers =
            Headers.build {
                append(HttpHeaders.ContentType, "text/plain")
                authenticate.forEach { append(HttpHeaders.WWWAuthenticate, it) }
                nonce?.let { append("DPoP-Nonce", it) }
            },
    )

    private companion object {
        const val TIMELINE = "https://pds.example/xrpc/app.bsky.feed.getTimeline"
    }
}
