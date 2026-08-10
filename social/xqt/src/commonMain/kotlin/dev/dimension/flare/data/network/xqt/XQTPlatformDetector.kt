package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object XQTPlatformDetector : PlatformDetector {
    override val priority: Int = 100

    override suspend fun detect(host: String): NodeDetection? {
        val aliases = listOf(xqtOldHost, "xqt.social", xqtHost)
        if (!aliases.any { it.equals(host, ignoreCase = true) }) {
            return null
        }
        return NodeDetection(
            host = host,
            software = "xQt",
            compatibleMode = false,
        )
    }
}
