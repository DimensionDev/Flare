package dev.dimension.flare.data.network

import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.proxy.ProxySettingsStateHolder
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector

/**
 * JVM/Android OkHttp engine used by both the Android app and the desktop app.
 *
 * The proxy is resolved through a [ProxySelector] instead of `OkHttpConfig.proxy`: OkHttp asks the
 * selector on every connect, so changing the setting in the app applies without rebuilding this
 * engine (and therefore without rebuilding the clients wrapping it). It also preserves the
 * behaviour from before the setting existed, which is to defer to the platform by default.
 */
internal fun createOkHttpEngine(): HttpClientEngine =
    OkHttp.create {
        config { proxySelector(JvmRuntimeProxySelector) }
    }

/**
 * Resolves the proxy on every connect attempt.
 *
 * OkHttp invokes this from its connection threads and concurrently, hence the volatile read.
 */
internal object JvmRuntimeProxySelector : ProxySelector() {
    override fun select(uri: java.net.URI?): List<Proxy> =
        when (val proxies = resolveProxy(ProxySettingsStateHolder.current)) {
            // System: defer to the platform. Returning an empty list is NOT equivalent: OkHttp maps
            // both `null` and an empty list to Proxy.NO_PROXY, which would silently stop honouring
            // a system proxy.
            null -> selectSystemProxy(uri)

            else -> proxies
        }

    private fun selectSystemProxy(uri: java.net.URI?): List<Proxy> {
        if (uri == null) return listOf(Proxy.NO_PROXY)
        // A misconfigured system proxy can make the default selector throw; OkHttp treats that as
        // "no usable proxy", so do the same here instead of crashing the request.
        return runCatching { ProxySelector.getDefault()?.select(uri) }
            .getOrNull()
            .orEmpty()
            .ifEmpty { listOf(Proxy.NO_PROXY) }
    }

    override fun connectFailed(
        uri: java.net.URI?,
        sa: java.net.SocketAddress?,
        ioe: java.io.IOException?,
    ) = Unit
}

/**
 * The proxies to use for [config], or `null` when the platform default should decide.
 *
 * Kept free of platform state so the decision can be unit tested.
 */
internal fun resolveProxy(config: AppSettings.ProxyConfig): List<Proxy>? =
    when (config.type) {
        AppSettings.ProxyConfig.Type.System -> {
            null
        }

        AppSettings.ProxyConfig.Type.Direct -> {
            listOf(Proxy.NO_PROXY)
        }

        AppSettings.ProxyConfig.Type.Http -> {
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
        }
    }
