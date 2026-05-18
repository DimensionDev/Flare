package dev.dimension.flare.data.network.rss

internal expect fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String?
