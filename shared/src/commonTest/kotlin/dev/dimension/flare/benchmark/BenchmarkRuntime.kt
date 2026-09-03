package dev.dimension.flare.benchmark

internal expect val benchmarkPlatform: String

/** Returns the best available post-GC live heap estimate for the current platform. */
internal expect fun collectLiveHeapBytes(): Long?
