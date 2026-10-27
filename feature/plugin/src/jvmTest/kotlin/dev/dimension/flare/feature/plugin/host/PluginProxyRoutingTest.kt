package dev.dimension.flare.feature.plugin.host

import androidx.datastore.core.DataStore
import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class PluginProxyRoutingTest {
    @Test
    fun productionTransportUsesGlobalPolicyWithoutDirectFallback() =
        runBlocking {
            RoutingFixture().use {
                it.repository.selectGlobal(it.unavailablePolicy)
                assertFails { it.transport.execute(it.request()) }
                assertEquals(0, it.calls.get(), "A failed proxy must not send the plugin request directly")

                it.repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                assertEquals(
                    "OK",
                    it.transport
                        .execute(it.request())
                        .body
                        .decodeToString(),
                )
                it.repository.selectGlobal(it.unavailablePolicy)
                assertFails { it.transport.execute(it.request()) }
                assertEquals(1, it.calls.get(), "An existing transport must follow the new global route")
            }
        }

    @Test
    fun accountsCanOverrideOrInheritGlobalWithoutSharingRoutes() =
        runBlocking {
            RoutingFixture().use { fixture ->
                val alice = ProxyRef.Account(MicroBlogKey("alice", "plugin.example:8443"))
                val bob = ProxyRef.Account(MicroBlogKey("bob", "plugin.example:8443"))
                fixture.repository.selectGlobal(fixture.unavailablePolicy)
                fixture.repository.selectAccount(alice.accountKey, ProxyPolicy(ProxyMode.DIRECT))
                coroutineScope {
                    val direct = async { fixture.transport.execute(fixture.request(alice)) }
                    val inherited = async { assertFails { fixture.transport.execute(fixture.request(bob)) } }
                    assertEquals("OK", direct.await().body.decodeToString())
                    inherited.await()
                }
                assertEquals(1, fixture.calls.get())

                fixture.repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                assertEquals(
                    "OK",
                    fixture.transport
                        .execute(fixture.request(bob))
                        .body
                        .decodeToString(),
                )
                fixture.repository.selectAccount(bob.accountKey, fixture.unavailablePolicy)
                assertFails { fixture.transport.execute(fixture.request(bob)) }
                assertEquals(
                    "OK",
                    fixture.transport
                        .execute(fixture.request(alice))
                        .body
                        .decodeToString(),
                )

                fixture.repository.selectAccount(alice.accountKey, ProxyPolicy())
                fixture.repository.selectGlobal(fixture.unavailablePolicy)
                assertFails { fixture.transport.execute(fixture.request(alice)) }
                assertEquals(3, fixture.calls.get())
            }
        }

    @Test
    fun removedAccountNeverFallsBackToGlobal() =
        runBlocking {
            val account = ProxyRef.Account(MicroBlogKey("removed", "plugin.example"))
            RoutingFixture(ProxySettings(removedAccounts = setOf(account.accountKey.toString()))).use {
                it.repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                assertFails { it.transport.execute(it.request(account)) }
                assertEquals(0, it.calls.get())
            }
        }

    @Test
    fun changingAccountPolicyCancelsItsStreamingUploadAndKeepsOtherAccountsWorking() =
        runBlocking {
            RoutingFixture().use { fixture ->
                val account = ProxyRef.Account(MicroBlogKey("upload", "plugin.example"))
                val other = ProxyRef.Account(MicroBlogKey("other", "plugin.example"))
                fixture.repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                val started = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                fixture.server.createContext("/upload") { exchange ->
                    try {
                        exchange.requestBody.readNBytes(1_024)
                        started.complete(Unit)
                        release.await(5, TimeUnit.SECONDS)
                    } finally {
                        exchange.close()
                    }
                }
                try {
                    coroutineScope {
                        val upload =
                            async {
                                runCatching {
                                    fixture.transport.execute(
                                        fixture.request(account).copy(
                                            method = "POST",
                                            url = "${fixture.url}/upload",
                                            body =
                                                PluginTransportBodyV1.Multipart(
                                                    listOf(
                                                        PluginTransportMultipartPartV1.Asset(
                                                            "file",
                                                            BufferAsset(ByteArray(8 * 1024 * 1024)),
                                                            "upload.png",
                                                            "image/png",
                                                        ),
                                                    ),
                                                ),
                                        ),
                                    )
                                }.exceptionOrNull()
                            }
                        withTimeout(5_000) { started.await() }
                        fixture.repository.selectAccount(account.accountKey, fixture.unavailablePolicy)
                        assertIs<CancellationException>(withTimeout(5_000) { upload.await() })
                        assertEquals(
                            "OK",
                            fixture.transport
                                .execute(fixture.request(other))
                                .body
                                .decodeToString(),
                        )
                    }
                } finally {
                    release.countDown()
                }
            }
        }
}

private class RoutingFixture(
    initial: ProxySettings = ProxySettings(),
) : AutoCloseable {
    val calls = AtomicInteger()
    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    val repository = ProxyRepository(MemoryProxyStore(initial))
    val transport = createKtorPluginHttpTransport()
    val url: String get() = "http://127.0.0.1:${server.address.port}"
    val unavailablePolicy = ProxyPolicy(ProxyMode.PROXY, "unavailable")

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("OK".encodeToByteArray()) }
            exchange.close()
        }
        server.start()
        val application = KoinApplication.init()
        application.koin.declare(repository)
        GlobalContext.startKoin(application)
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            repository.save(ProxyProfile("unavailable", "Unavailable", ProxyProtocol.SOCKS5, "127.0.0.1", port))
        }
    }

    // The Host tests enforce HTTPS; this local fixture isolates transport routing without TLS setup.
    fun request(proxy: ProxyRef = ProxyRef.Global): PluginTransportRequestV1 =
        PluginTransportRequestV1("GET", url, emptyMap(), null, 5_000, proxy)

    override fun close() {
        transport.close()
        runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT)) }
        stopKoin()
        server.stop(0)
        executor.shutdownNow()
    }
}

private class MemoryProxyStore(
    initial: ProxySettings,
) : DataStore<ProxySettings> {
    override val data = MutableStateFlow(initial)

    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
        transform(data.value).also { data.value = it }
}
