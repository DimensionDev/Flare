package dev.dimension.flare.benchmark

internal actual val benchmarkPlatform: String = "android-robolectric-bundled-sqlite"

internal actual fun collectLiveHeapSnapshot(): LiveHeapSnapshot {
    System.gc()
    val runtime = Runtime.getRuntime()
    return LiveHeapSnapshot(
        totalObjectsSizeBytes = runtime.totalMemory() - runtime.freeMemory(),
        markedObjectCount = null,
    )
}
