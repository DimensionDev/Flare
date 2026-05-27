package dev.dimension.flare.data.network.nodeinfo
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PlatformDetector {
    public val priority: Int
        get() = 0

    public suspend fun detect(host: String): NodeData?
}
