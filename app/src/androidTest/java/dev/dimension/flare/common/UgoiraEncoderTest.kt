package dev.dimension.flare.common

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.media.UgoiraFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UgoiraEncoderTest {
    @Test
    fun encodesOneLoopWithVariableTimingWhiteAlphaAndEvenPadding(): Unit =
        runBlocking {
            val directory =
                File(
                    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "ugoira-test-${System.nanoTime()}",
                ).apply {
                    mkdirs()
                }
            try {
                val frames =
                    listOf(40, 80, 100).mapIndexed { index, duration ->
                        val image = Bitmap.createBitmap(255, 257, Bitmap.Config.ARGB_8888)
                        image.eraseColor(Color.TRANSPARENT)
                        val path = File(directory, "$index.png")
                        path.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        image.recycle()
                        UgoiraFrame(path.absolutePath, duration)
                    }
                val animation = UgoiraAnimation("test", frames, "original")
                val output = File(directory, "test.mp4")
                encodeUgoira(animation, output, false) {}
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(output.absolutePath)
                    assertEquals(1, extractor.trackCount)
                    val format = extractor.getTrackFormat(0)
                    assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, format.getString(MediaFormat.KEY_MIME))
                    assertEquals(0, format.getInteger(MediaFormat.KEY_WIDTH) % 2)
                    assertEquals(0, format.getInteger(MediaFormat.KEY_HEIGHT) % 2)
                    assertEquals(220_000L, format.getLong(MediaFormat.KEY_DURATION))
                    extractor.selectTrack(0)
                    val times = mutableListOf<Long>()
                    while (extractor.sampleTime >= 0) {
                        times += extractor.sampleTime
                        extractor.advance()
                    }
                    assertEquals(listOf(0L, 40_000, 120_000), times)
                } finally {
                    extractor.release()
                }
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(output.absolutePath)
                    val image = checkNotNull(reader.getFrameAtTime(0))
                    val pixel = image.getPixel(0, 0)
                    assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
                    image.recycle()
                } finally {
                    reader.release()
                }
                val canceled = File(directory, "cancel.mp4")
                try {
                    encodeUgoira(animation, canceled, false) { throw CancellationException() }
                    fail("Canceled export should throw")
                } catch (_: CancellationException) {
                    assertFalse(canceled.exists())
                }
            } finally {
                directory.deleteRecursively()
            }
        }
}
