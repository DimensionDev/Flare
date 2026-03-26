package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort

internal data object VVOPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeData? {
        val aliases = listOf(vvoHost, vvo, vvoHostShort, "vvo.social", vvoHostLong)
        if (!aliases.any { it.equals(host, ignoreCase = true) }) {
            return null
        }
        return NodeData(
            host = host,
            platformType = PlatformType.VVo,
            software = PlatformType.VVo.name,
            compatibleMode = false,
        )
    }
}
