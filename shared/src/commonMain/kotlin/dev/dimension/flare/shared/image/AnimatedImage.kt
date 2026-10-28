package dev.dimension.flare.shared.image

import dev.dimension.flare.common.MimeTypes

internal fun ByteArray.hasAnimatedChunks(): Boolean {
    if (MimeTypes.detectFromBytes(this) == "image/webp") {
        return size >= 21 && copyOfRange(12, 16).decodeToString() == "VP8X" && (this[20].toInt() and 2) != 0
    }
    if (MimeTypes.detectFromBytes(this) != "image/png") return false
    var offset = 8
    while (offset <= size - 12) {
        val length = (0..3).fold(0L) { value, i -> (value shl 8) or (this[offset + i].toLong() and 255) }
        if (length > size - offset - 12) return false
        when (copyOfRange(offset + 4, offset + 8).decodeToString()) {
            "acTL" -> return true
            "IDAT", "IEND" -> return false
        }
        offset += length.toInt() + 12
    }
    return false
}
