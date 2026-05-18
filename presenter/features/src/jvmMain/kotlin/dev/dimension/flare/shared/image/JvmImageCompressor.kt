package dev.dimension.flare.shared.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.min

public class JvmImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val inputStream = ByteArrayInputStream(imageBytes)
            var image = ImageIO.read(inputStream) ?: throw IllegalArgumentException("Failed to decode image")

            val (maxWidth, maxHeight) = maxDimensions

            if (image.width > maxWidth || image.height > maxHeight) {
                val scale = min(maxWidth.toFloat() / image.width, maxHeight.toFloat() / image.height)
                val newWidth = (image.width * scale).toInt()
                val newHeight = (image.height * scale).toInt()

                // Create resized image
                val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
                val g: Graphics2D = resizedImage.createGraphics()
                // Use smooth scaling
                val scaled = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
                g.drawImage(scaled, 0, 0, null)
                g.dispose()

                image = resizedImage
            } else {
                // Convert to RGB if needed to ensure JPEG compatibility (drop alpha)
                if (image.type != BufferedImage.TYPE_INT_RGB) {
                    val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                    val g = rgbImage.createGraphics()
                    g.drawImage(image, 0, 0, null)
                    g.dispose()
                    image = rgbImage
                }
            }

            // Compress loop
            var quality = 1.0f
            var resultBytes: ByteArray? = null

            val writers = ImageIO.getImageWritersByFormatName("jpeg")
            if (!writers.hasNext()) throw IllegalStateException("No JPEG writer found")
            val writer = writers.next()

            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            }

            while (quality > 0.05f) {
                val outputStream = ByteArrayOutputStream()
                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios

                if (param.canWriteCompressed()) {
                    param.compressionQuality = quality
                }
                writer.write(null, IIOImage(image, null, null), param)

                ios.close()
                val bytes = outputStream.toByteArray()
                if (bytes.size <= maxSize) {
                    resultBytes = bytes
                    break
                }
                quality -= 0.05f
            }

            writer.dispose()

            resultBytes ?: run {
                // Fail safe: return last attempt
                val outputStream = ByteArrayOutputStream()
                val ios = ImageIO.createImageOutputStream(outputStream)
                val newWriter = ImageIO.getImageWritersByFormatName("jpeg").next()
                newWriter.output = ios
                val newParam = newWriter.defaultWriteParam
                if (newParam.canWriteCompressed()) {
                    newParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    newParam.compressionQuality = quality
                }
                newWriter.write(null, IIOImage(image, null, null), newParam)
                newWriter.dispose()
                ios.close()
                outputStream.toByteArray()
            }
        }
}
