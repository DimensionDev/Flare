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
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import kotlin.math.min
import kotlin.native.HiddenFromObjC

@Single(binds = [ImageCompressor::class])
@OptIn(CryptographyProviderApi::class, ExperimentalForeignApi::class)
@HiddenFromObjC
public class IosImageCompressor : ImageCompressor {
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
                    UIImage.imageWithCGImage(thumbnail)
                } finally {
                    CGImageRelease(thumbnail)
                }

            val currentW = image.size.useContents { width }
            val currentH = image.size.useContents { height }
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
                    val currentData = UIImageJPEGRepresentation(image, midQ)
                    val size = currentData?.length ?: 0UL

                    if (size.toLong() <= maxSize) {
                        bestQ = midQ
                        resultData = currentData
                        minQ = midQ + 0.05
                    } else {
                        maxQ = midQ - 0.05
                    }
                }

                val finalCheck = UIImageJPEGRepresentation(image, bestQ)
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

    private fun UIImage.resize(
        width: Double,
        height: Double,
    ): UIImage {
        val targetSize = CGSizeMake(width, height)
        UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, width, height))
        this.drawInRect(CGRectMake(0.0, 0.0, width, height))
        val newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return newImage ?: this
    }
}
