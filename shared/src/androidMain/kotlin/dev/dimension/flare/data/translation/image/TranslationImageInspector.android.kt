package dev.dimension.flare.data.translation.image

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File

internal actual fun inspectTranslationImage(path: String): TranslationImageInfo {
    try {
        if (Build.VERSION.SDK_INT < 28) return inspectLegacyTranslationImage(path)
        var result: TranslationImageInfo? = null
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                if (info.isAnimated) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                val size = info.size
                val mime = info.mimeType
                if (mime !in listOf("image/jpeg", "image/png", "image/webp") || size.width <= 0 || size.height <= 0) {
                    throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
                }
                result = TranslationImageInfo(size.width, size.height, mime)
                decoder.setTargetSampleSize((maxOf(size.width, size.height) / 1024).coerceAtLeast(1))
                decoder.setOnPartialImageListener { false }
            }
        bitmap.recycle()
        return result ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
    } catch (
        error: ImageTranslationException,
    ) {
        throw error
    } catch (_: Exception) {
        throw ImageTranslationException(ImageTranslationError.InvalidImage)
    }
}

private fun inspectLegacyTranslationImage(path: String): TranslationImageInfo {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val mime = bounds.outMimeType
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || mime !in listOf("image/jpeg", "image/png", "image/webp")) {
        throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
    }
    java.io.RandomAccessFile(path, "r").use { file ->
        if (mime == "image/jpeg") {
            if (file.length() < 2) throw ImageTranslationException(ImageTranslationError.InvalidImage)
            file.seek(file.length() - 2)
            if (file.readUnsignedShort() != 0xffd9) throw ImageTranslationException(ImageTranslationError.InvalidImage)
        }
        if (mime == "image/webp" && file.length() > 21) {
            file.seek(12)
            val fourcc = ByteArray(4).also(file::readFully).decodeToString()
            if (fourcc == "VP8X") {
                file.seek(20)
                if (file.readUnsignedByte() and 2 != 0) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
            }
        }
    }
    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1) }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: throw ImageTranslationException(ImageTranslationError.InvalidImage)
    bitmap.recycle()
    return TranslationImageInfo(bounds.outWidth, bounds.outHeight, mime)
}
