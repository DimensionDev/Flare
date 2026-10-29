@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.shared.image.imageMetadata
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData

internal actual fun validatePluginIconImage(bytes: ByteArray) {
    val metadata = requireNotNull(imageMetadata(bytes)) { "Platform icon cannot be decoded" }
    requirePluginIconDimensions(metadata.width, metadata.height)
    bytes.usePinned { pinned ->
        val data = requireNotNull(CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert()))
        try {
            val source = requireNotNull(CGImageSourceCreateWithData(data, null)) { "Platform icon cannot be decoded" }
            try {
                val image = requireNotNull(CGImageSourceCreateImageAtIndex(source, 0u, null)) { "Platform icon cannot be decoded" }
                CGImageRelease(image)
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(data)
        }
    }
}
