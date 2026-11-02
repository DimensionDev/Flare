package dev.dimension.flare.data.network.bluesky

import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.VideoView
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.startProxyTestStore
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.context.GlobalContext
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.oauth.DpopKeyPair
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BlueskyProxyRoutingTest {
    private val account = MicroBlogKey("did:plc:proxy-routing-test", "bsky.social")
    private val proxy = ProxyRef.forAccount(account)

    @Test
    fun linkMetadataAndThumbnailUseTheSourceAccount(): Unit =
        runBlocking {
            fixture { connections ->
                val resolver = BlueskyLinkCardResolver(proxy)
                resolver.resolve("https://example.test/article")
                resolver.fetchImage("https://cardyb.bsky.app/v1/image?url=https%3A%2F%2Fexample.test%2Fcard.png")
                assertEquals(listOf("account:cardyb.bsky.app:443", "account:cardyb.bsky.app:443"), connections.toList())
            }
        }

    @Test
    fun videoDownloadDiscoveryUsesTheTimelineAccount(): Unit =
        runBlocking {
            fixture { connections ->
                val post =
                    PostView(
                        uri = AtUri("at://${account.id}/app.bsky.feed.post/1"),
                        cid = Cid("post"),
                        author = ProfileViewBasic(did = Did(account.id), handle = Handle("author.test")),
                        record = BlueskyJson.encodeAsJsonContent(buildJsonObject {}),
                        indexedAt = Instant.fromEpochMilliseconds(0),
                        embed =
                            PostViewEmbedUnion.VideoView(
                                VideoView(cid = Cid("video"), playlist = Uri("https://video.example/playlist.m3u8")),
                            ),
                    )
                resolveBlueskyVideoDownloadUrls(listOf(post), account)
                assertEquals(listOf("account:plc.directory:443"), connections.toList())
            }
        }

    @Test
    fun oauthPdsDiscoveryUsesExplicitScopeForLoginAndRelogin(): Unit =
        runBlocking {
            fixture { connections ->
                val token = token()
                runCatching { token.withResolvedPds("https://bsky.social", proxy) }
                runCatching { token.withResolvedPds("https://bsky.social", ProxyRef.Global) }
                assertEquals(listOf("account:plc.directory:443", "global:plc.directory:443"), connections.toList())
            }
        }

    @Test
    fun legacyOAuthPdsMigrationKeepsTheServiceAccount(): Unit =
        runBlocking {
            fixture { connections ->
                val credential = BlueskyCredential.OAuthCredential("https://bsky.social", token(), pdsUrlVerified = false)
                runCatching { BlueskyService(account, flowOf(credential)) {}.getSession() }
                assertEquals(listOf("account:plc.directory:443"), connections.toList())
            }
        }

    @Test
    fun delegationRepairKeepsTheReloginScope(): Unit =
        runBlocking {
            fixture { connections ->
                val credential = BlueskyCredential.OAuthCredential("https://bsky.social", token(), pdsUrlVerified = true)
                runCatching { repairTranquilDelegationScopes(credential, "did:plc:controller", proxy) }
                assertEquals(listOf("account:bsky.social:443"), connections.toList())
            }
        }

    private suspend fun token() =
        OAuthToken(
            accessToken = "proxy-test-access",
            refreshToken = "proxy-test-refresh",
            keyPair = DpopKeyPair.generateKeyPair(),
            expiresIn = 10.minutes,
            scopes = listOf(OAuthScope.AtProto, OAuthScope.Generic),
            subject = Did(account.id),
            nonce = "nonce",
            clientId = "https://example.test/client-metadata.json",
            pdsUrl = "https://bsky.social",
        )

    private suspend fun fixture(block: suspend (CopyOnWriteArrayList<String>) -> Unit) {
        val connections = CopyOnWriteArrayList<String>()
        val global = refusingProxy("global", connections)
        val scoped = refusingProxy("account", connections)
        val store = startProxyTestStore()
        val repository = GlobalContext.get().get<ProxyRepository>()
        try {
            repository.save(ProxyProfile("global", "Global", ProxyProtocol.SOCKS5, "127.0.0.1", global.localPort))
            repository.save(ProxyProfile("account", "Account", ProxyProtocol.SOCKS5, "127.0.0.1", scoped.localPort))
            repository.selectGlobal(ProxyPolicy(ProxyMode.PROXY, "global"))
            repository.selectAccount(account, ProxyPolicy(ProxyMode.PROXY, "account"))
            withTimeout(10_000) { block(connections) }
        } finally {
            repository.selectAccount(account, ProxyPolicy(ProxyMode.DIRECT))
            repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
            global.close()
            scoped.close()
            store.close()
        }
    }

    private fun refusingProxy(
        name: String,
        connections: MutableList<String>,
    ): ServerSocket {
        val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        thread(isDaemon = true) {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val input = socket.getInputStream()
                        val output = socket.getOutputStream()
                        check(input.read() == 5)
                        repeat(input.read()) { input.read() }
                        output.write(byteArrayOf(5, 0))
                        output.flush()
                        check(input.read() == 5 && input.read() == 1 && input.read() == 0 && input.read() == 3)
                        val hostname = ByteArray(input.read())
                        hostname.indices.forEach { hostname[it] = input.read().toByte() }
                        val port = (input.read() shl 8) + input.read()
                        connections += "$name:${hostname.toString(Charsets.US_ASCII)}:$port"
                        // Reject before TLS: the regression must never contact a real service.
                        output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                        output.flush()
                    }
                }
            }
        }
        return server
    }
}
