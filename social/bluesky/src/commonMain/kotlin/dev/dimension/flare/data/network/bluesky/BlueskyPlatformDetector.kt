package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType

internal data object BlueskyPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeData? =
        tryRun {
            BlueskyService("https://$host").describeServer().requireResponse()
            NodeData(
                host = host,
                platformType = PlatformType.Bluesky,
                software = PlatformType.Bluesky.name,
                compatibleMode = false,
            )
        }.getOrNull()
}
