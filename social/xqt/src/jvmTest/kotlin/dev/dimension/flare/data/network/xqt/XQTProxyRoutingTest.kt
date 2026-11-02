package dev.dimension.flare.data.network.xqt

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class XQTProxyRoutingTest {
    @Test
    fun xReloginIdentityLookupMustUseAccountProxy(): Unit =
        runBlocking {
            val connections = CopyOnWriteArrayList<String>()

            fun refusingProxy(name: String): ServerSocket {
                val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
                thread(isDaemon = true) {
                    runCatching {
                        server.accept().use { socket ->
                            socket.soTimeout = 3_000
                            val input = socket.getInputStream()
                            val output = socket.getOutputStream()
                            check(input.read() == 5)
                            val methods = input.read()
                            repeat(methods) { input.read() }
                            output.write(byteArrayOf(5, 0))
                            output.flush()
                            check(input.read() == 5)
                            check(input.read() == 1)
                            check(input.read() == 0)
                            check(input.read() == 3)
                            val hostname = ByteArray(input.read())
                            hostname.indices.forEach { hostname[it] = input.read().toByte() }
                            val port = (input.read() shl 8) + input.read()
                            connections += "$name:${hostname.toString(Charsets.US_ASCII)}:$port"
                            output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                            output.flush()
                        }
                    }
                }
                return server
            }
            val global = refusingProxy("global")
            val account = refusingProxy("account")
            val store =
                object : DataStore<ProxySettings> {
                    override val data = MutableStateFlow(ProxySettings())

                    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                        transform(data.value).also { data.value = it }
                }
            val repository = ProxyRepository(store)
            startKoin { modules(module { single { repository } }) }
            val key = MicroBlogKey("proxy-routing-test-user", "x.com")
            try {
                repository.save(ProxyProfile("global", "Global", ProxyProtocol.SOCKS5, "127.0.0.1", global.localPort))
                repository.save(ProxyProfile("account", "Account", ProxyProtocol.SOCKS5, "127.0.0.1", account.localPort))
                repository.selectGlobal(ProxyPolicy(ProxyMode.PROXY, "global"))
                repository.selectAccount(key, ProxyPolicy(ProxyMode.PROXY, "account"))
                val fakeCookie = "ct0=proxy-test; auth_token=proxy-test"
                runCatching { XQTService(flowOf(fakeCookie), key).getInitialUserId(fakeCookie) }
                assertEquals(listOf("account:x.com:443"), connections.toList())
            } finally {
                repository.selectAccount(key, ProxyPolicy(ProxyMode.DIRECT))
                repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                global.close()
                account.close()
                stopKoin()
            }
        }
}
