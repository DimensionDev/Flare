package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.model.defaultSocialPlatformRegistry

internal suspend fun detectPlatformType(host: String): NodeData {
    val hostCleaned = NodeInfoService.normalizeHost(host)
    return defaultSocialPlatformRegistry.specs
        .map { it.detector }
        .distinct()
        .sortedByDescending { it.priority }
        .firstNotNullOfOrNull { detector -> detector.detect(hostCleaned) }
        ?: throw IllegalArgumentException("Unsupported platform: $hostCleaned")
}
