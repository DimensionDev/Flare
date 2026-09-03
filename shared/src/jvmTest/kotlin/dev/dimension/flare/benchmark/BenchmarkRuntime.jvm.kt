package dev.dimension.flare.benchmark

internal actual val benchmarkPlatform: String = "jvm-bundled-sqlite"

internal actual fun collectLiveHeapBytes(): Long? {
    System.gc()
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
