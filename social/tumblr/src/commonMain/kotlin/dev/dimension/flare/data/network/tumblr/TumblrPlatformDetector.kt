package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.data.platform.TUMBLR_WEB_HOST
import dev.dimension.flare.model.PlatformType

internal data object TumblrPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeData? {
        if (!host.isTumblrHost()) {
            return null
        }
        return NodeData(
            host = TUMBLR_HOST,
            platformType = PlatformType.Tumblr,
            software = PlatformType.Tumblr.name,
            compatibleMode = false,
        )
    }
}

private fun String.isTumblrHost(): Boolean {
    val normalized = lowercase()
    if (normalized == TUMBLR_HOST || normalized == TUMBLR_WEB_HOST) {
        return true
    }
    val blogName = normalized.removeSuffix(".$TUMBLR_HOST")
    return blogName != normalized && blogName.isNotBlank() && !blogName.contains('.')
}
