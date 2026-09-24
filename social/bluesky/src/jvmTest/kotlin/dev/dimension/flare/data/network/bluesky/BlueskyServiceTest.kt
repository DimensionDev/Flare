package dev.dimension.flare.data.network.bluesky

import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import sh.christian.ozone.api.Did
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class BlueskyServiceTest {
    @Test
    fun videoServiceAuthRequestsPdsAudienceAndBlobUploadScope() =
        runBlocking {
            val pdsServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val pdsOrigin = "http://127.0.0.1:${pdsServer.address.port}"
            val requests = Collections.synchronizedList(mutableListOf<Url>())
            pdsServer.createContext("/xrpc/com.atproto.server.getServiceAuth") { exchange ->
                requests += Url("$pdsOrigin${exchange.requestURI}")
                // Stop at the auth boundary so this test never contacts the real video service.
                val body = """{"error":"InvalidRequest","message":"Service auth disabled for this test"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(400, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            pdsServer.start()
            try {
                val service = BlueskyService(pdsOrigin)
                val media = UploadMedia.fromBytes("clip.mp4", byteArrayOf(1))
                withTimeout(10_000) {
                    val error = assertFailsWith<IllegalArgumentException> { service.uploadVideo(media) }
                    assertTrue(error.message.orEmpty().contains("Service auth disabled for this test"))
                }
                val query = requests.single().parameters
                assertEquals("did:web:127.0.0.1", query["aud"])
                assertEquals("com.atproto.repo.uploadBlob", query["lxm"])
            } finally {
                pdsServer.stop(0)
            }
        }

    @Test
    fun switchingResourceServerMustKeepOAuthRefreshOnIssuer() =
        runBlocking {
            assertOAuthRefreshRouting(separateTokenEndpoint = false)
        }

    @Test
    fun metadataTokenEndpointMustKeepItsOwnOrigin() =
        runBlocking {
            assertOAuthRefreshRouting(separateTokenEndpoint = true)
        }

    private suspend fun assertOAuthRefreshRouting(separateTokenEndpoint: Boolean) {
        val authServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val pdsServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val tokenServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val authOrigin = "http://127.0.0.1:${authServer.address.port}"
        val pdsOrigin = "http://127.0.0.1:${pdsServer.address.port}"
        val tokenOrigin = if (separateTokenEndpoint) "http://127.0.0.1:${tokenServer.address.port}" else authOrigin
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val tokenBody =
            """{"access_token":"access-new","token_type":"DPoP","expires_in":3600,
            |"refresh_token":"refresh-new","scope":"atproto","sub":"did:plc:alice"}
            """.trimMargin()
        authServer.createContext("/") { exchange ->
            requests += "issuer:${exchange.requestURI.path}"
            val body =
                when (exchange.requestURI.path) {
                    "/.well-known/oauth-authorization-server" -> {
                        """{"issuer":"$authOrigin","authorization_endpoint":"$authOrigin/authorize","token_endpoint":"$tokenOrigin/token"}"""
                    }

                    "/token" -> {
                        tokenBody
                    }

                    else -> {
                        error("Unexpected issuer path")
                    }
                }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("DPoP-Nonce", "auth-nonce")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        tokenServer.createContext("/token") { exchange ->
            requests += "token:${exchange.requestURI.path}"
            val bytes = tokenBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("DPoP-Nonce", "auth-nonce")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        pdsServer.createContext("/") { exchange ->
            requests += "pds:${exchange.requestURI.path}"
            val (status, body) =
                when {
                    exchange.requestURI.path != "/xrpc/com.atproto.server.getSession" -> {
                        404 to """{"error":"NotFound"}"""
                    }

                    exchange.requestHeaders.getFirst("Authorization") == "DPoP access-old" -> {
                        401 to """{"error":"invalid_token","message":"token expired"}"""
                    }

                    else -> {
                        200 to """{"did":"did:plc:alice","handle":"alice.bsky.social"}"""
                    }
                }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("DPoP-Nonce", "pds-nonce")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        authServer.start()
        pdsServer.start()
        tokenServer.start()
        try {
            val credentials =
                MutableStateFlow<BlueskyCredential>(
                    BlueskyCredential.OAuthCredential(
                        baseUrl = authOrigin,
                        oAuthToken =
                            OAuthToken(
                                accessToken = "access-old",
                                refreshToken = "refresh-old",
                                keyPair = DpopKeyPair.generateKeyPair(),
                                expiresIn = 10.minutes,
                                scopes = listOf(OAuthScope.AtProto),
                                subject = Did("did:plc:alice"),
                                nonce = "auth-nonce",
                                clientId = "https://client.example/metadata.json",
                                pdsUrl = pdsOrigin,
                            ),
                        pdsUrlVerified = true,
                    ),
                )
            val service =
                BlueskyService(
                    accountKey = MicroBlogKey("did:plc:alice", "auth.example"),
                    credentialFlow = credentials,
                    onCredentialRefreshed = { credentials.value = it },
                ).newBaseUrlService(pdsOrigin)
            val result = runCatching { withTimeout(10_000) { service.getSession().requireResponse() } }
            assertTrue(
                result.isSuccess,
                "Expected refresh on issuer. Observed $requests; error=${result.exceptionOrNull()?.javaClass?.simpleName}",
            )
            assertEquals(
                listOf(
                    "pds:/xrpc/com.atproto.server.getSession",
                    "issuer:/.well-known/oauth-authorization-server",
                    if (separateTokenEndpoint) "token:/token" else "issuer:/token",
                    "pds:/xrpc/com.atproto.server.getSession",
                ),
                requests.toList(),
            )
            assertEquals("access-new", credentials.value.accessToken)
            assertEquals("refresh-new", credentials.value.refreshToken)
        } finally {
            authServer.stop(0)
            pdsServer.stop(0)
            tokenServer.stop(0)
        }
    }
}
