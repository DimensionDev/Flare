package dev.dimension.flare.data.translation.image

import java.io.File
import javax.imageio.ImageIO

internal actual fun inspectTranslationImage(path: String): TranslationImageInfo {
    // shortcut: Desktop validates JPEG/PNG with ImageIO; add a decoder when Desktop WebP support is required.
    try {
        ImageIO.createImageInputStream(File(path)).use { stream ->
            val reader =
                ImageIO.getImageReaders(stream).asSequence().firstOrNull()
                    ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
            try {
                reader.input = stream
                if (reader.getNumImages(true) != 1) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val format = reader.formatName.lowercase()
                val mime =
                    when (format) {
                        "jpeg", "jpg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                    }
                val warnings = mutableListOf<String>()
                reader.addIIOReadWarningListener { _, warning -> warnings.add(warning) }
                val params = reader.defaultReadParam
                params.setSourceSubsampling((width / 1024).coerceAtLeast(1), (height / 1024).coerceAtLeast(1), 0, 0)
                val decoded = reader.read(0, params)
                if (decoded == null || warnings.isNotEmpty() || width <= 0 || height <= 0) {
                    throw ImageTranslationException(ImageTranslationError.InvalidImage)
                }
                decoded.flush()
                return TranslationImageInfo(width, height, mime)
            } finally {
                reader.dispose()
            }
        }
    } catch (
        error: ImageTranslationException,
    ) {
        throw error
    } catch (_: Exception) {
        throw ImageTranslationException(ImageTranslationError.InvalidImage)
    }
}
