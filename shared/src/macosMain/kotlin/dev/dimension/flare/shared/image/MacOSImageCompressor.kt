package dev.dimension.flare.shared.image

import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.providers.base.toByteArray
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import platform.AppKit.NSBitmapImageRep
import platform.AppKit.NSColor
import platform.AppKit.NSCompositeSourceOver
import platform.AppKit.NSDeviceRGBColorSpace
import platform.AppKit.NSGraphicsContext
import platform.AppKit.NSImage
import platform.AppKit.NSImageCompressionFactor
import platform.AppKit.NSJPEGFileType
import platform.AppKit.NSRectFill
import platform.AppKit.representationUsingType
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import kotlin.math.min
import kotlin.native.HiddenFromObjC

@Single(binds = [ImageCompressor::class])
@OptIn(CryptographyProviderApi::class, ExperimentalForeignApi::class)
@HiddenFromObjC
public class MacOSImageCompressor : ImageCompressor {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            require(maxSize > 0 && maxDimensions.first > 0 && maxDimensions.second > 0)
            val thumbnail = requireNotNull(downsampleImage(imageBytes, maxDimensions)) { "Failed to decode image" }
            var image =
                try {
                    NSImage(
                        cGImage = thumbnail,
                        size = CGSizeMake(CGImageGetWidth(thumbnail).toDouble(), CGImageGetHeight(thumbnail).toDouble()),
                    )
                } finally {
                    CGImageRelease(thumbnail)
                }

            val currentW = image.size.useContents { width }
            val currentH = image.size.useContents { height }
            if (currentW <= 0.0 || currentH <= 0.0) {
                throw IllegalArgumentException(
                    "Failed to decode image from provided byte array (size=${imageBytes.size} bytes)",
                )
            }
            val maxW = maxDimensions.first.toDouble()
            val maxH = maxDimensions.second.toDouble()

            run {
                val ratio = min(1.0, min(maxW / currentW, maxH / currentH))
                val newW = currentW * ratio
                val newH = currentH * ratio
                image = image.resize(newW, newH)
            }

            var resultData: NSData? = null
            var attempt = 0

            while (attempt < 5) {
                currentCoroutineContext().ensureActive()
                var minQ = 0.0
                var maxQ = 1.0
                var bestQ = 0.0

                while (minQ <= maxQ + 0.01) {
                    currentCoroutineContext().ensureActive()
                    val midQ = (minQ + maxQ) / 2.0
                    val currentData = image.jpegData(midQ)
                    val size = currentData?.length ?: 0UL

                    if (size.toLong() <= maxSize) {
                        bestQ = midQ
                        resultData = currentData
                        minQ = midQ + 0.05
                    } else {
                        maxQ = midQ - 0.05
                    }
                }

                val finalCheck = image.jpegData(bestQ)
                if ((finalCheck?.length ?: 0UL).toLong() <= maxSize) {
                    resultData = finalCheck
                    break
                }

                val w = image.size.useContents { width } * 0.8
                val h = image.size.useContents { height } * 0.8
                if (w < 50 || h < 50) break

                image = image.resize(w, h)
                attempt++
            }

            return@withContext resultData?.toByteArray()
                ?: throw IllegalStateException("Image compression failed: unable to meet max size $maxSize bytes after all attempts")
        }

    private fun NSImage.resize(
        width: Double,
        height: Double,
    ): NSImage {
        // Explicit pixel dimensions avoid lockFocus doubling the output on Retina displays.
        val pixelsWide = width.toLong().coerceAtLeast(1)
        val pixelsHigh = height.toLong().coerceAtLeast(1)
        val bitmap =
            NSBitmapImageRep(
                bitmapDataPlanes = null,
                pixelsWide = pixelsWide,
                pixelsHigh = pixelsHigh,
                bitsPerSample = 8,
                samplesPerPixel = 4,
                hasAlpha = true,
                isPlanar = false,
                colorSpaceName = NSDeviceRGBColorSpace,
                bytesPerRow = 0,
                bitsPerPixel = 0,
            )
        val context = requireNotNull(NSGraphicsContext.graphicsContextWithBitmapImageRep(bitmap))
        NSGraphicsContext.saveGraphicsState()
        try {
            NSGraphicsContext.setCurrentContext(context)
            context.imageInterpolation = platform.AppKit.NSImageInterpolationHigh
            val rect = CGRectMake(0.0, 0.0, pixelsWide.toDouble(), pixelsHigh.toDouble())
            NSColor.whiteColor.setFill()
            NSRectFill(rect)
            this.drawInRect(
                rect,
                fromRect = CGRectMake(0.0, 0.0, this.size.useContents { this.width }, this.size.useContents { this.height }),
                operation = NSCompositeSourceOver,
                fraction = 1.0,
            )
        } finally {
            NSGraphicsContext.restoreGraphicsState()
        }
        return NSImage(size = CGSizeMake(pixelsWide.toDouble(), pixelsHigh.toDouble())).apply { addRepresentation(bitmap) }
    }

    private fun NSImage.jpegData(quality: Double): NSData? {
        val tiffData = TIFFRepresentation ?: return null
        val bitmapImageRep = NSBitmapImageRep.imageRepWithData(tiffData) ?: return null
        return bitmapImageRep.representationUsingType(
            NSJPEGFileType,
            properties = mapOf(NSImageCompressionFactor to quality),
        )
    }
}
