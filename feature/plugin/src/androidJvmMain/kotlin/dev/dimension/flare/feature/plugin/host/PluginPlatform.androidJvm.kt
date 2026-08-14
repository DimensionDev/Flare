package dev.dimension.flare.feature.plugin.host

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.util.UUID

private val secureRandom = SecureRandom()

internal actual fun platformUuid(): String = UUID.randomUUID().toString()

internal actual fun platformSecureRandom(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

internal actual fun platformPluginHttpEngine(): HttpClientEngine = OkHttp.create()

internal actual fun defaultPluginRuntimeHeapBudgetBytes(): Long =
    (Runtime.getRuntime().maxMemory() / 4).coerceIn(MIN_RUNTIME_POOL_BYTES, MAX_RUNTIME_POOL_BYTES)

private const val MIN_RUNTIME_POOL_BYTES = 64L * 1024 * 1024
private const val MAX_RUNTIME_POOL_BYTES = 256L * 1024 * 1024
