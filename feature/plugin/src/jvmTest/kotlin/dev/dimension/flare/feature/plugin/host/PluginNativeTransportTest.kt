package dev.dimension.flare.feature.plugin.host

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginNativeTransportTest {
    @Test
    fun nativeHeadersKeepIndependentValuesAndDoNotStoreCookiesOrCredentials() =
        runBlocking {
            for (mode in listOf(ProxyMode.DIRECT, ProxyMode.SYSTEM)) {
                NativeFixture(mode).use { fixture ->
                    val response = fixture.transport.execute(fixture.request("/headers"))
                    assertEquals(
                        listOf("first=one; Path=/; HttpOnly", "second=two; Expires=Wed, 09 Jun 2038 10:18:14 GMT; Path=/"),
                        response.header("Set-Cookie"),
                        mode.name,
                    )
                    assertEquals(listOf("first", "second,third"), response.header("X-Session"), mode.name)
                    fixture.transport.execute(fixture.request("/echo", headers = mapOf("Authorization" to "Basic dGVzdDp0ZXN0")))
                    fixture.transport.execute(fixture.request("/echo"))
                    assertNull(fixture.requests.last().headers["cookie"], mode.name)
                    assertNull(fixture.requests.last().headers["authorization"], mode.name)
                    println("native-transport $mode: headers preserved; no implicit Cookie/Authorization")
                }
            }
        }

    @Test
    fun nativeRedirectDoesNotFollowGetOrPost() =
        runBlocking {
            for (mode in listOf(ProxyMode.DIRECT, ProxyMode.SYSTEM)) {
                NativeFixture(mode).use { fixture ->
                    for (method in listOf("GET", "POST")) {
                        val response = fixture.transport.execute(fixture.request("/redirect", method))
                        assertEquals(302, response.status, "$mode $method")
                        assertEquals(listOf("/final"), response.header("Location"))
                    }
                    assertEquals(listOf("/redirect", "/redirect"), fixture.requests.map { it.path })
                    println("native-transport $mode: GET/POST redirect returned one hop")
                }
            }
        }

    @Test
    fun nativeConnectionFailuresDoNotReplayReceivedRequests() =
        runBlocking {
            for (mode in listOf(ProxyMode.DIRECT, ProxyMode.SYSTEM)) {
                for (method in listOf("GET", "POST")) {
                    for (reused in listOf(false, true)) {
                        NativeFixture(mode).use { fixture ->
                            if (reused) fixture.transport.execute(fixture.request("/warm"))
                            val result = runCatching { fixture.transport.execute(fixture.request("/drop", method)) }
                            val received = fixture.requests.filter { it.path == "/drop" }
                            val label = "$mode $method reused=$reused"
                            println("native-transport $label: received=${received.size}; success=${result.isSuccess}")
                            assertEquals(1, received.size, label)
                            if (method == "POST") {
                                received.forEach { assertEquals("payload", it.body, label) }
                            }
                            assertTrue(result.isFailure, label)
                        }
                    }
                }
            }
        }
}

private fun PluginTransportResponseV1.header(name: String): List<String> =
    headers.entries
        .firstOrNull { it.key.equals(name, true) }
        ?.value
        .orEmpty()

private data class ReceivedRequest(
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private class NativeFixture(
    mode: ProxyMode,
) : AutoCloseable {
    private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<ReceivedRequest>()
    private val dropped = AtomicBoolean()
    private val previousSelector = ProxySelector.getDefault()
    private val store = NativeProxyStore(ProxySettings(global = ProxyPolicy(mode)))
    private val repository = ProxyRepository(store)
    val transport = createKtorPluginHttpTransport()

    init {
        // SYSTEM is still selected by production routing; only this test process's proxy discovery is isolated.
        ProxySelector.setDefault(
            object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)

                override fun connectFailed(
                    uri: URI,
                    sa: SocketAddress,
                    ioe: java.io.IOException,
                ) = Unit
            },
        )
        val application = KoinApplication.init()
        application.koin.declare(repository)
        GlobalContext.startKoin(application)
        thread(isDaemon = true, name = "plugin-native-http") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets += socket
                thread(isDaemon = true) { runCatching { handle(socket) } }
            }
        }
    }

    fun request(
        path: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): PluginTransportRequestV1 =
        PluginTransportRequestV1(
            method,
            "http://127.0.0.1:${server.localPort}$path",
            headers,
            if (method == "POST") PluginTransportBodyV1.Text("payload", "text/plain") else null,
            5_000,
        )

    private fun handle(socket: Socket) =
        socket.use {
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            while (true) {
                val requestLine = reader.readLine() ?: break
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: return@use
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                val body = CharArray(headers["content-length"]?.toIntOrNull() ?: 0)
                var offset = 0
                while (offset < body.size) {
                    val count = reader.read(body, offset, body.size - offset)
                    if (count < 0) return@use
                    offset += count
                }
                val path = requestLine.split(' ')[1]
                requests += ReceivedRequest(path, headers, body.concatToString())
                if (path == "/drop" && dropped.compareAndSet(false, true)) {
                    socket.setSoLinger(true, 0)
                    break
                }
                val response =
                    when (path) {
                        "/headers" -> {
                            "HTTP/1.1 200 OK\r\nSet-Cookie: first=one; Path=/; HttpOnly\r\n" +
                                "Set-Cookie: second=two; Expires=Wed, 09 Jun 2038 10:18:14 GMT; Path=/\r\n" +
                                "X-Session: first\r\nX-Session: second,third\r\n"
                        }

                        "/redirect" -> {
                            "HTTP/1.1 302 Found\r\nLocation: /final\r\n"
                        }

                        else -> {
                            "HTTP/1.1 200 OK\r\n"
                        }
                    }
                val keepAlive = path == "/warm"
                socket.getOutputStream().write(
                    (response + "Content-Length: 2\r\nConnection: ${if (keepAlive) "keep-alive" else "close"}\r\n\r\nOK")
                        .toByteArray(Charsets.ISO_8859_1),
                )
                socket.getOutputStream().flush()
                if (!keepAlive) break
            }
        }

    override fun close() {
        transport.close()
        runBlocking {
            // Retire the shared production route before the next fixture starts.
            repository.selectGlobal(
                ProxyPolicy(
                    if (store.data.value.global.mode ==
                        ProxyMode.DIRECT
                    ) {
                        ProxyMode.SYSTEM
                    } else {
                        ProxyMode.DIRECT
                    },
                ),
            )
        }
        stopKoin()
        ProxySelector.setDefault(previousSelector)
        server.close()
        sockets.forEach { runCatching { it.close() } }
    }
}

private class NativeProxyStore(
    initial: ProxySettings,
) : DataStore<ProxySettings> {
    override val data = MutableStateFlow(initial)

    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
        transform(data.value).also { data.value = it }
}
