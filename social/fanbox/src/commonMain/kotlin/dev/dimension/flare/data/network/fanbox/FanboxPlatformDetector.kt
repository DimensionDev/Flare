package dev.dimension.flare.data.network.fanbox

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.FANBOX_HOST
import dev.dimension.flare.data.platform.FANBOX_WEB_HOST
import dev.dimension.flare.model.PlatformType

internal data object FanboxPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeData? {
        if (!FANBOX_HOST.equals(host, ignoreCase = true) && !FANBOX_WEB_HOST.equals(host, ignoreCase = true)) {
            return null
        }
        return NodeData(
            host = FANBOX_HOST,
            platformType = PlatformType.Fanbox,
            software = PlatformType.Fanbox.name,
            compatibleMode = false,
        )
    }
}
