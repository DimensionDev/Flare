package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyRef
import io.ktor.client.engine.js.Js
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyEngineCapabilitiesTest {
    @Test
    fun routingPreservesBrowserTimeoutWebSocketAndSseCapabilities() {
        val browser = Js.create()
        val routing = ProxyRoutingEngine(ProxyRef.Global)
        try {
            assertEquals(browser.supportedCapabilities, routing.supportedCapabilities)
        } finally {
            routing.close()
            browser.close()
        }
    }
}
