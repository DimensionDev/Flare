package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyProtocol
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProxyTransportTest {
    @Test
    fun socksSendsTargetHostnameAndOnlyOffersNoAuthentication(): Unit =
        runBlocking {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val request = CompletableFuture<String>()
                thread(isDaemon = true) {
                    runCatching {
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val input = socket.getInputStream()
                            val output = socket.getOutputStream()
                            assertEquals(listOf(5, 1, 0), List(3) { input.read() })
                            output.write(byteArrayOf(5, 0))
                            assertEquals(listOf(5, 1, 0, 3), List(4) { input.read() })
                            val host = String(input.readNBytes(input.read()))
                            assertEquals(80, input.read() * 256 + input.read())
                            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80))
                            val reader = input.bufferedReader()
                            val line = reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { /* Drain request headers. */ }
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
                            request.complete("$host $line")
                        }
                    }.onFailure { request.completeExceptionally(it) }
                }
                val engine =
                    createPlatformHttpClientEngine(ProxyConnection(ProxyMode.PROXY, ProxyProtocol.SOCKS5, "127.0.0.1", server.localPort))
                try {
                    HttpClient(engine).use { client ->
                        assertEquals("OK", client.get("http://target-must-not-resolve.invalid/image").bodyAsText())
                    }
                    assertEquals("target-must-not-resolve.invalid GET /image HTTP/1.1", request.get(5, TimeUnit.SECONDS))
                } finally {
                    engine.close()
                }
            }
        }

    @Test
    fun directBypassesSystemProxyAndProxyFailureDoesNotRetryDirect(): Unit =
        runBlocking {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { target ->
                val system = ProxySelector.getDefault()
                val unavailable = ServerSocket(0).use { it.localPort }
                ProxySelector.setDefault(
                    object : ProxySelector() {
                        override fun select(uri: URI): List<Proxy> =
                            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", unavailable)))

                        override fun connectFailed(
                            uri: URI,
                            sa: SocketAddress,
                            ioe: java.io.IOException,
                        ) = Unit
                    },
                )
                val response = CompletableFuture<Unit>()
                thread(isDaemon = true) {
                    runCatching {
                        target.accept().use { socket ->
                            socket.soTimeout = 5000
                            val reader = socket.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { /* Drain request headers. */ }
                            socket.getOutputStream().write(
                                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray(),
                            )
                            response.complete(Unit)
                        }
                    }.onFailure { response.completeExceptionally(it) }
                }
                try {
                    val url = "http://127.0.0.1:${target.localPort}/"
                    for (route in listOf(
                        ProxyConnection(ProxyMode.SYSTEM),
                        ProxyConnection(ProxyMode.PROXY, ProxyProtocol.SOCKS5, "127.0.0.1", unavailable),
                    )) {
                        val engine = createPlatformHttpClientEngine(route)
                        try {
                            HttpClient(engine).use { client -> assertFails { client.get(url) } }
                        } finally {
                            engine.close()
                        }
                    }
                    val engine = createPlatformHttpClientEngine(ProxyConnection(ProxyMode.DIRECT))
                    try {
                        HttpClient(engine).use { assertEquals("OK", it.get(url).bodyAsText()) }
                    } finally {
                        engine.close()
                    }
                    response.get(5, TimeUnit.SECONDS)
                } finally {
                    ProxySelector.setDefault(system)
                }
            }
        }
}
