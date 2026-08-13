package dev.dimension.flare.feature.plugin.host

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.util.UUID

private val secureRandom = SecureRandom()

internal actual fun platformUuid(): String = UUID.randomUUID().toString()

internal actual fun platformSecureRandom(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

internal actual fun platformPluginHttpEngine(): HttpClientEngine = OkHttp.create()

internal actual fun defaultPluginRuntimeHeapBudgetBytes(): Long = 256L * 1024 * 1024
