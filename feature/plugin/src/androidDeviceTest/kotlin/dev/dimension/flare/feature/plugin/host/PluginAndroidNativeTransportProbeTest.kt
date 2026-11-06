package dev.dimension.flare.feature.plugin.host

import androidx.datastore.core.DataStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.io.IOException
import java.net.Authenticator
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.InetAddress
import java.net.PasswordAuthentication
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real Android OkHttp backend, production transport factory and loopback sockets; no MockEngine. */
@RunWith(AndroidJUnit4::class)
class PluginAndroidNativeTransportProbeTest {
    @Test
    fun preservesRepeatedHeadersAndDoesNotFollowGetOrPostRedirects() =
        runBlocking {
            AndroidNativeFixture().use { fixture ->
                val response = fixture.request("/headers")
                assertEquals(listOf(COOKIE_A, COOKIE_B), response.values("Set-Cookie"))
                assertEquals(listOf("first", "second"), response.values("X-Repeated"))
                for (method in listOf("GET", "POST")) {
                    val redirected = fixture.request("/redirect/$method", method)
                    assertEquals(307, redirected.status)
                    assertEquals(listOf("/redirect-target"), redirected.values("Location"))
                    assertEquals(listOf(COOKIE_A, COOKIE_B), redirected.values("Set-Cookie"))
                }
                assertEquals(0, fixture.count("/redirect-target"))
                println("ANDROID_TRANSPORT headers=preserved GET/POST-redirect=single-hop")
            }
        }

    @Test
    fun doesNotAdoptServerOrGlobalCookiesAndDoesNotCacheResponses() =
        runBlocking {
            AndroidNativeFixture().use { fixture ->
                val previous = CookieHandler.getDefault()
                try {
                    val globalCookies = CookieManager()
                    globalCookies.cookieStore.add(URI(fixture.url), HttpCookie("GLOBAL_COOKIE", "probe-only"))
                    CookieHandler.setDefault(globalCookies)
                    fixture.request("/headers")
                    val echo = fixture.request("/echo")
                    assertEquals("<missing>", echo.body.decodeToString())
                    assertEquals("1", fixture.request("/cache").body.decodeToString())
                    assertEquals("2", fixture.request("/cache").body.decodeToString())
                    assertEquals(2, fixture.count("/cache"))
                    println("ANDROID_TRANSPORT implicit-cookie=none cache=none")
                } finally {
                    CookieHandler.setDefault(previous)
                }
            }
        }

    @Test
    fun doesNotAnswerAuthenticationChallengesWithGlobalCredentials() =
        runBlocking {
            try {
                Authenticator.setDefault(
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication("probe-user", "probe-password".toCharArray())
                    },
                )
                AndroidNativeFixture().use { fixture ->
                    for (method in listOf("GET", "POST")) {
                        val path = "/auth/$method"
                        assertEquals(401, fixture.request(path, method).status)
                        assertEquals(1, fixture.count(path))
                        println("ANDROID_TRANSPORT implicit-auth=none method=$method count=${fixture.count(path)}")
                    }
                }
            } finally {
                Authenticator.setDefault(null)
            }
        }

    @Test
    fun doesNotReplayFullyReceivedColdOrPooledRequests() =
        runBlocking {
            val counts = mutableListOf<Pair<String, Int>>()
            for (mode in listOf(ProxyMode.DIRECT, ProxyMode.SYSTEM)) {
                for (pooled in listOf(false, true)) {
                    for (method in listOf("GET", "POST")) {
                        AndroidNativeFixture(mode).use { fixture ->
                            if (pooled) fixture.request("/warm")
                            val path = "/drop/${method.lowercase()}"
                            val outcome = runCatching { fixture.request(path, method) }
                            delay(100)
                            val count = fixture.count(path)
                            if (pooled) {
                                assertEquals(
                                    fixture.firstConnection("/warm"),
                                    fixture.firstConnection(path),
                                    "First drop uses the warmed TCP connection",
                                )
                            }
                            counts += "$mode pooled=$pooled $method" to count
                            println(
                                "ANDROID_TRANSPORT drop mode=$mode pooled=$pooled method=$method count=$count failed=${outcome.isFailure}",
                            )
                            assertTrue(outcome.isFailure, "Server closes before sending an HTTP response")
                        }
                    }
                }
            }
            assertTrue(counts.all { it.second == 1 }, "Fully received request counts: $counts")
        }
}

private class AndroidNativeFixture(
    mode: ProxyMode = ProxyMode.DIRECT,
) : AutoCloseable {
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val firstConnections = ConcurrentHashMap<String, Int>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val repository = ProxyRepository(AndroidProbeProxyStore(ProxySettings(global = ProxyPolicy(mode))))
    private val transport = createKtorPluginHttpTransport()
    val url: String get() = "http://127.0.0.1:${server.localPort}"

    init {
        val application = KoinApplication.init()
        application.koin.declare(repository)
        GlobalContext.startKoin(application)
        runBlocking {
            repository.selectGlobal(ProxyPolicy(if (mode == ProxyMode.DIRECT) ProxyMode.SYSTEM else ProxyMode.DIRECT))
            repository.selectGlobal(ProxyPolicy(mode))
        }
        workers.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets += socket
                workers.execute { serve(socket) }
            }
        }
    }

    suspend fun request(
        path: String,
        method: String = "GET",
    ): PluginTransportResponseV1 =
        transport.execute(
            PluginTransportRequestV1(
                method = method,
                url = url + path,
                headers = emptyMap(),
                body = if (method == "POST") PluginTransportBodyV1.Text("probe-body", "text/plain") else null,
                timeoutMillis = 5_000,
                proxy = ProxyRef.Global,
            ),
        )

    fun count(path: String): Int = counts[path]?.get() ?: 0

    fun firstConnection(path: String): Int? = firstConnections[path]

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val output = socket.getOutputStream()
            while (true) {
                val requestLine = input.readLine() ?: break
                val path = requestLine.split(' ')[1]
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: return
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                repeat(headers["content-length"]?.toInt() ?: 0) { check(input.read() != -1) }
                val count = counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                firstConnections.putIfAbsent(path, socket.port)
                if (path.startsWith("/drop/")) return
                val body =
                    when (path) {
                        "/echo" -> headers["cookie"] ?: "<missing>"
                        "/cache" -> count.toString()
                        else -> "ok"
                    }.encodeToByteArray()
                val response =
                    buildString {
                        append(
                            when {
                                path.startsWith("/redirect/") -> "HTTP/1.1 307 Temporary Redirect\r\n"
                                path.startsWith("/auth/") -> "HTTP/1.1 401 Unauthorized\r\n"
                                else -> "HTTP/1.1 200 OK\r\n"
                            },
                        )
                        if (path == "/headers" || path.startsWith("/redirect/")) {
                            append("Set-Cookie: $COOKIE_A\r\nSet-Cookie: $COOKIE_B\r\n")
                            append("X-Repeated: first\r\nX-Repeated: second\r\n")
                        }
                        if (path.startsWith("/redirect/")) append("Location: /redirect-target\r\n")
                        if (path.startsWith("/auth/")) append("WWW-Authenticate: Basic realm=\"transport-probe\"\r\n")
                        if (path == "/cache") append("Cache-Control: max-age=3600\r\n")
                        append("Content-Length: ${body.size}\r\nContent-Type: text/plain\r\nConnection: keep-alive\r\n\r\n")
                    }
                output.write(response.toByteArray(Charsets.ISO_8859_1))
                output.write(body)
                output.flush()
            }
        } catch (_: IOException) {
            // Closing a fixture or client also closes an idle keep-alive read.
        } finally {
            sockets -= socket
            runCatching { socket.close() }
        }
    }

    override fun close() {
        transport.close()
        server.close()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        stopKoin()
    }
}

private class AndroidProbeProxyStore(
    initial: ProxySettings,
) : DataStore<ProxySettings> {
    override val data = MutableStateFlow(initial)

    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
        transform(data.value).also { data.value = it }
}

private fun PluginTransportResponseV1.values(name: String): List<String> =
    headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        .orEmpty()

private const val COOKIE_A = "SESSION=probe-a; Path=/; Expires=Wed, 09 Jun 2038 10:18:14 GMT; HttpOnly"
private const val COOKIE_B = "CSRF=probe-b; Path=/; SameSite=Lax"
