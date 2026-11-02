package dev.dimension.flare.shared.image

import android.graphics.BitmapFactory
import android.media.ExifInterface
import dev.dimension.flare.common.MimeTypes

public actual fun imageMetadata(bytes: ByteArray): ImageMetadata? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val orientation =
        runCatching {
            bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrDefault(1)
    return ImageMetadata(
        options.outWidth,
        options.outHeight,
        orientation.takeIf { it in 1..8 } ?: 1,
        MimeTypes.detectFromBytes(bytes) == "image/gif" || bytes.hasAnimatedChunks(),
    )
}
