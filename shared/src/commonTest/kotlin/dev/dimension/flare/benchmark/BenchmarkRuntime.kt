package dev.dimension.flare.benchmark

internal expect val benchmarkPlatform: String

internal data class LiveHeapSnapshot(
    val totalObjectsSizeBytes: Long?,
    val markedObjectCount: Long?,
)

/** Returns the best available post-GC live heap estimate for the current platform. */
internal expect fun collectLiveHeapSnapshot(): LiveHeapSnapshot

internal fun collectLiveHeapBytes(): Long? = collectLiveHeapSnapshot().totalObjectsSizeBytes
