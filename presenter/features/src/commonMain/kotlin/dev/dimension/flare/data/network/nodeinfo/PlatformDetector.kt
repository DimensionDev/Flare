package dev.dimension.flare.data.network.nodeinfo

internal interface PlatformDetector {
    val priority: Int
        get() = 0

    suspend fun detect(host: String): NodeData?
}
