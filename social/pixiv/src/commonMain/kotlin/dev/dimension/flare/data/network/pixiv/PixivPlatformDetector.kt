package dev.dimension.flare.data.network.pixiv

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.model.PlatformType

internal data object PixivPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeData? {
        if (!PIXIV_HOST.equals(host, ignoreCase = true) && !"www.$PIXIV_HOST".equals(host, ignoreCase = true)) {
            return null
        }
        return NodeData(
            host = PIXIV_HOST,
            platformType = PlatformType.Pixiv,
            software = PlatformType.Pixiv.name,
            compatibleMode = false,
        )
    }
}
