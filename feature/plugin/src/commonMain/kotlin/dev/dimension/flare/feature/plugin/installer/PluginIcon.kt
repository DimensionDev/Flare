package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.common.MimeTypes

internal fun pluginIconExtension(bytes: ByteArray): String {
    require(bytes.isNotEmpty() && bytes.size <= FppLimits.MAX_ICON_BYTES) { "Platform icon exceeds its size limit" }
    if (bytes.size >= 6 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte() && bytes[3] == 0.toByte()) {
        return "ico"
    }
    return requireNotNull(MimeTypes.extensionFor(MimeTypes.detectFromBytes(bytes))?.takeIf { it in setOf("png", "jpg", "gif", "webp") }) {
        "Use a PNG, JPEG, WebP, GIF or ICO icon; SVG is not supported"
    }
}

internal fun requirePluginIconDimensions(
    width: Int,
    height: Int,
) {
    require(width in 1..1024 && height in 1..1024) { "Platform icon dimensions must be between 1 and 1024 pixels" }
}

/** Decode for validation only. The original bytes, including animation, are stored unchanged. */
internal expect fun validatePluginIconImage(bytes: ByteArray)
