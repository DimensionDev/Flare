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

internal actual fun collectLiveHeapSnapshot(): LiveHeapSnapshot {
    GC.collect()
    val info = GC.lastGCInfo
    return LiveHeapSnapshot(
        totalObjectsSizeBytes = info?.memoryUsageAfter?.get("heap")?.totalObjectsSizeBytes,
        markedObjectCount = info?.markedCount,
    )
}
