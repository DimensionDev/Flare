package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object MisskeyPlatformDetector : PlatformDetector {
    override val priority: Int = 70

    override suspend fun detect(host: String): NodeDetection? {
        val nodeInfo =
            tryRun {
                NodeInfoService.fetchNodeInfo(host)
            }.getOrNull()

        if (nodeInfo?.equals("misskey", ignoreCase = true) == true) {
            return NodeDetection(
                host = host,
                software = nodeInfo,
                compatibleMode = false,
            )
        }

        return tryRun {
            MisskeyService("https://$host/api/").meta(MetaRequest()).let {
                requireNotNull(it.name)
                NodeDetection(
                    host = host,
                    software = nodeInfo ?: "Misskey",
                    compatibleMode = true,
                )
            }
        }.getOrNull()
    }
}
