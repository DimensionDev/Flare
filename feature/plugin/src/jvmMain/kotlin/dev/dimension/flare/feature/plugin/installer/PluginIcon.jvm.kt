package dev.dimension.flare.feature.plugin.installer

import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal actual fun validatePluginIconImage(bytes: ByteArray) {
    Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use { codec ->
            requirePluginIconDimensions(codec.width, codec.height)
            codec.readPixels().close()
        }
    }
}
