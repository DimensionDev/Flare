package dev.dimension.flare.shared.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

public class AndroidImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            // --- Step 1: Smart Decode & Downsampling (Prevent OOM) ---
            // Decode only the bounds first to calculate the sample size
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            // Calculate inSampleSize
            options.inSampleSize =
                calculateInSampleSize(
                    options,
                    maxDimensions.first,
                    maxDimensions.second,
                )

            // Decode the actual bitmap with subsampling
            options.inJustDecodeBounds = false
            val decodedBitmap =
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                    ?: throw IllegalArgumentException(
                        "Failed to decode image from provided byte array (size=${imageBytes.size} bytes)",
                    )
            var bitmap = decodedBitmap

            // --- Step 2: Exact Resizing ---
            // inSampleSize is not exact (powers of 2), so we resize to fit strict dimensions
            if (bitmap.width > maxDimensions.first || bitmap.height > maxDimensions.second) {
                val resized = getResizedBitmap(bitmap, maxDimensions.first, maxDimensions.second)
                if (resized != bitmap) {
                    bitmap.recycle()
                    bitmap = resized
                }
            }

            // --- Step 3: Compress File Size (Binary Search + Fallback) ---
            var stream = ByteArrayOutputStream()
            var attempt = 0

            while (attempt < 5) {
                stream = ByteArrayOutputStream()
                var minQ = 0
                var maxQ = 100
                var bestQ = 0

                // Binary search for the best quality
                while (minQ <= maxQ) {
                    val midQ = (minQ + maxQ) / 2
                    stream.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, midQ, stream)

                    if (stream.size() <= maxSize) {
                        bestQ = midQ
                        minQ = midQ + 1
                    } else {
                        maxQ = midQ - 1
                    }
                }

                // Compress again using the found best quality
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, bestQ, stream)

                if (stream.size() <= maxSize) {
                    break // Success
                } else {
                    // If the lowest quality is still too large, resize down by 20%
                    val newW = (bitmap.width * 0.8).roundToInt()
                    val newH = (bitmap.height * 0.8).roundToInt()

                    // Safety check to prevent resizing to 0
                    if (newW < 50 || newH < 50) break

                    val scaled = bitmap.scale(newW, newH)
                    if (bitmap != scaled) bitmap.recycle()
                    bitmap = scaled
                }
                attempt++
            }

            val result = stream.toByteArray()
            stream.close()

            if (!bitmap.isRecycled) bitmap.recycle()

            return@withContext result
        }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getResizedBitmap(
        bm: Bitmap,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap {
        val width = bm.width
        val height = bm.height

        if (width <= maxWidth && height <= maxHeight) return bm

        val ratio = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).roundToInt()
        val newHeight = (height * ratio).roundToInt()

        return bm.scale(newWidth, newHeight)
    }
}
