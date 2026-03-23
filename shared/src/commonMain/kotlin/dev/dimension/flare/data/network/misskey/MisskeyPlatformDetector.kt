package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType

internal data object MisskeyPlatformDetector : PlatformDetector {
    override val priority: Int = 70

    override suspend fun detect(host: String): NodeData? {
        val nodeInfo =
            tryRun {
                NodeInfoService.fetchNodeInfo(host)
            }.getOrNull()

        if (nodeInfo?.equals("misskey", ignoreCase = true) == true) {
            return NodeData(
                host = host,
                platformType = PlatformType.Misskey,
                software = nodeInfo,
                compatibleMode = false,
            )
        }

        return tryRun {
            MisskeyService("https://$host/api/").meta(MetaRequest()).let {
                requireNotNull(it.name)
                NodeData(
                    host = host,
                    platformType = PlatformType.Misskey,
                    software = nodeInfo ?: PlatformType.Misskey.name,
                    compatibleMode = true,
                )
            }
        }.getOrNull()
    }
}
