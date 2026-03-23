package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost

internal data object XQTPlatformDetector : PlatformDetector {
    override val priority: Int = 100

    override suspend fun detect(host: String): NodeData? {
        val aliases = listOf(xqtOldHost, "xqt.social", xqtHost)
        if (!aliases.any { it.equals(host, ignoreCase = true) }) {
            return null
        }
        return NodeData(
            host = host,
            platformType = PlatformType.xQt,
            software = PlatformType.xQt.name,
            compatibleMode = false,
        )
    }
}
