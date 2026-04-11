package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal data object TumblrPlatformDetector : PlatformDetector {
    override val priority: Int = 70

    override suspend fun detect(host: String): NodeData? {
        val normalized = host.lowercase()
        return if (normalized == "tumblr.com" || normalized == "www.tumblr.com" || normalized.endsWith(".tumblr.com")) {
            NodeData(
                host = host,
                platformType = PlatformType.Tumblr,
                software = PlatformType.Tumblr.name,
                compatibleMode = false,
            )
        } else {
            null
        }
    }
}
