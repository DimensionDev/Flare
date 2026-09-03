@file:OptIn(
    ExperimentalStdlibApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlin.native.runtime.NativeRuntimeApi::class,
)

package dev.dimension.flare.benchmark

import kotlin.native.Platform
import kotlin.native.runtime.GC

internal actual val benchmarkPlatform: String =
    "${Platform.osFamily.name.lowercase()}-${Platform.cpuArchitecture.name.lowercase()}-" +
        (if (Platform.isDebugBinary) "debug" else "release") +
        "-bundled-sqlite"

internal actual fun collectLiveHeapBytes(): Long? {
    GC.collect()
    return GC.lastGCInfo
        ?.memoryUsageAfter
        ?.get("heap")
        ?.totalObjectsSizeBytes
}
