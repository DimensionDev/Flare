@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.shared.image

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGImageRef
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCache
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import kotlin.math.max
import kotlin.math.min
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public actual fun imageMetadata(bytes: ByteArray): ImageMetadata? =
    bytes.withImageSource { source ->
        val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return@withImageSource null
        try {
            memScoped {
                fun number(key: kotlinx.cinterop.CPointer<*>?): Int {
                    val value = CFDictionaryGetValue(properties, key) ?: return 0
                    val result = alloc<IntVar>()
                    return if (CFNumberGetValue(value.reinterpret(), kCFNumberIntType, result.ptr)) result.value else 0
                }
                ImageMetadata(
                    number(kCGImagePropertyPixelWidth),
                    number(kCGImagePropertyPixelHeight),
                    number(kCGImagePropertyOrientation).takeIf { it in 1..8 } ?: 1,
                    CGImageSourceGetCount(source) > 1u,
                ).takeIf { it.width > 0 && it.height > 0 }
            }
        } finally {
            CFRelease(properties)
        }
    }

internal fun downsampleImage(
    bytes: ByteArray,
    maxDimensions: Pair<Int, Int>,
): CGImageRef? {
    val metadata = imageMetadata(bytes) ?: return null
    val width = if (metadata.orientation in 5..8) metadata.height else metadata.width
    val height = if (metadata.orientation in 5..8) metadata.width else metadata.height
    val ratio = min(1.0, min(maxDimensions.first.toDouble() / width, maxDimensions.second.toDouble() / height))
    return bytes.withImageSource { source ->
        memScoped {
            val size = alloc<IntVar> { value = max(1, (max(width, height) * ratio).toInt()) }
            val number = CFNumberCreate(null, kCFNumberIntType, size.ptr) ?: return@memScoped null
            val options =
                CFDictionaryCreateMutable(null, 0, null, null) ?: run {
                    CFRelease(number)
                    return@memScoped null
                }
            try {
                CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
                CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                CFDictionarySetValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
                CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
            } finally {
                CFRelease(options)
                CFRelease(number)
            }
        }
    }
}

private fun <T> ByteArray.withImageSource(block: (CGImageSourceRef) -> T): T? {
    if (isEmpty()) return null
    return usePinned { pinned ->
        // Returned thumbnails may retain the source beyond the Kotlin buffer's pinned lifetime.
        val data = CFDataCreate(null, pinned.addressOf(0).reinterpret(), size.convert()) ?: return@usePinned null
        try {
            val source = CGImageSourceCreateWithData(data, null) ?: return@usePinned null
            try {
                block(source)
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(data)
        }
    }
}
