package dev.dimension.flare.shared.image

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import kotlinx.coroutines.test.runTest
import okio.Buffer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmImageCompressorTest {
    @Test
    fun jpegExifBeforeJfifRemainsDecodableAndOriented() =
        runTest {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "jpg", output)
            val original = output.toByteArray().withTestExifOrientation(6, beforeJfif = true)
            assertEquals(40, ImageIO.read(original.inputStream()).width)
            assertEquals(6, imageMetadata(original)?.orientation)
            val result = JvmImageCompressor().compress(original, 100_000, 40 to 40)
            assertEquals(ImageMetadata(20, 40), imageMetadata(result))
        }

    @Test
    fun jpegExifOrientationIsAppliedAndRemovedFromCompressedPixels() =
        runTest {
            val image = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply {
                color = Color.RED
                fillRect(0, 0, 20, 20)
                color = Color.BLUE
                fillRect(20, 0, 20, 20)
                dispose()
            }
            val output = ByteArrayOutputStream()
            ImageIO.write(image, "jpg", output)
            val jpeg = output.toByteArray()
            val original = jpeg.withTestExifOrientation(6)
            assertEquals(6, imageMetadata(original)?.orientation)
            val result = JvmImageCompressor().compress(original, 100_000, 40 to 40)
            val decoded = ImageIO.read(result.inputStream())
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            assertEquals(1, imageMetadata(result)?.orientation)
            assertTrue(Color(decoded.getRGB(10, 5)).red > 200)
            assertTrue(Color(decoded.getRGB(10, 35)).blue > 200)
        }

    @Test
    fun transparentImagesUseWhiteBackground() =
        runTest {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), "png", output)
            val result = JvmImageCompressor().compress(output.toByteArray(), 100_000, 20 to 20)
            val color = Color(ImageIO.read(result.inputStream()).getRGB(10, 10))
            assertTrue(color.red > 245 && color.green > 245 && color.blue > 245)
        }

    @Test
    fun impossibleSizeFailsInsteadOfReturningOversizedData() =
        runTest {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB), "png", output)
            assertFailsWith<IllegalStateException> { JvmImageCompressor().compress(output.toByteArray(), 1, 50 to 50) }
        }

    @Test
    fun longScreenshotKeepsAspectRatioAndFitsDimensions() =
        runTest {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(100, 12000, BufferedImage.TYPE_INT_RGB), "png", output)
            val result = JvmImageCompressor().compress(output.toByteArray(), 100_000, 1200 to 1200)
            val decoded = ImageIO.read(result.inputStream())
            assertEquals(10, decoded.width)
            assertEquals(1200, decoded.height)
        }

    @Test
    fun uploadPreparationPreservesBothGifFrames() =
        runTest {
            val output = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("gif").next()
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                writer.prepareWriteSequence(null)
                repeat(2) { index ->
                    val frame = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
                    frame.setRGB(0, 0, if (index == 0) 0xff0000 else 0x00ff00)
                    writer.writeToSequence(IIOImage(frame, null, null), null)
                }
                writer.endWriteSequence()
            }
            writer.dispose()
            val original = output.toByteArray()
            val prepared =
                UploadMedia
                    .fromBytes("animation.gif", original)
                    .compressImage(JvmImageCompressor(), ComposeConfig.Media.Compression())
            val buffer = Buffer()
            prepared.forEachChunk { bytes, count -> buffer.write(bytes, 0, count) }
            val uploaded = buffer.readByteArray()
            assertContentEquals(original, uploaded)
            ImageIO.createImageInputStream(uploaded.inputStream()).use { stream ->
                val reader = ImageIO.getImageReaders(stream).next()
                reader.input = stream
                assertEquals(2, reader.getNumImages(true))
                reader.dispose()
            }
        }

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
