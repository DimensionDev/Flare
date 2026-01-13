package dev.dimension.flare.shared.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

public class AndroidImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            val (maxWidth, maxHeight) = maxDimensions

            var inSampleSize = 1
            if (srcHeight > maxHeight || srcWidth > maxWidth) {
                val halfHeight: Int = srcHeight / 2
                val halfWidth: Int = srcWidth / 2

                while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions =
                BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }

            var bitmap =
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
                    ?: throw IllegalArgumentException("Failed to decode image")

            // Resize if still too large after sampling
            if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val scale = min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            var quality = 100
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

            while (stream.size() > maxSize && quality > 5) {
                stream.reset()
                quality -= 5
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }

            bitmap.recycle()
            stream.toByteArray()
        }
}
