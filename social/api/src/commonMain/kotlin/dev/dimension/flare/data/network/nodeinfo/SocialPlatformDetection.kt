package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.model.SocialPlatformRegistry

public suspend fun SocialPlatformRegistry.detectPlatformType(host: String): NodeData {
    val hostCleaned = host.normalizeHost()
    return specs
        .map { it.detector }
        .distinct()
        .sortedByDescending { it.priority }
        .firstNotNullOfOrNull { detector -> detector.detect(hostCleaned) }
        ?: throw IllegalArgumentException("Unsupported platform: $hostCleaned")
}

private fun String.normalizeHost(): String =
    trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
