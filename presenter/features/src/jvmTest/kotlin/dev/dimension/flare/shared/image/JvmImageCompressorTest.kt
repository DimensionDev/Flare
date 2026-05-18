package dev.dimension.flare.shared.image

import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmImageCompressorTest {
    @Test
    fun testCompressLargeImage() =
        runTest {
            // Create a large image (e.g. 2000x2000)
            val width = 2000
            val height = 2000
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            // Fill with some data to ensure it doesn't compress too trivially
            val graphics = image.createGraphics()
            for (x in 0 until width step 50) {
                for (y in 0 until height step 50) {
                    graphics.fillRect(x, y, 25, 25)
                }
            }
            graphics.dispose()

            val os = ByteArrayOutputStream()
            ImageIO.write(image, "jpg", os)
            val largeBytes = os.toByteArray()

            val compressor = JvmImageCompressor()
            val maxDimension = 500
            val maxSize = 100 * 1024L // 100KB

            val compressed = compressor.compress(largeBytes, maxSize, maxDimension to maxDimension)

            val compressedImage = ImageIO.read(java.io.ByteArrayInputStream(compressed))

            assertTrue(compressed.size <= maxSize, "Size should be <= $maxSize but was ${compressed.size}")
            assertTrue(compressedImage.width <= maxDimension, "Width should be <= $maxDimension but was ${compressedImage.width}")
            assertTrue(compressedImage.height <= maxDimension, "Height should be <= $maxDimension but was ${compressedImage.height}")
        }
}
