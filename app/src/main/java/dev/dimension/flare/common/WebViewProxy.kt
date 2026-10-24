package dev.dimension.flare.common

import android.os.Build
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.dimension.flare.data.datastore.model.AppSettings
import java.util.concurrent.Executor

/**
 * Routes Flare's own WebViews through the in-app proxy.
 *
 * WebView has its own network stack (Chromium) and never asks the Ktor/OkHttp engine for a proxy,
 * so it keeps using the system proxy unless an override is installed. Account sign-in for the
 * cookie based services happens inside a WebView, which is why it used to ignore the setting.
 *
 * The override is scoped to Flare's process: it applies to every WebView this app creates, but
 * never to other apps or to the system proxy. On unsupported platforms/WebView versions this is a
 * no-op and WebView falls back to the system proxy.
 */
internal object WebViewProxy {
    private const val TAG = "WebViewProxy"
    private val directExecutor = Executor { it.run() }

    private val isSupported: Boolean
        get() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    fun apply(config: AppSettings.ProxyConfig) {
        runCatching {
            if (!isSupported) {
                return
            }
            val host =
                config.host
                    .trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .trimEnd('/')
            if (config.type != AppSettings.ProxyConfig.Type.Http ||
                host.isEmpty() ||
                config.port !in 1..65535
            ) {
                clear()
                return
            }
            val proxyConfig =
                ProxyConfig
                    .Builder()
                    .addProxyRule("$host:${config.port}")
                    .build()
            ProxyController
                .getInstance()
                .setProxyOverride(proxyConfig, directExecutor) {}
            Log.i(TAG, "WebView proxy override -> $host:${config.port}")
        }.onFailure {
            Log.w(TAG, "Cannot apply the WebView proxy override", it)
        }
    }

    fun clear() {
        runCatching {
            if (!isSupported) {
                return
            }
            ProxyController
                .getInstance()
                .clearProxyOverride(directExecutor) {}
            Log.i(TAG, "WebView proxy override cleared")
        }.onFailure {
            Log.w(TAG, "Cannot clear the WebView proxy override", it)
        }
    }
}
