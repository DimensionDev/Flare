package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal data object NostrPlatformDetector : PlatformDetector {
    override suspend fun detect(host: String): NodeData? {
        if (!host.equals("nostr", ignoreCase = true)) {
            return null
        }
        return NodeData(
            host = host,
            platformType = PlatformType.Nostr,
            software = PlatformType.Nostr.name,
            compatibleMode = false,
        )
    }
}
