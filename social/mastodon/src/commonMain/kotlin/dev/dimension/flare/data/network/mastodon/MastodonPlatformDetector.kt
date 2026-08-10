package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object MastodonPlatformDetector : PlatformDetector {
    override val priority: Int = 60

    override suspend fun detect(host: String): NodeDetection? {
        val nodeInfo =
            tryRun {
                NodeInfoService.fetchNodeInfo(host)
            }.getOrNull()

        if (NodeInfoService.isUnsupportedSoftware(nodeInfo)) {
            return null
        }

        if (nodeInfo?.equals("mastodon", ignoreCase = true) == true) {
            return NodeDetection(
                host = host,
                software = nodeInfo,
                compatibleMode = false,
            )
        }

        return tryRun {
            MastodonInstanceService("https://$host/").instance().let {
                requireNotNull(it.title)
                NodeDetection(
                    host = host,
                    software = nodeInfo ?: "Mastodon",
                    compatibleMode = nodeInfo != null,
                )
            }
        }.getOrElse {
            tryRun {
                MastodonInstanceService("https://$host/").instanceV1().let {
                    requireNotNull(it.title)
                    NodeDetection(
                        host = host,
                        software = nodeInfo ?: "Mastodon",
                        compatibleMode = nodeInfo != null,
                    )
                }
            }.getOrNull()
        }
    }
}
