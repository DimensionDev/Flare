package dev.dimension.flare.shared.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min

@Single(binds = [ImageCompressor::class])
public class JvmImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            require(maxSize > 0 && maxDimensions.first > 0 && maxDimensions.second > 0)
            val metadata = requireNotNull(imageMetadata(imageBytes)) { "Failed to read image metadata" }
            val rotated = metadata.orientation in 5..8
            var image =
                ImageIO.createImageInputStream(imageBytes.inputStream()).use { input ->
                    val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: error("Failed to decode image")
                    try {
                        reader.input = input
                        val sample =
                            max(
                                metadata.width / (if (rotated) maxDimensions.second else maxDimensions.first),
                                metadata.height / (if (rotated) maxDimensions.first else maxDimensions.second),
                            ).coerceAtLeast(1)
                        val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                        reader.read(0, params)
                    } finally {
                        reader.dispose()
                    }
                }
            val w = image.width.toDouble()
            val h = image.height.toDouble()
            val transform =
                when (metadata.orientation) {
                    2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
                    3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
                    4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
                    5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
                    6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
                    7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
                    8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
                    else -> AffineTransform()
                }
            val width = if (rotated) image.height else image.width
            val height = if (rotated) image.width else image.height
            val ratio = min(1.0, min(maxDimensions.first.toDouble() / width, maxDimensions.second.toDouble() / height))
            val normalized =
                BufferedImage(
                    (width * ratio).toInt().coerceAtLeast(1),
                    (height * ratio).toInt().coerceAtLeast(1),
                    BufferedImage.TYPE_INT_RGB,
                )
            normalized.createGraphics().apply {
                color = Color.WHITE
                fillRect(0, 0, normalized.width, normalized.height)
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                scale(ratio, ratio)
                drawImage(image, transform, null)
                dispose()
            }
            image.flush()
            image = normalized
            try {
                repeat(5) {
                    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull() ?: error("No JPEG writer found")
                    try {
                        for (quality in 100 downTo 5 step 5) {
                            currentCoroutineContext().ensureActive()
                            val output = ByteArrayOutputStream()
                            ImageIO.createImageOutputStream(output).use { stream ->
                                writer.output = stream
                                val params =
                                    writer.defaultWriteParam.apply {
                                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                                        compressionQuality = quality / 100f
                                    }
                                writer.write(null, IIOImage(image, null, null), params)
                            }
                            if (output.size() <= maxSize) return@withContext output.toByteArray()
                        }
                    } finally {
                        writer.dispose()
                    }
                    val resized =
                        BufferedImage(
                            (image.width * 0.8).toInt().coerceAtLeast(1),
                            (image.height * 0.8).toInt().coerceAtLeast(1),
                            BufferedImage.TYPE_INT_RGB,
                        )
                    resized.createGraphics().apply {
                        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                        drawImage(image, 0, 0, resized.width, resized.height, null)
                        dispose()
                    }
                    image.flush()
                    image = resized
                }
                error("Image compression could not meet the size limit")
            } finally {
                image.flush()
            }
        }
}
