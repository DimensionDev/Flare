package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object BlueskyPlatformDetector : PlatformDetector {
    override val priority: Int = 80

    override suspend fun detect(host: String): NodeDetection? =
        tryRun {
            BlueskyService("https://$host").describeServer().requireResponse()
            NodeDetection(
                host = host,
                software = "Bluesky",
                compatibleMode = false,
            )
        }.getOrNull()
}
