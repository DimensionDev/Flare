package dev.dimension.flare.feature.plugin.host

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun platformUuid(): String = NSUUID().UUIDString.lowercase()

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformSecureRandom(size: Int): ByteArray =
    ByteArray(size).also { bytes ->
        bytes.usePinned { pinned ->
            check(SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0)) == errSecSuccess) {
                "Secure random generation failed"
            }
        }
    }

internal actual fun platformPluginHttpEngine(): HttpClientEngine = Darwin.create()

internal actual fun defaultPluginRuntimeHeapBudgetBytes(): Long =
    (NSProcessInfo.processInfo.physicalMemory.toLong() / 64).coerceIn(MIN_RUNTIME_POOL_BYTES, MAX_RUNTIME_POOL_BYTES)

private const val MIN_RUNTIME_POOL_BYTES = 64L * 1024 * 1024
private const val MAX_RUNTIME_POOL_BYTES = 256L * 1024 * 1024
