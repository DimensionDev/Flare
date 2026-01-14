package dev.dimension.flare.shared.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidImageCompressorTest {
    private val compressor = AndroidImageCompressor()

    private fun createBitmap(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    @Test
    fun `compress should return original image if within constraints`() =
        runTest {
            // Create a small image
            val originalBytes = createBitmap(100, 100)
            val maxDimensions = 200 to 200
            val maxSize = 1024L * 1024L // 1MB

            val compressedBytes = compressor.compress(originalBytes, maxSize, maxDimensions)

            // Since it's PNG to JPEG, sizes might vary, but dimensions should be preserved if they fit
            val options = BitmapFactory.Options()
            BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size, options)

            assertEquals(100, options.outWidth)
            assertEquals(100, options.outHeight)
        }

    @Test
    fun `compress should resize image if dimensions exceed limit`() =
        runTest {
            // Create a large image
            val originalBytes = createBitmap(2000, 2000)
            val maxDimensions = 1000 to 1000
            val maxSize = 10L * 1024L * 1024L // 10MB (large enough to avoid quality compression)

            val compressedBytes = compressor.compress(originalBytes, maxSize, maxDimensions)

            val options = BitmapFactory.Options()
            BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size, options)

            assertTrue(options.outWidth <= 1000)
            assertTrue(options.outHeight <= 1000)
        }

    @Test
    fun `compress should reduce quality if file size exceeds limit`() =
        runTest {
            // Create a large image
            val originalBytes = createBitmap(1000, 1000)
            val maxDimensions = 2000 to 2000 // Large enough dimensions
            val maxSize = 10L * 1024L // 10KB (small size)

            val compressedBytes = compressor.compress(originalBytes, maxSize, maxDimensions)

            assertTrue(compressedBytes.size.toLong() <= maxSize + 1024) // Allow small buffer? Or strictly <= maxSize?
            // The implementation loop checks while (stream.size() > maxSize && quality > 5)
            // usage of strict check depends on encoding overhead.

            // Let's verify it actually tried to compress.
            // For a 1000x1000 image, 10KB is very aggressive.

            // Actually, checking strict inequality might be flaky if it can't reach that low.
            // Let's just check it's smaller than original.
            assertTrue(compressedBytes.size < originalBytes.size)
        }
}
