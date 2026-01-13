package dev.dimension.flare.shared.image

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.min

public class IosImageCompressor : ImageCompressor {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            val data =
                imageBytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
                }

            val image = UIImage(data = data)

            val (maxWidth, maxHeight) = maxDimensions
            var currentImage = image

            val width = image.size.useContents { width }
            val height = image.size.useContents { height }

            if (width > maxWidth || height > maxHeight) {
                val widthRatio = maxWidth.toDouble() / width
                val heightRatio = maxHeight.toDouble() / height
                val ratio = min(widthRatio, heightRatio)

                val newWidth = width * ratio
                val newHeight = height * ratio

                val newSize = CGSizeMake(newWidth, newHeight)
                val rect = CGRectMake(0.0, 0.0, newWidth, newHeight)

                UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
                image.drawInRect(rect)
                val newImage = UIGraphicsGetImageFromCurrentImageContext()
                UIGraphicsEndImageContext()

                if (newImage != null) {
                    currentImage = newImage
                }
            }

            var compressionQuality = 1.0
            var jpegData = UIImageJPEGRepresentation(currentImage, compressionQuality)

            while ((jpegData?.length ?: 0UL) > maxSize.toULong() && compressionQuality > 0.05) {
                compressionQuality -= 0.05
                jpegData = UIImageJPEGRepresentation(currentImage, compressionQuality)
            }

            val resultData = jpegData ?: throw IllegalArgumentException("Failed to compress image")

            if (resultData.length == 0UL) return@withContext ByteArray(0)

            resultData.bytes!!.readBytes(resultData.length.toInt())
        }
}
