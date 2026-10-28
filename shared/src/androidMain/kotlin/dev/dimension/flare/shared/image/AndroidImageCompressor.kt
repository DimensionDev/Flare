package dev.dimension.flare.shared.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

@Single(binds = [ImageCompressor::class])
public class AndroidImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            require(maxSize > 0 && maxDimensions.first > 0 && maxDimensions.second > 0)
            val metadata = requireNotNull(imageMetadata(imageBytes)) { "Failed to read image metadata" }
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
                    if (metadata.orientation in 5..8) maxDimensions.second else maxDimensions.first,
                    if (metadata.orientation in 5..8) maxDimensions.first else maxDimensions.second,
                )

            // Decode the actual bitmap with subsampling
            options.inJustDecodeBounds = false
            val decodedBitmap =
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                    ?: throw IllegalArgumentException(
                        "Failed to decode image from provided byte array (size=${imageBytes.size} bytes)",
                    )
            var bitmap = decodedBitmap
            try {
                if (metadata.orientation in 2..8) {
                    val transform =
                        Matrix().apply {
                            when (metadata.orientation) {
                                2 -> {
                                    setScale(-1f, 1f)
                                }

                                3 -> {
                                    setRotate(180f)
                                }

                                4 -> {
                                    setScale(1f, -1f)
                                }

                                5 -> {
                                    setRotate(90f)
                                    postScale(-1f, 1f)
                                }

                                6 -> {
                                    setRotate(90f)
                                }

                                7 -> {
                                    setRotate(90f)
                                    postScale(1f, -1f)
                                }

                                8 -> {
                                    setRotate(270f)
                                }
                            }
                        }
                    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
                    if (oriented != bitmap) bitmap.recycle()
                    bitmap = oriented
                }

                // --- Step 2: Exact Resizing ---
                // inSampleSize is not exact (powers of 2), so we resize to fit strict dimensions
                if (bitmap.width > maxDimensions.first || bitmap.height > maxDimensions.second) {
                    val resized = getResizedBitmap(bitmap, maxDimensions.first, maxDimensions.second)
                    if (resized != bitmap) {
                        bitmap.recycle()
                        bitmap = resized
                    }
                }
                if (bitmap.hasAlpha()) {
                    val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    Canvas(flattened).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(bitmap, 0f, 0f, null)
                    }
                    bitmap.recycle()
                    bitmap = flattened
                }

                // --- Step 3: Compress File Size (Binary Search + Fallback) ---
                var stream = ByteArrayOutputStream()
                var attempt = 0

                while (attempt < 5) {
                    currentCoroutineContext().ensureActive()
                    stream = ByteArrayOutputStream()
                    var minQ = 0
                    var maxQ = 100
                    var bestQ = 0

                    // Binary search for the best quality
                    while (minQ <= maxQ) {
                        currentCoroutineContext().ensureActive()
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

                check(result.size <= maxSize) { "Image compression could not meet the size limit" }
                return@withContext result
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
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
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
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
        val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)

        return bm.scale(newWidth, newHeight)
    }
}
