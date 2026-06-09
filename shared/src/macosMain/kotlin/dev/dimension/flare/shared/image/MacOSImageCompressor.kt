package dev.dimension.flare.shared.image

import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.providers.base.toByteArray
import dev.whyoleg.cryptography.providers.base.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import platform.AppKit.NSBitmapImageRep
import platform.AppKit.NSGraphicsContext
import platform.AppKit.NSImage
import platform.AppKit.NSImageCompressionFactor
import platform.AppKit.NSCompositeCopy
import platform.AppKit.NSJPEGFileType
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.AppKit.representationUsingType
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
            val data = imageBytes.toNSData()
            var image = NSImage(data = data)

            val currentW = image.size.useContents { width }
            val currentH = image.size.useContents { height }
            if (currentW <= 0.0 || currentH <= 0.0) {
                throw IllegalArgumentException(
                    "Failed to decode image from provided byte array (size=${imageBytes.size} bytes)",
                )
            }
            val maxW = maxDimensions.first.toDouble()
            val maxH = maxDimensions.second.toDouble()

            if (currentW > maxW || currentH > maxH) {
                val ratio = min(maxW / currentW, maxH / currentH)
                val newW = currentW * ratio
                val newH = currentH * ratio
                image = image.resize(newW, newH)
            }

            var resultData: NSData? = null
            var attempt = 0

            while (attempt < 5) {
                var minQ = 0.0
                var maxQ = 1.0
                var bestQ = 0.0

                while (minQ <= maxQ + 0.01) {
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
        val targetSize = CGSizeMake(width, height)
        val newImage = NSImage(size = targetSize)
        newImage.lockFocus()
        NSGraphicsContext.currentContext?.imageInterpolation = platform.AppKit.NSImageInterpolationHigh
        this.drawInRect(
            CGRectMake(0.0, 0.0, width, height),
            fromRect = CGRectMake(0.0, 0.0, this.size.useContents { this.width }, this.size.useContents { this.height }),
            operation = NSCompositeCopy,
            fraction = 1.0,
        )
        newImage.unlockFocus()
        return newImage
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
