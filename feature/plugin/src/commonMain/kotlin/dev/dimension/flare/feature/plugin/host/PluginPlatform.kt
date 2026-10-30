package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.proxyHttpClientEngine
import kotlin.native.HiddenFromObjC

internal expect fun platformUuid(): String

internal expect fun platformSecureRandom(size: Int): ByteArray

@HiddenFromObjC
public fun createKtorPluginHttpTransport(): KtorPluginHttpTransport = KtorPluginHttpTransport(proxyHttpClientEngine(ProxyRef.Global))
