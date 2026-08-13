package dev.dimension.flare.feature.plugin.host

import io.ktor.client.engine.HttpClientEngine
import kotlin.native.HiddenFromObjC

internal expect fun platformUuid(): String

internal expect fun platformSecureRandom(size: Int): ByteArray

internal expect fun platformPluginHttpEngine(): HttpClientEngine

internal expect fun defaultPluginRuntimeHeapBudgetBytes(): Long

@HiddenFromObjC
public fun createKtorPluginHttpTransport(): KtorPluginHttpTransport = KtorPluginHttpTransport(platformPluginHttpEngine())
