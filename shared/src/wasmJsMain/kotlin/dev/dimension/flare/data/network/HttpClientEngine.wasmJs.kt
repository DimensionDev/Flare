package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyMode
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.plugins.websocket.WebSocketCapability

internal actual val platformEngineCapabilities: Set<HttpClientEngineCapability<*>> =
    setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

internal actual fun createPlatformHttpClientEngine(connection: ProxyConnection): HttpClientEngine {
    check(connection.mode == ProxyMode.SYSTEM) { "App proxies are not supported in a browser" }
    return Js.create()
}
