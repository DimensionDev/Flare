package dev.dimension.flare.data.network

import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProxyRoutingTest {
    @Test
    fun accountRoutesAndRelayFollowPolicyWithoutDirectFallback(): Unit =
        runBlocking {
            val root = createTestRootPath()
            val repository = ProxyRepository(OkioFileStorage(createTestFileSystem(), root))
            val calls = CopyOnWriteArrayList<Pair<String, String?>>()
            val cookies = CopyOnWriteArrayList<Pair<String, String?>>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
            server.executor = executor
            server.createContext("/") { exchange ->
                calls += exchange.requestURI.path to exchange.requestHeaders.getFirst("Authorization")
                cookies += exchange.requestURI.path to exchange.requestHeaders.getFirst("Cookie")
                if (exchange.requestURI.path == "/redirect") {
                    exchange.responseHeaders.add("Location", "http://localhost:${server.address.port}/credential-target")
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                    return@createContext
                }
                val playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST"
                val body =
                    when (exchange.requestURI.path) {
                        "/video.m3u8" -> playlist
                        "/range" -> "2345"
                        else -> "OK"
                    }.toByteArray()
                exchange.responseHeaders.add(
                    "Content-Type",
                    if (exchange.requestURI.path.endsWith("m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t",
                )
                val status =
                    if (exchange.requestURI.path == "/range") {
                        assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
                        exchange.responseHeaders.add("Content-Range", "bytes 2-5/10")
                        206
                    } else {
                        200
                    }
                if (exchange.requestMethod == "HEAD") {
                    exchange.responseHeaders.add("Content-Length", body.size.toString())
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    exchange.sendResponseHeaders(status, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            server.start()
            val base = "http://127.0.0.1:${server.address.port}"
            val local = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
            val alice = ProxyRef.Account(MicroBlogKey("proxy-test-alice", "example.social"))
            val bob = ProxyRef.Account(MicroBlogKey("proxy-test-bob", "example.social"))
            val unavailable = ServerSocket(0).use { it.localPort }
            val profile = ProxyProfile("test", "Unavailable", ProxyProtocol.SOCKS5, "127.0.0.1", unavailable)
            startKoin { modules(module { testSingle<ProxyRepository> { repository } }) }
            try {
                repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                repository.save(profile)
                repository.selectAccount(bob.accountKey, ProxyPolicy(ProxyMode.PROXY, profile.id))
                val original = ProxyNetwork.snapshot(bob)
                repository.save(profile.copy(name = "Renamed"))
                assertEquals(original, ProxyNetwork.snapshot(bob))
                coroutineScope {
                    val success = async { ktorClient(alice).use { it.get("$base/alice").bodyAsText() } }
                    val failure = async { ktorClient(bob).use { assertFails { it.get("$base/bob") } } }
                    assertEquals("OK", success.await())
                    failure.await()
                }
                assertEquals(listOf("/alice"), calls.map { it.first })

                val authenticated =
                    UiMedia.Image(
                        "$base/redirect",
                        persistentMapOf("Authorization" to "Bearer private", "Cookie" to "secret=private"),
                        alice,
                    )
                ktorClient(alice).use { client ->
                    assertEquals("OK", client.get(authenticated.url) { applyMedia(authenticated) }.bodyAsText())
                }
                assertEquals("Bearer private", calls[calls.lastIndex - 1].second)
                assertEquals("/credential-target" to null, calls.last())
                assertEquals("/credential-target" to null, cookies.last())
                val redirected = MediaProxy.playbackSource(authenticated)
                try {
                    local.newCall(Request.Builder().url(redirected.url).build()).execute().use { assertEquals("OK", it.body.string()) }
                    assertEquals("/credential-target" to null, calls.last())
                    assertEquals("/credential-target" to null, cookies.last())
                } finally {
                    MediaProxy.release(redirected)
                }

                val media =
                    UiMedia.Video(
                        "$base/video.m3u8",
                        "",
                        null,
                        0f,
                        0f,
                        customHeaders = persistentMapOf("Authorization" to "Bearer test"),
                        proxy = alice,
                    )
                val source = MediaProxy.playbackSource(media)
                val lease = MediaProxy.playbackSource(media)
                assertEquals(source, lease)
                val playlist =
                    local.newCall(Request.Builder().url(source.url).build()).execute().use {
                        assertEquals(200, it.code)
                        requireNotNull(it.body).string()
                    }
                val segment = playlist.lineSequence().first { it.startsWith("http") }
                assertTrue(segment.startsWith("http://127.0.0.1:"))
                assertTrue(!playlist.contains("segment.ts"))
                MediaProxy.release(lease)
                local.newCall(Request.Builder().url(segment).build()).execute().use { assertEquals("OK", it.body.string()) }
                assertEquals("Bearer test", calls.last().second)
                MediaProxy.release(source)
                local.newCall(Request.Builder().url(segment).build()).execute().use { assertEquals(502, it.code) }

                val ranged = MediaProxy.playbackSource(media.copy(url = "$base/range"))
                local
                    .newCall(
                        Request
                            .Builder()
                            .url(ranged.url)
                            .header("Range", "bytes=2-5")
                            .build(),
                    ).execute()
                    .use {
                        assertEquals(206, it.code)
                        assertEquals("bytes 2-5/10", it.header("Content-Range"))
                        assertEquals("2345", it.body.string())
                    }
                repository.selectAccount(alice.accountKey, ProxyPolicy(ProxyMode.PROXY, profile.id))
                val count = calls.size
                local
                    .newCall(
                        Request
                            .Builder()
                            .url(ranged.url)
                            .header("Range", "bytes=2-5")
                            .build(),
                    ).execute()
                    .use { assertEquals(502, it.code) }
                assertEquals(count, calls.size)
                MediaProxy.release(ranged)
                assertNotEquals(original.key, ProxyNetwork.snapshot(alice).key)
                repository.removeAccount(alice.accountKey)
                assertFailsWith<IllegalStateException> { ProxyNetwork.snapshot(alice) }
                repository.restoreAccount(alice.accountKey)
                assertEquals(ProxyMode.DIRECT, ProxyNetwork.snapshot(alice).connection.mode)
            } finally {
                repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                stopKoin()
                server.stop(0)
                executor.shutdownNow()
                local.dispatcher.executorService.shutdown()
                local.connectionPool.evictAll()
                deleteTestRootPath(root)
            }
        }

    @Test
    fun playlistRewritesEverySupportedUriAndRejectsEscapes() {
        val urls = mutableListOf<String>()
        val rewritten =
            rewriteProxyPlaylist(
                "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key\"\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:1,\nseg.ts?x=1",
                "https://cdn.example/video/list.m3u8",
            ) { url, _ ->
                urls.add(url)
                "http://127.0.0.1/token-${urls.size}"
            }
        assertEquals(listOf("https://cdn.example/key", "https://cdn.example/video/init.mp4", "https://cdn.example/video/seg.ts?x=1"), urls)
        assertTrue(rewritten.contains("URI=\"http://127.0.0.1/token-1\""))
        for (unsupported in listOf(
            "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"",
            "#EXT-X-CONTENT-STEERING:SERVER-URI=\"https://escape.invalid\"",
            "#EXT-X-MAP:URI=https://escape.invalid",
            "file:///etc/passwd",
            "https://user:password@escape.invalid/segment",
            "#EXT-X-UNKNOWN:URI=\"segment\"",
        )) {
            assertFailsWith<UnsupportedProxyMedia> {
                rewriteProxyPlaylist(
                    "#EXTM3U\n$unsupported",
                    "https://cdn.example/list.m3u8",
                ) { url, _ -> url }
            }
        }
    }
}
