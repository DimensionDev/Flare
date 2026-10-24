package dev.dimension.flare.data.network.proxy

import dev.dimension.flare.data.datastore.model.AppSettings.ProxyConfig
import kotlin.concurrent.Volatile

/**
 * The proxy configuration every HTTP engine reads while it is running.
 *
 * [ProxyConfig.type] is read on every network connect, so this has to be safe to read from
 * OkHttp's connection threads while the settings screen writes it from the main thread.
 */
internal object ProxySettingsStateHolder {
    @Volatile
    private var value: ProxyConfig = ProxyConfig()

    internal val current: ProxyConfig
        get() = value

    internal fun update(value: ProxyConfig) {
        this.value = value
    }
}
