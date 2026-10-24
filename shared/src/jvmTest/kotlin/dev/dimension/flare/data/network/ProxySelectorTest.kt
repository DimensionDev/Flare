package dev.dimension.flare.data.network

import dev.dimension.flare.data.datastore.model.AppSettings
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxySelectorTest {
    @Test
    fun systemModeDefersToThePlatform() {
        val resolved =
            resolveProxy(
                AppSettings.ProxyConfig(type = AppSettings.ProxyConfig.Type.System),
            )

        // null means "let the platform decide"; an empty list would be read by OkHttp as
        // Proxy.NO_PROXY and would silently stop honouring a system proxy.
        assertNull(resolved)
    }

    @Test
    fun directModeForcesNoProxy() {
        val resolved =
            resolveProxy(
                AppSettings.ProxyConfig(type = AppSettings.ProxyConfig.Type.Direct),
            )

        assertEquals(listOf(Proxy.NO_PROXY), resolved)
    }

    @Test
    fun httpModeUsesTheConfiguredHostAndPort() {
        val resolved =
            resolveProxy(
                AppSettings.ProxyConfig(
                    type = AppSettings.ProxyConfig.Type.Http,
                    host = "192.168.3.2",
                    port = 7890,
                ),
            )

        assertEquals(
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("192.168.3.2", 7890))),
            resolved,
        )
    }

    @Test
    fun httpModeKeepsTheConfiguredValuesVerbatim() {
        val resolved =
            resolveProxy(
                AppSettings.ProxyConfig(
                    type = AppSettings.ProxyConfig.Type.Http,
                    host = "proxy.example.com",
                    port = 1080,
                ),
            )

        val address = resolved?.single()?.address()
        assertEquals("proxy.example.com", (address as InetSocketAddress).hostString)
        assertEquals(1080, address.port)
    }
}
