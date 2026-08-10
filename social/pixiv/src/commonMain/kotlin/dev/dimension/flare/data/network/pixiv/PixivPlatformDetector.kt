package dev.dimension.flare.data.network.pixiv

import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object PixivPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeDetection? {
        if (!PIXIV_HOST.equals(host, ignoreCase = true) && !"www.$PIXIV_HOST".equals(host, ignoreCase = true)) {
            return null
        }
        return NodeDetection(
            host = PIXIV_HOST,
            software = "Pixiv",
            compatibleMode = false,
        )
    }
}
