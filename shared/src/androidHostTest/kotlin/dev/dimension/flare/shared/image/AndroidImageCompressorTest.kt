package dev.dimension.flare.shared.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageCompressorTest {
    private val compressor = AndroidImageCompressor()

    @Test
    fun transparentImagesUseWhiteBackground() =
        runTest {
            val result = compressor.compress(transparentTestPng, 100_000, 20 to 20)
            val bitmap = BitmapFactory.decodeByteArray(result, 0, result.size)
            val color = bitmap.getPixel(0, 0)
            assertTrue(Color.red(color) > 245 && Color.green(color) > 245 && Color.blue(color) > 245)
            bitmap.recycle()
        }

    @Test
    fun impossibleSizeFailsInsteadOfReturningOversizedData() =
        runTest {
            assertFailsWith<IllegalStateException> { compressor.compress(transparentTestPng, 1, 20 to 20) }
        }

    @Test
    fun longScreenshotKeepsAspectRatioAndFitsDimensions() =
        runTest {
            val result = compressor.compress(createBitmap(100, 12000), 100_000, 1200 to 1200)
            val metadata = imageMetadata(result)
            assertEquals(10, metadata?.width)
            assertEquals(1200, metadata?.height)
        }

    @Test
    fun exifOrientationIsAppliedToCompressedPixels() =
        runTest {
            val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            bitmap.recycle()
            val original = stream.toByteArray().withTestExifOrientation(6)
            assertEquals(6, imageMetadata(original)?.orientation)
            val metadata = imageMetadata(compressor.compress(original, 100_000, 40 to 40))
            assertEquals(20, metadata?.width)
            assertEquals(40, metadata?.height)
            assertEquals(1, metadata?.orientation)
        }

    private fun createBitmap(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
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
            val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
            val random = Random(42)
            val pixels = IntArray(1_000_000) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
            bitmap.setPixels(pixels, 0, 1000, 0, 0, 1000, 1000)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            val originalBytes = stream.toByteArray()
            val maxDimensions = 2000 to 2000
            val maxSize = 10L * 1024L
            assertTrue(originalBytes.size > maxSize)

            val compressedBytes = compressor.compress(originalBytes, maxSize, maxDimensions)

            assertTrue(compressedBytes.isNotEmpty() && compressedBytes.size <= maxSize)
        }
}
