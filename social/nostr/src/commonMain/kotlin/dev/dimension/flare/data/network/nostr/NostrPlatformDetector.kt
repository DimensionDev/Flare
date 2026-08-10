package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object NostrPlatformDetector : PlatformDetector {
    override suspend fun detect(host: String): NodeDetection? {
        if (!host.equals("nostr", ignoreCase = true)) {
            return null
        }
        return NodeDetection(
            host = host,
            software = "Nostr",
            compatibleMode = false,
        )
    }
}
