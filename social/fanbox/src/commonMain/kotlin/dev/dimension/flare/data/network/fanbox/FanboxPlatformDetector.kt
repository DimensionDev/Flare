package dev.dimension.flare.data.network.fanbox

import dev.dimension.flare.data.platform.FANBOX_HOST
import dev.dimension.flare.data.platform.FANBOX_WEB_HOST
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object FanboxPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeDetection? {
        if (!FANBOX_HOST.equals(host, ignoreCase = true) && !FANBOX_WEB_HOST.equals(host, ignoreCase = true)) {
            return null
        }
        return NodeDetection(
            host = FANBOX_HOST,
            software = "Fanbox",
            compatibleMode = false,
        )
    }
}
