package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import sh.christian.ozone.api.Did
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class OAuthPdsResolverTest {
    @Test
    fun oauthTokenUsesSubjectPdsInsteadOfAuthorizationIssuer() =
        runTest {
            val resolverRequests = mutableListOf<String>()
            val resolverClient =
                resolverClient(
                    did = "did:plc:alice",
                    pdsUrl = "https://puffball.us-east.host.bsky.network:8443",
                    issuer = "https://bsky.social",
                    requests = resolverRequests,
                )
            val resolvedToken =
                oauthToken(
                    subject = "did:plc:alice",
                    pdsUrl = "https://bsky.social",
                ).withResolvedPds("https://bsky.social", resolverClient)
            var apiRequestHost: String? = null
            var apiRequestPort: Int? = null
            val credentialFlow =
                MutableStateFlow<BlueskyCredential>(
                    BlueskyCredential.OAuthCredential(
                        baseUrl = "https://bsky.social",
                        oAuthToken = resolvedToken,
                        pdsUrlVerified = true,
                    ),
                )
            val apiClient =
                HttpClient(
                    MockEngine { request ->
                        apiRequestHost = request.url.host
                        apiRequestPort = request.url.port
                        val authorization = request.headers[HttpHeaders.Authorization]
                        if (request.url.host == "bsky.social" && authorization != null) {
                            respond(
                                content =
                                    """{"error":"InvalidToken","message":"OAuth tokens are meant for PDS access only"}""",
                                status = HttpStatusCode.Unauthorized,
                                headers = jsonHeaders,
                            )
                        } else {
                            respond(
                                content = """{"ok":true}""",
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders,
                            )
                        }
                    },
                ) {
                    install(BlueskyAuthPlugin) {
                        accountKey = MicroBlogKey("did:plc:alice", "bsky.social")
                        authTokenFlow = credentialFlow
                        onAuthTokensChanged = { credentialFlow.value = it }
                        oauthApi =
                            OAuthApi(
                                httpClient = resolverClient,
                                challengeSelector = { OAuthCodeChallengeMethodS256 },
                            )
                    }
                }

            try {
                val body =
                    apiClient
                        .get("https://bsky.social/xrpc/com.atproto.server.getSession")
                        .bodyAsText()

                assertEquals("""{"ok":true}""", body)
                assertEquals("puffball.us-east.host.bsky.network", apiRequestHost)
                assertEquals(8443, apiRequestPort)
                assertEquals(
                    listOf(
                        "plc.directory/did:plc:alice",
                        "puffball.us-east.host.bsky.network/.well-known/oauth-protected-resource",
                        "bsky.social/.well-known/oauth-authorization-server",
                    ),
                    resolverRequests,
                )
            } finally {
                apiClient.close()
                resolverClient.close()
            }
        }

    @Test
    fun rejectsPdsBoundToDifferentAuthorizationServer() =
        runTest {
            val resolverClient =
                resolverClient(
                    did = "did:plc:alice",
                    pdsUrl = "https://pds.example",
                    issuer = "https://evil.example",
                )

            try {
                val error =
                    assertFailsWith<IllegalStateException> {
                        oauthToken(subject = "did:plc:alice")
                            .withResolvedPds("https://bsky.social", resolverClient)
                    }

                assertTrue(error.message.orEmpty().contains("is bound to OAuth issuer"))
            } finally {
                resolverClient.close()
            }
        }

    @Test
    fun rejectsAuthorizationServerMetadataIssuerMismatch() =
        runTest {
            val resolverClient =
                resolverClient(
                    did = "did:plc:alice",
                    pdsUrl = "https://pds.example",
                    issuer = "https://auth.example",
                    metadataIssuer = "https://evil.example",
                )

            try {
                val error =
                    assertFailsWith<IllegalStateException> {
                        oauthToken(subject = "did:plc:alice")
                            .withResolvedPds("https://auth.example", resolverClient)
                    }

                assertTrue(error.message.orEmpty().contains("metadata issuer"))
            } finally {
                resolverClient.close()
            }
        }

    @Test
    fun usesFirstMatchingPdsService() =
        runTest {
            val requests = mutableListOf<String>()
            val resolverClient =
                resolverClient(
                    did = "did:plc:alice",
                    pdsUrl = "https://first-pds.example",
                    issuer = "https://auth.example",
                    requests = requests,
                    pdsServiceEndpoints =
                        listOf(
                            "https://first-pds.example",
                            "https://second-pds.example",
                        ),
                )

            try {
                val resolvedToken =
                    oauthToken(subject = "did:plc:alice")
                        .withResolvedPds("https://auth.example", resolverClient)

                assertEquals("https://first-pds.example", resolvedToken.pdsUrl)
                assertEquals(
                    listOf(
                        "plc.directory/did:plc:alice",
                        "first-pds.example/.well-known/oauth-protected-resource",
                        "auth.example/.well-known/oauth-authorization-server",
                    ),
                    requests,
                )
            } finally {
                resolverClient.close()
            }
        }

    @Test
    fun resolvesHostnameDidWebDocument() =
        runTest {
            val requests = mutableListOf<String>()
            val resolverClient =
                resolverClient(
                    did = "did:web:pds.example",
                    pdsUrl = "https://pds.example",
                    issuer = "https://auth.example",
                    requests = requests,
                )

            try {
                val resolvedToken =
                    oauthToken(subject = "did:web:pds.example")
                        .withResolvedPds("https://auth.example", resolverClient)

                assertEquals("https://pds.example", resolvedToken.pdsUrl)
                assertEquals(
                    listOf(
                        "pds.example/.well-known/did.json",
                        "pds.example/.well-known/oauth-protected-resource",
                        "auth.example/.well-known/oauth-authorization-server",
                    ),
                    requests,
                )
            } finally {
                resolverClient.close()
            }
        }

    @Test
    fun rejectsPathBasedDidWeb() =
        runTest {
            val requests = mutableListOf<String>()
            val resolverClient =
                resolverClient(
                    did = "did:web:pds.example:users:alice",
                    pdsUrl = "https://pds.example",
                    issuer = "https://auth.example",
                    requests = requests,
                )

            try {
                val error =
                    assertFailsWith<IllegalArgumentException> {
                        oauthToken(subject = "did:web:pds.example:users:alice")
                            .withResolvedPds("https://auth.example", resolverClient)
                    }

                assertTrue(error.message.orEmpty().contains("hostname-level did:web"))
                assertTrue(requests.isEmpty())
            } finally {
                resolverClient.close()
            }
        }

    private suspend fun oauthToken(
        subject: String,
        pdsUrl: String = "https://auth.example",
    ) = OAuthToken(
        accessToken = "oauth-access",
        refreshToken = "oauth-refresh",
        keyPair = DpopKeyPair.generateKeyPair(),
        expiresIn = 10.minutes,
        scopes = listOf(OAuthScope.AtProto, OAuthScope.Generic),
        subject = Did(subject),
        nonce = "nonce",
        clientId = "https://example.com/client-metadata.json",
        pdsUrl = pdsUrl,
    )

    private fun resolverClient(
        did: String,
        pdsUrl: String,
        issuer: String,
        requests: MutableList<String> = mutableListOf(),
        pdsServiceEndpoints: List<String> = listOf(pdsUrl),
        metadataIssuer: String = issuer,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                requests += "${request.url.host}${request.url.encodedPath}"
                when (request.url.encodedPath) {
                    "/did:plc:alice", "/.well-known/did.json" -> {
                        val services =
                            pdsServiceEndpoints.joinToString(separator = ",") { endpoint ->
                                """
                                {
                                  "id": "#atproto_pds",
                                  "type": "AtprotoPersonalDataServer",
                                  "serviceEndpoint": "$endpoint"
                                }
                                """.trimIndent()
                            }
                        respond(
                            content =
                                """
                                {
                                  "@context": ["https://www.w3.org/ns/did/v1"],
                                  "id": "$did",
                                  "service": [
                                    $services
                                  ]
                                }
                                """.trimIndent(),
                            headers = jsonHeaders,
                        )
                    }

                    "/.well-known/oauth-protected-resource" -> {
                        respond(
                            content =
                                """
                                {
                                  "resource": "$pdsUrl",
                                  "authorization_servers": ["$issuer"]
                                }
                                """.trimIndent(),
                            headers = jsonHeaders,
                        )
                    }

                    "/.well-known/oauth-authorization-server" -> {
                        respond(
                            content =
                                """
                                {
                                  "issuer": "$metadataIssuer"
                                }
                                """.trimIndent(),
                            headers = jsonHeaders,
                        )
                    }

                    else -> {
                        error("Unexpected resolver request: ${request.url}")
                    }
                }
            },
        )

    private val jsonHeaders =
        Headers.build {
            append(HttpHeaders.ContentType, "application/json")
        }
}
