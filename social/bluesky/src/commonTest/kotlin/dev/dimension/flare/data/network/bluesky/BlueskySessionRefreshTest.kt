package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.StatusCode
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class BlueskySessionRefreshTest {
    @Test
    fun passwordRefreshServiceUnavailableMustNotExpireLogin() =
        runTest {
            transientRefreshFailure(oauth = false, status = HttpStatusCode.ServiceUnavailable)
        }

    @Test
    fun passwordRefreshRateLimitMustNotExpireLogin() =
        runTest {
            transientRefreshFailure(oauth = false, status = HttpStatusCode.TooManyRequests)
        }

    @Test
    fun oauthRefreshServiceUnavailableMustNotExpireLogin() =
        runTest {
            transientRefreshFailure(oauth = true, status = HttpStatusCode.ServiceUnavailable)
        }

    @Test
    fun oauthRefreshRateLimitPreservesOriginalError() =
        runTest {
            transientRefreshFailure(oauth = true, status = HttpStatusCode.TooManyRequests)
        }

    @Test
    fun networkFailurePreservesSessionAndOriginalError() =
        runTest {
            for (oauth in listOf(false, true)) {
                val networkError = IOException("connection interrupted")
                val failure = refreshFailure(oauth) { throw networkError }
                assertIs<IOException>(failure)
                assertEquals(networkError.message, failure.message)
            }
        }

    @Test
    fun cancelledRefreshPropagatesCancellation() =
        runTest {
            for (oauth in listOf(false, true)) {
                val cancellation = CancellationException("request cancelled")
                val failure = refreshFailure(oauth) { throw cancellation }
                assertIs<CancellationException>(failure)
            }
        }

    @Test
    fun invalidPasswordRefreshTokenRequiresLogin() =
        runTest {
            for (error in listOf("ExpiredToken", "InvalidToken")) {
                val failure =
                    refreshFailure(oauth = false) {
                        jsonResponse(HttpStatusCode.Unauthorized, """{"error":"$error","message":"refresh token rejected"}""")
                    }
                assertIs<LoginExpiredException>(failure)
            }
        }

    @Test
    fun invalidOAuthRefreshGrantRequiresLogin() =
        runTest {
            val failure =
                refreshFailure(oauth = true) {
                    jsonResponse(HttpStatusCode.BadRequest, """{"error":"invalid_grant","message":"refresh token rejected"}""")
                }
            assertIs<LoginExpiredException>(failure)
        }

    @Test
    fun invalidOAuthClientPreservesConfigurationError() =
        runTest {
            val failure =
                refreshFailure(oauth = true) {
                    jsonResponse(HttpStatusCode.BadRequest, """{"error":"invalid_client","message":"client metadata unavailable"}""")
                }
            assertIs<AtpException>(failure)
            assertEquals("invalid_client", failure.error?.error)
        }

    @Test
    fun nonceRetryLimitPreservesSessionAndServerResponse() =
        runTest {
            val original = oauthCredential()
            val credentials = MutableStateFlow<BlueskyCredential>(original)
            var requests = 0
            val oauthClient = HttpClient(MockEngine { error("Nonce retries must not contact the authorization server") })
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        jsonResponse(HttpStatusCode.BadRequest, """{"error":"use_dpop_nonce"}""", "nonce-$requests")
                    },
                ) {
                    install(BlueskyAuthPlugin) {
                        authTokenFlow = credentials
                        onAuthTokensChanged = { credentials.value = it }
                        oauthApi = OAuthApi(oauthClient, challengeSelector = { OAuthCodeChallengeMethodS256 })
                    }
                }
            try {
                val response = client.get("https://pds.example/xrpc/app.bsky.feed.getTimeline")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("""{"error":"use_dpop_nonce"}""", response.bodyAsText())
                assertEquals(4, requests)
                assertEquals(original.accessToken, credentials.value.accessToken)
                assertEquals(original.refreshToken, credentials.value.refreshToken)
            } finally {
                client.close()
                oauthClient.close()
            }
        }

    @Test
    fun lateNonceResponseMustNotRestoreAlreadyConsumedRefreshToken() =
        runTest {
            lateResponseKeepsRefreshedTokens(nonceChallenge = true)
        }

    @Test
    fun lateErrorResponseMustNotRestoreAlreadyConsumedRefreshToken() =
        runTest {
            lateResponseKeepsRefreshedTokens(nonceChallenge = false)
        }

    @Test
    fun lateSuccessfulResponseMustNotRestoreAlreadyConsumedRefreshToken() =
        runTest {
            lateResponseKeepsRefreshedTokens(nonceChallenge = false, successfulResponse = true)
        }

    private suspend fun lateResponseKeepsRefreshedTokens(
        nonceChallenge: Boolean,
        successfulResponse: Boolean = false,
    ) = withContext(Dispatchers.Default) {
        val credentials = MutableStateFlow<BlueskyCredential>(oauthCredential())
        val parkedRequest = CompletableDeferred<Unit>()
        val releaseOldResponse = CompletableDeferred<Unit>()
        val writtenAccessTokens = mutableListOf<String>()
        var refreshCalls = 0
        var oldProfileCalls = 0
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
                            refreshCalls++
                            if (refreshCalls == 1) {
                                jsonResponse(
                                    HttpStatusCode.OK,
                                    """{"access_token":"access-new","token_type":"DPoP","expires_in":3600,"refresh_token":"refresh-new","scope":"atproto","sub":"did:plc:alice"}""",
                                    "auth-nonce-new",
                                )
                            } else {
                                jsonResponse(
                                    HttpStatusCode.BadRequest,
                                    """{"error":"invalid_grant","message":"refresh token already consumed"}""",
                                    "auth-nonce-new",
                                )
                            }
                        }

                        else -> {
                            error("Unexpected OAuth path: ${request.url.encodedPath}")
                        }
                    }
                },
            )
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.headers[HttpHeaders.Authorization] == "DPoP access-new") {
                        jsonResponse(HttpStatusCode.OK, """{"ok":true}""", "pds-nonce-new")
                    } else if (request.url.encodedPath.endsWith("getProfile") && ++oldProfileCalls == 1) {
                        parkedRequest.complete(Unit)
                        withTimeout(5_000) { releaseOldResponse.await() }
                        val error = if (nonceChallenge) "use_dpop_nonce" else "InvalidRequest"
                        jsonResponse(
                            if (successfulResponse) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                            if (successfulResponse) """{"ok":true}""" else """{"error":"$error"}""",
                            "pds-nonce-old",
                        )
                    } else {
                        jsonResponse(
                            HttpStatusCode.Unauthorized,
                            """{"error":"invalid_token","message":"token expired"}""",
                            "pds-nonce-new",
                        )
                    }
                },
            ) {
                install(BlueskyAuthPlugin) {
                    accountKey = MicroBlogKey("did:plc:alice", "auth.example")
                    authTokenFlow = credentials
                    onAuthTokensChanged = {
                        writtenAccessTokens += it.accessToken
                        credentials.value = it
                    }
                    oauthApi = OAuthApi(oauthClient, challengeSelector = { OAuthCodeChallengeMethodS256 })
                }
            }
        try {
            val lateResult =
                supervisorScope {
                    val lateRequest =
                        async {
                            runCatching { client.get("https://pds.example/xrpc/app.bsky.actor.getProfile") }
                        }
                    withTimeout(5_000) { parkedRequest.await() }
                    client.get("https://pds.example/xrpc/app.bsky.feed.getTimeline")
                    releaseOldResponse.complete(Unit)
                    lateRequest.await()
                }
            assertEquals(
                "access-new",
                credentials.value.accessToken,
                "Late nonce response rolled tokens backwards: $writtenAccessTokens; refreshCalls=$refreshCalls; error=${lateResult.exceptionOrNull()}",
            )
            assertEquals(1, refreshCalls)
            assertTrue(lateResult.isSuccess)
            assertEquals(
                if (nonceChallenge || successfulResponse) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                lateResult.getOrThrow().status,
            )
            val current = credentials.value as BlueskyCredential.OAuthCredential
            assertEquals("auth-nonce-new", current.oAuthToken.nonce)
            assertEquals("pds-nonce-new", current.pdsNonce)
        } finally {
            client.close()
            oauthClient.close()
        }
    }

    private suspend fun transientRefreshFailure(
        oauth: Boolean,
        status: HttpStatusCode,
    ) {
        val failure =
            refreshFailure(oauth) {
                jsonResponse(status, """{"error":"temporarily_unavailable","message":"try again later"}""")
            }
        assertIs<AtpException>(failure)
        assertEquals(StatusCode.fromCode(status.value), failure.statusCode)
        assertEquals("temporarily_unavailable", failure.error?.error)
    }

    private suspend fun refreshFailure(
        oauth: Boolean,
        respondToRefresh: suspend MockRequestHandleScope.() -> HttpResponseData,
    ): Throwable {
        val original: BlueskyCredential =
            if (oauth) {
                oauthCredential()
            } else {
                BlueskyCredential.Password("https://auth.example", "access-old", "refresh-old")
            }
        val credentials = MutableStateFlow(original)
        var refreshCalls = 0
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
                            refreshCalls++
                            respondToRefresh()
                        }

                        else -> {
                            error("Unexpected OAuth path: ${request.url.encodedPath}")
                        }
                    }
                },
            )
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("refreshSession")) {
                        refreshCalls++
                        respondToRefresh()
                    } else {
                        val error = if (oauth) "invalid_token" else "ExpiredToken"
                        jsonResponse(HttpStatusCode.Unauthorized, """{"error":"$error","message":"access token expired"}""")
                    }
                },
            ) {
                install(ContentNegotiation) { json(BlueskyJson) }
                install(BlueskyAuthPlugin) {
                    accountKey = MicroBlogKey("did:plc:alice", "auth.example")
                    authTokenFlow = credentials
                    onAuthTokensChanged = { credentials.value = it }
                    oauthApi = OAuthApi(oauthClient, challengeSelector = { OAuthCodeChallengeMethodS256 })
                }
            }
        try {
            val result = runCatching { client.get("https://auth.example/xrpc/app.bsky.feed.getTimeline") }
            assertEquals(1, refreshCalls)
            assertEquals(original, credentials.value)
            return assertNotNull(result.exceptionOrNull())
        } finally {
            client.close()
            oauthClient.close()
        }
    }

    private suspend fun oauthCredential() =
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
                    pdsUrl = "https://pds.example",
                ),
            pdsUrlVerified = true,
        )

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
}
