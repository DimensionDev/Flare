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
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyMediaCancellationTest {
    private val account = MicroBlogKey("media-transfer", "example.social")
    private val accountProxy = ProxyRef.forAccount(account)
    private val blocked = ProxyPolicy(ProxyMode.PROXY, "unavailable")

    @Test
    fun policyChangesCancelAnOngoingDownload(): Unit =
        runBlocking {
            for (proxy in listOf(ProxyRef.Global, accountProxy)) {
                fixture { repository, url, remainingBody, _ ->
                    val call = proxyCallFactory(UiMedia.Image(url, proxy = proxy)).newCall(Request.Builder().url(url).build())
                    call.execute().use { response ->
                        // Consume the first byte before changing policy; the second is still upstream.
                        assertEquals('a'.code.toByte(), response.body.source().readByte())
                        if (proxy == ProxyRef.Global) repository.selectGlobal(blocked) else repository.selectAccount(account, blocked)
                        assertTrue(call.isCanceled())
                        remainingBody.countDown()
                        assertFailsWith<IOException> { response.body.string() }
                    }
                }
            }
        }

    @Test
    fun removingAccountCancelsItsOngoingDownload(): Unit =
        runBlocking {
            fixture { repository, url, remainingBody, _ ->
                val call = proxyCallFactory(UiMedia.Image(url, proxy = accountProxy)).newCall(Request.Builder().url(url).build())
                call.execute().use { response ->
                    assertEquals('a'.code.toByte(), response.body.source().readByte())
                    repository.removeAccount(account)
                    assertTrue(call.isCanceled())
                    remainingBody.countDown()
                    assertFailsWith<IOException> { response.body.string() }
                }
            }
        }

    @Test
    fun unrelatedPolicyChangesDoNotCancelDownloads(): Unit =
        runBlocking {
            fixture { repository, url, remainingBody, _ ->
                repository.selectAccount(account, ProxyPolicy(ProxyMode.DIRECT))
                val call = proxyCallFactory(UiMedia.Image(url, proxy = accountProxy)).newCall(Request.Builder().url(url).build())
                call.execute().use { response ->
                    repository.selectGlobal(blocked)
                    repository.selectAccount(MicroBlogKey("other", "example.social"), blocked)
                    assertFalse(call.isCanceled())
                    remainingBody.countDown()
                    assertEquals("ab", response.body.string())
                }
            }
        }

    @Test
    fun callCreatedBeforePolicyChangeCannotStartOnTheOldRoute(): Unit =
        runBlocking {
            fixture { repository, url, remainingBody, requests ->
                val factory = proxyCallFactory(UiMedia.Image(url, proxy = accountProxy))
                val request = Request.Builder().url(url).build()
                val oldCall = factory.newCall(request)
                repository.selectAccount(account, blocked)
                assertFailsWith<IOException> { oldCall.execute().close() }
                assertTrue(oldCall.isCanceled())
                assertFailsWith<IOException> { factory.newCall(request).execute().close() }
                assertEquals(0, requests.get())
                repository.selectAccount(account, ProxyPolicy(ProxyMode.DIRECT))
                remainingBody.countDown()
                factory.newCall(request).execute().use { assertEquals("ab", it.body.string()) }
            }
        }

    @Test
    fun snapshotObserversHandleRenameLateSubscriptionAndUnsubscribe(): Unit =
        runBlocking {
            fixture { repository, _, _, _ ->
                repository.selectAccount(account, blocked)
                val snapshot = ProxyNetwork.snapshot(accountProxy)
                var invalidations = 0
                snapshot.onInvalidated { invalidations++ }
                val unsubscribe = snapshot.onInvalidated { error("Unsubscribed observer must not run") }
                unsubscribe()
                val profile = ProxyProfile("unavailable", "Renamed", ProxyProtocol.SOCKS5, "127.0.0.1", snapshot.connection.port!!)
                repository.save(profile)
                assertEquals(0, invalidations)
                assertEquals(snapshot, ProxyNetwork.snapshot(accountProxy))
                repository.save(profile.copy(host = "localhost"))
                assertEquals(1, invalidations)
                snapshot.onInvalidated { invalidations++ }
                assertEquals(2, invalidations)
            }
        }

    private suspend fun fixture(block: suspend (ProxyRepository, String, CountDownLatch, AtomicInteger) -> Unit) {
        val root = createTestRootPath()
        val repository = ProxyRepository(OkioFileStorage(createTestFileSystem(), root))
        val remainingBody = CountDownLatch(1)
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            try {
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { output ->
                    output.write('a'.code)
                    output.flush()
                    check(remainingBody.await(10, TimeUnit.SECONDS))
                    output.write('b'.code)
                }
            } catch (_: IOException) {
                // Cancellation closes the socket before the second byte is sent.
            } finally {
                exchange.close()
            }
        }
        server.start()
        startKoin { modules(module { testSingle<ProxyRepository> { repository } }) }
        try {
            repository.restoreAccount(account)
            repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
            val unavailablePort = ServerSocket(0).use { it.localPort }
            repository.save(ProxyProfile("unavailable", "Unavailable", ProxyProtocol.SOCKS5, "127.0.0.1", unavailablePort))
            block(repository, "http://127.0.0.1:${server.address.port}/download", remainingBody, requests)
        } finally {
            remainingBody.countDown()
            repository.removeAccount(account)
            repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
            stopKoin()
            server.stop(0)
            deleteTestRootPath(root)
        }
    }
}
