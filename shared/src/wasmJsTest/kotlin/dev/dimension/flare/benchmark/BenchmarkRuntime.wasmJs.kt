package dev.dimension.flare.benchmark

internal actual val benchmarkPlatform: String = "wasm-js-web-sqlite"

internal actual fun collectLiveHeapSnapshot(): LiveHeapSnapshot =
    LiveHeapSnapshot(
        totalObjectsSizeBytes = null,
        markedObjectCount = null,
    )
