package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyMode
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.plugins.websocket.WebSocketCapability
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLSessionConfiguration

@OptIn(ExperimentalForeignApi::class)
public fun interface AppleProxySessionConfigurator {
    public fun configure(
        configuration: NSURLSessionConfiguration,
        connection: ProxyConnection,
    )
}

public object AppleProxyTransport {
    public var configurator: AppleProxySessionConfigurator? = null
}

internal actual val platformEngineCapabilities: Set<HttpClientEngineCapability<*>> =
    setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

@OptIn(ExperimentalForeignApi::class)
internal actual fun createPlatformHttpClientEngine(
    connection: ProxyConnection,
    plugin: Boolean,
): HttpClientEngine =
    Darwin.create {
        configureSession {
            URLCredentialStorage = null
            URLCache = null
            HTTPCookieStorage = null
            HTTPShouldSetCookies = false
            // shortcut: Foundation can retransmit on 401 challenges; a different native transport is needed to suppress those attempts.
            if (connection.mode != ProxyMode.SYSTEM) {
                checkNotNull(AppleProxyTransport.configurator) { "Apple proxy transport has not been initialized" }
                    .configure(this, connection)
            }
        }
    }
