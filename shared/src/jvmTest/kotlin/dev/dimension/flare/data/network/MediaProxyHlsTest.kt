package dev.dimension.flare.data.network

import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MediaProxyHlsTest {
    private val key = ByteArray(16) { (it + 0x3c).toByte() }
    private val ciphertext = "<MPD>opaque ciphertext, not a manifest</MPD>".toByteArray().copyOf(64)
    private val playlist =
        """
        #EXTM3U
        #EXT-X-TARGETDURATION:2
        #EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES
        #EXT-X-PART-INF:PART-TARGET=0.5
        #EXT-X-KEY:METHOD=AES-128,URI="key"
        #EXT-X-MAP:URI="init.mp4"
        #EXT-X-PART:DURATION=0.5,URI="part.ts"
        #EXT-X-PRELOAD-HINT:TYPE=PART,URI="next.ts"
        #EXTINF:2,
        segment.ts
        #EXT-X-KEY:METHOD=NONE
        #EXTINF:2,
        error.ts
        """.trimIndent()

    @Test
    fun aesKeysAndEncryptedSegmentsPassThroughWithoutTextSniffing(): Unit =
        runBlocking {
            fixture { source, client, _ ->
                val body =
                    client.get(source.url).use {
                        assertEquals(200, it.code)
                        it.body.string()
                    }
                val attributes = Regex("URI=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
                client.get(attributes.first()).use {
                    assertEquals(200, it.code, MediaProxy.failureMessage(source.url))
                    assertContentEquals(key, it.body.bytes())
                }
                val segments = body.lineSequence().filter { it.startsWith("http") }.toList()
                for (url in attributes.drop(1) + segments.first()) {
                    client.get(url).use {
                        assertEquals(200, it.code, MediaProxy.failureMessage(source.url))
                        assertContentEquals(ciphertext, it.body.bytes())
                    }
                }
                // METHOD=NONE restores validation for unencrypted resources.
                client.get(segments.last()).use { assertEquals(502, it.code) }
            }
        }

    @Test
    fun lowLatencyReloadPreservesSignedQueryAndOnlyForwardsDeliveryDirectives(): Unit =
        runBlocking {
            for (path in listOf("/live.m3u8", "/playlist")) {
                fixture(path) { source, client, queries ->
                    client.get(source.url + "?_HLS_msn=12&_HLS_part=3&_HLS_skip=v2&token=replace&extra=ignored").use {
                        assertEquals(200, it.code)
                    }
                    assertEquals("token=keep%2Fsignature&nonce=a+b&_HLS_msn=12&_HLS_part=3&_HLS_skip=v2", queries.last())
                }
            }
        }

    @Test
    fun playlistMarksOnlyKeysAndEncryptedPayloadsOpaque() {
        val resources = mutableListOf<Pair<String, Boolean>>()
        rewriteProxyPlaylist(
            "#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"key\"\nvariant.m3u8",
            "https://cdn.example/master.m3u8",
        ) { url, opaque ->
            resources.add(url to opaque)
            url
        }
        assertEquals(listOf("https://cdn.example/key" to true, "https://cdn.example/variant.m3u8" to false), resources)
        resources.clear()
        rewriteProxyPlaylist(playlist, "https://cdn.example/list.m3u8") { url, opaque ->
            resources.add(url to opaque)
            url
        }
        assertEquals(listOf(true, true, true, true, true, false), resources.map { it.second })
    }

    private fun OkHttpClient.get(url: String) = newCall(Request.Builder().url(url).build()).execute()

    private suspend fun fixture(
        path: String = "/live.m3u8",
        block: (MediaPlaybackSource, OkHttpClient, CopyOnWriteArrayList<String>) -> Unit,
    ) {
        val root = createTestRootPath()
        val repository = ProxyRepository(OkioFileStorage(createTestFileSystem(), root))
        val queries = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            queries += exchange.requestURI.rawQuery.orEmpty()
            val body =
                when (exchange.requestURI.path) {
                    "/key" -> key
                    "/init.mp4", "/part.ts", "/next.ts", "/segment.ts" -> ciphertext
                    "/error.ts" -> "<html>Not media</html>".toByteArray()
                    else -> playlist.toByteArray()
                }
            exchange.responseHeaders.add(
                "Content-Type",
                if (exchange.requestURI.path ==
                    path
                ) {
                    "application/vnd.apple.mpegurl"
                } else {
                    "application/octet-stream"
                },
            )
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        val local = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
        startKoin { modules(module { testSingle<ProxyRepository> { repository } }) }
        try {
            repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
            val base = "http://127.0.0.1:${server.address.port}"
            val source =
                MediaProxy.playbackSource(
                    UiMedia.Video("$base$path?token=keep%2Fsignature&nonce=a+b&_HLS_msn=2", "", null, 0f, 0f, proxy = ProxyRef.Global),
                )
            try {
                block(source, local, queries)
            } finally {
                MediaProxy.release(source)
            }
        } finally {
            repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
            stopKoin()
            server.stop(0)
            local.dispatcher.executorService.shutdownNow()
            local.connectionPool.evictAll()
            deleteTestRootPath(root)
        }
    }
}
