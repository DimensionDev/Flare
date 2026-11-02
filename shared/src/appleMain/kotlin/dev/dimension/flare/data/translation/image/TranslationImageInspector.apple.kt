package dev.dimension.flare.data.translation.image

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFURLCreateFromFileSystemRepresentation
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceGetStatus
import platform.ImageIO.CGImageSourceGetType
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceShouldCache
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.ImageIO.kCGImageStatusComplete

@OptIn(ExperimentalForeignApi::class)
internal actual fun inspectTranslationImage(path: String): TranslationImageInfo {
    val bytes = path.encodeToByteArray()
    val url =
        bytes.usePinned {
            CFURLCreateFromFileSystemRepresentation(null, it.addressOf(0).reinterpret(), bytes.size.toLong(), false)
        } ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
    try {
        val source = CGImageSourceCreateWithURL(url, null) ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
        try {
            if (CGImageSourceGetCount(source) != 1UL || CGImageSourceGetStatus(source) != kCGImageStatusComplete) {
                throw ImageTranslationException(ImageTranslationError.InvalidImage)
            }
            val type = CGImageSourceGetType(source) ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
            val identifier =
                memScoped {
                    val buffer = allocArray<ByteVar>(256)
                    if (!CFStringGetCString(type, buffer, 256, kCFStringEncodingUTF8)) {
                        throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                    }
                    buffer.toKString()
                }
            val mime =
                when (identifier) {
                    "public.jpeg" -> "image/jpeg"
                    "public.png" -> "image/png"
                    "org.webmproject.webp" -> "image/webp"
                    else -> throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                }
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0UL, null)
            var orientation = 1
            if (properties != null) {
                try {
                    val value = CFDictionaryGetValue(properties, kCGImagePropertyOrientation)
                    if (value != null) {
                        memScoped {
                            val number = alloc<IntVar>()
                            if (CFNumberGetValue(value.reinterpret(), kCFNumberIntType, number.ptr)) orientation = number.value
                        }
                    }
                } finally {
                    CFRelease(properties)
                }
            }
            val options =
                memScoped {
                    val keys = allocArray<COpaquePointerVar>(1)
                    val values = allocArray<COpaquePointerVar>(1)
                    keys[0] = kCGImageSourceShouldCache
                    values[0] = kCFBooleanFalse
                    CFDictionaryCreate(null, keys, values, 1, null, null)
                }
            val image =
                try {
                    CGImageSourceCreateImageAtIndex(source, 0UL, options)
                } finally {
                    if (options != null) CFRelease(options)
                } ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
            try {
                val width = CGImageGetWidth(image).toInt()
                val height = CGImageGetHeight(image).toInt()
                if (width <= 0 || height <= 0) throw ImageTranslationException(ImageTranslationError.InvalidImage)
                memScoped {
                    val size = alloc<IntVar> { value = 512 }
                    val number = CFNumberCreate(null, kCFNumberIntType, size.ptr)
                    val keys = allocArray<COpaquePointerVar>(2)
                    val values = allocArray<COpaquePointerVar>(2)
                    keys[0] = kCGImageSourceCreateThumbnailFromImageAlways
                    values[0] = kCFBooleanTrue
                    keys[1] = kCGImageSourceThumbnailMaxPixelSize
                    values[1] = number
                    val decodeOptions = CFDictionaryCreate(null, keys, values, 2, null, null)
                    try {
                        val decoded =
                            CGImageSourceCreateThumbnailAtIndex(source, 0UL, decodeOptions)
                                ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
                        CGImageRelease(decoded)
                    } finally {
                        if (decodeOptions != null) CFRelease(decodeOptions)
                        if (number != null) CFRelease(number)
                    }
                }
                return TranslationImageInfo(if (orientation in 5..8) height else width, if (orientation in 5..8) width else height, mime)
            } finally {
                CGImageRelease(image)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(url)
    }
}
