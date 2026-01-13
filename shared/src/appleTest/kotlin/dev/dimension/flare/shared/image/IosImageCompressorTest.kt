package dev.dimension.flare.shared.image
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosImageCompressorTest {
    @Test
    fun testCompressToSpecificSizeAndDimension() =
        runTest {
            // --- 1. Prepare Data ---
            // Generate a large image (2000x2000) to exceed both size and dimension limits
            val originalWidth = 2000.0
            val originalHeight = 2000.0
            val originalImage = generateTestImage(originalWidth, originalHeight)

            // Convert to ByteArray (simulating data from gallery)
            // 1.0 quality usually results in a file > 5MB for this resolution
            val originalBytes =
                originalImage.jpegData(1.0)
                    ?: throw IllegalStateException("Failed to create test data")

            // --- 2. Set Limits ---
            val maxFileSize = 300 * 1024L // Limit to 300 KB
            val maxDimensions = 1000 to 1000 // Limit dimensions to 1000px

            // --- 3. Execute Compression ---
            val resultBytes =
                IosImageCompressor().compress(
                    imageBytes = originalBytes,
                    maxSize = maxFileSize,
                    maxDimensions = maxDimensions,
                )

            // --- 4. Verify Results ---

            // Assert 1: File size limit
            assertTrue(
                resultBytes.size <= maxFileSize,
                "File size (${resultBytes.size}) exceeds limit ($maxFileSize)",
            )

            // Assert 2: Dimension limit
            val resultImage = UIImage(data = resultBytes.toNSData())
            val resultWidth = resultImage.size.useContents { width }
            val resultHeight = resultImage.size.useContents { height }

            assertTrue(
                resultWidth <= maxDimensions.first && resultHeight <= maxDimensions.second,
                "Image dimensions ($resultWidth x $resultHeight) exceed limit ($maxDimensions)",
            )

            // Assert 3: Data integrity
            assertTrue(resultBytes.isNotEmpty(), "Compressed result should not be empty")
        }

    // --- Helpers: Generate Test Image ---
    private fun generateTestImage(
        width: Double,
        height: Double,
    ): UIImage {
        val size = CGSizeMake(width, height)
        UIGraphicsBeginImageContextWithOptions(size, true, 1.0)

        // Fill red background
        UIColor.redColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, width, height))

        // Draw a blue line to add complexity
        UIColor.blueColor.setStroke()
        val path = UIBezierPath()
        path.moveToPoint(CGPointMake(0.0, 0.0))
        path.addLineToPoint(CGPointMake(width, height))
        path.lineWidth = 10.0
        path.stroke()

        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return image ?: UIImage()
    }

    private fun UIImage.jpegData(quality: Double): ByteArray? {
        val nsData = UIImageJPEGRepresentation(this, quality) ?: return null
        return nsData.toByteArray()
    }

    // --- Helpers: Data Conversion (Private copy for testing) ---
    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        val byteArray = ByteArray(length)
        if (length > 0) {
            byteArray.usePinned { pinned ->
                platform.posix.memcpy(pinned.addressOf(0), this.bytes, length.toULong())
            }
        }
        return byteArray
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData =
        memScoped {
            if (isEmpty()) return NSData()
            return this@toNSData.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), this@toNSData.size.toULong())
            }
        }
}
