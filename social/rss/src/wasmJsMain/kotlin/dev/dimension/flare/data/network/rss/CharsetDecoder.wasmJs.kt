package dev.dimension.flare.data.network.rss

internal actual fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String? =
    when (charset.lowercase()) {
        "utf-8", "utf8", "us-ascii", "ascii" -> bytes.decodeToString()
        else -> null
    }
