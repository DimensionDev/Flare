package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType

internal data object MastodonPlatformDetector : PlatformDetector {
    override val priority: Int = 60

    override suspend fun detect(host: String): NodeData? {
        val nodeInfo =
            tryRun {
                NodeInfoService.fetchNodeInfo(host)
            }.getOrNull()

        if (nodeInfo?.equals("mastodon", ignoreCase = true) == true) {
            return NodeData(
                host = host,
                platformType = PlatformType.Mastodon,
                software = nodeInfo,
                compatibleMode = false,
            )
        }

        return tryRun {
            MastodonInstanceService("https://$host/").instance().let {
                requireNotNull(it.title)
                NodeData(
                    host = host,
                    platformType = PlatformType.Mastodon,
                    software = nodeInfo ?: PlatformType.Mastodon.name,
                    compatibleMode = true,
                )
            }
        }.getOrElse {
            tryRun {
                MastodonInstanceService("https://$host/").instanceV1().let {
                    requireNotNull(it.title)
                    NodeData(
                        host = host,
                        platformType = PlatformType.Mastodon,
                        software = nodeInfo ?: PlatformType.Mastodon.name,
                        compatibleMode = true,
                    )
                }
            }.getOrNull()
        }
    }
}
