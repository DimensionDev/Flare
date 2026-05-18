package dev.dimension.flare.shared.image

import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.providers.base.toByteArray
import dev.whyoleg.cryptography.providers.base.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.min

@OptIn(CryptographyProviderApi::class, ExperimentalForeignApi::class)
public class IosImageCompressor : ImageCompressor {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            val data = imageBytes.toNSData()
            var image = UIImage(data = data)

            val currentW = image.size.useContents { width }
            val currentH = image.size.useContents { height }
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
        this.drawInRect(CGRectMake(0.0, 0.0, width, height))
        val newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return newImage ?: this
    }
}
