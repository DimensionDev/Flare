package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object VVOPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeDetection? {
        val aliases = listOf(vvoHost, vvo, vvoHostShort, "vvo.social", vvoHostLong)
        if (!aliases.any { it.equals(host, ignoreCase = true) }) {
            return null
        }
        return NodeDetection(
            host = host,
            software = "VVo",
            compatibleMode = false,
        )
    }
}
