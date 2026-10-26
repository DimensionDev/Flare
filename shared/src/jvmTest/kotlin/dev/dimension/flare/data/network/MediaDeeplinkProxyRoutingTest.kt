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
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class MediaDeeplinkProxyRoutingTest {
    @Test
    fun externalMediaCannotSendRequestsOutsideTheGlobalProxy(): Unit =
        runBlocking {
            val root = createTestRootPath()
            val repository = ProxyRepository(OkioFileStorage(createTestFileSystem(), root))
            val calls = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/image") { exchange ->
                calls.incrementAndGet()
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("OK".toByteArray()) }
                exchange.close()
            }
            server.start()
            val originalSelector = ProxySelector.getDefault()
            ProxySelector.setDefault(
                object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)

                    override fun connectFailed(
                        uri: URI,
                        sa: SocketAddress,
                        ioe: IOException,
                    ) = Unit
                },
            )
            startKoin { modules(module { testSingle<ProxyRepository> { repository } }) }
            try {
                val unavailable = ServerSocket(0).use { it.localPort }
                repository.save(ProxyProfile("blocked", "Blocked", ProxyProtocol.SOCKS5, "127.0.0.1", unavailable))
                repository.selectGlobal(ProxyPolicy(ProxyMode.PROXY, "blocked"))
                val account = MicroBlogKey("deeplink-direct", "example.social")
                repository.selectAccount(account, ProxyPolicy(ProxyMode.DIRECT))
                val image = DeeplinkRoute.Media.Image("http://127.0.0.1:${server.address.port}/image", null)

                assertFails { fetch(image) }
                assertEquals(0, calls.get())
                for (proxy in listOf(ProxyRef.System, ProxyRef.Account(account))) {
                    val encoded = ProtoBuf.encodeToHexString(ExternalRoutePackage(image.copy(proxy = proxy)))
                    val decoded = DeeplinkRoute.parse("flare://$encoded")
                    if (decoded is DeeplinkRoute.Media.Image) {
                        runCatching { fetch(decoded) }
                    }
                    assertEquals(0, calls.get(), "External media must not bypass the global proxy via $proxy")
                    assertNull(decoded)
                }

                val internal = image.copy(proxy = ProxyRef.Account(account))
                val decoded = assertIs<DeeplinkRoute.Media.Image>(DeeplinkRoute.parse(internal.toUri()))
                assertEquals(200, fetch(decoded))
                assertEquals(1, calls.get(), "Internally generated account media must retain its selected route")
            } finally {
                repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                stopKoin()
                server.stop(0)
                ProxySelector.setDefault(originalSelector)
                deleteTestRootPath(root)
            }
        }

    private suspend fun fetch(route: DeeplinkRoute.Media.Image): Int {
        val media = UiMedia.Image(route.uri, proxy = route.proxy)
        val client = mediaHttpClient(media.proxy)
        return try {
            client.get(media.url) { applyMedia(media) }.status.value
        } finally {
            client.close()
            client.engine.close()
        }
    }

    @Serializable
    private data class ExternalRoutePackage(
        val route: DeeplinkRoute,
    )
}
