package dev.dimension.flare.common

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.network.ProxySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal object WebViewProxy {
    private val mutex = Mutex()
    private var appliedKey: String? = null

    // WebView's override is process-wide; a screen must never clear it on disposal.
    suspend fun prepare(): ProxySnapshot =
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val snapshot = ProxyNetwork.snapshot(ProxyRef.Global)
                if (appliedKey != snapshot.key) {
                    val connection = snapshot.connection
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                        if (connection.mode !=
                            ProxyMode.SYSTEM
                        ) {
                            throw UnsupportedOperationException("WebView proxy overrides are unavailable")
                        }
                    } else {
                        val config =
                            when (connection.mode) {
                                ProxyMode.SYSTEM -> {
                                    null
                                }

                                ProxyMode.DIRECT -> {
                                    ProxyConfig
                                        .Builder()
                                        .addDirect()
                                        .removeImplicitRules()
                                        .build()
                                }

                                ProxyMode.PROXY -> {
                                    val scheme =
                                        when (connection.protocol) {
                                            ProxyProtocol.HTTPS -> "https"
                                            ProxyProtocol.SOCKS5 -> "socks5"
                                            null -> error("Missing proxy protocol")
                                        }
                                    val host = requireNotNull(connection.host).let { if (':' in it) "[$it]" else it }
                                    ProxyConfig
                                        .Builder()
                                        .addProxyRule("$scheme://$host:${requireNotNull(connection.port)}")
                                        .removeImplicitRules()
                                        .build()
                                }

                                ProxyMode.INHERIT -> {
                                    error("Unresolved proxy policy")
                                }
                            }
                        // Cancellation must not let a late native callback overwrite a newer override.
                        withContext(NonCancellable) {
                            suspendCancellableCoroutine { continuation ->
                                val done = Runnable { continuation.resume(Unit) }
                                val executor = java.util.concurrent.Executor { it.run() }
                                if (config == null) {
                                    ProxyController.getInstance().clearProxyOverride(executor, done)
                                } else {
                                    ProxyController.getInstance().setProxyOverride(config, executor, done)
                                }
                            }
                        }
                    }
                    appliedKey = snapshot.key
                }
                snapshot
            }
        }
}
