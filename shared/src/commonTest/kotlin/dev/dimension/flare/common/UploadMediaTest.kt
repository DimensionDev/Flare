package dev.dimension.flare.common

import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.shared.image.ImageCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UploadMediaTest {
    @Test
    fun largeFileUsesBoundedReadsAndCanBeReopened() =
        runTest {
            val size = 64L * 1024 * 1024 + 7
            var opens = 0
            var closes = 0
            var largestRead = 0L
            val media =
                UploadMedia.fromSource("VIDEO.MP4", "video/mp4", size) {
                    opens++
                    object : Source {
                        var remaining = size
                        val block = ByteArray(8192)

                        override fun read(
                            sink: Buffer,
                            byteCount: Long,
                        ): Long {
                            largestRead = maxOf(largestRead, byteCount)
                            if (remaining == 0L) return -1
                            val count = minOf(byteCount, remaining, block.size.toLong()).toInt()
                            sink.write(block, 0, count)
                            remaining -= count
                            return count.toLong()
                        }

                        override fun timeout(): Timeout = Timeout.NONE

                        override fun close() {
                            closes++
                        }
                    }
                }
            repeat(2) {
                var consumed = 0L
                media.forEachChunk { bytes, count ->
                    assertEquals(64 * 1024, bytes.size)
                    consumed += count
                }
                assertEquals(size, consumed)
            }
            assertTrue(largestRead <= 64 * 1024)
            assertEquals(opens, closes)
            assertEquals(3, opens) // Header inspection and two independent reads.
        }

    @Test
    fun cancellationClosesReaderAndRetryStartsAtTheBeginning() =
        runTest {
            var closed = 0
            val media =
                UploadMedia.fromSource("clip.mp4", "video/mp4", 100_000) {
                    object : okio.ForwardingSource(Buffer().write(ByteArray(100_000) { (it % 251).toByte() })) {
                        override fun close() {
                            closed++
                            super.close()
                        }
                    }
                }
            assertFailsWith<CancellationException> {
                media.forEachChunk { _, _ -> throw CancellationException("cancel") }
            }
            assertEquals(2, closed)
            var read = 0
            media.forEachChunk { bytes, count ->
                repeat(count) { assertEquals(((read + it) % 251).toByte(), bytes[it]) }
                read += count
            }
            assertEquals(100_000, read)
            assertEquals(3, closed)
        }

    @Test
    fun headerWinsOverFileNameAndDeclaredMime() =
        runTest {
            val gif = UploadMedia.fromBytes("no-extension", "GIF89a1234".encodeToByteArray(), "image/jpeg")
            assertEquals("image/gif", gif.mimeType)
            assertEquals("no-extension.gif", gif.name)
            val mov = UploadMedia.fromBytes("CLIP.MP4", byteArrayOf(0, 0, 0, 20) + "ftypqt  ".encodeToByteArray())
            assertEquals("video/quicktime", mov.mimeType)
            assertEquals("CLIP.mov", mov.name)
        }

    @Test
    fun videoAndGifBypassImageCompressionAndLimitsRejectBeforeReading() =
        runTest {
            val compressor =
                object : ImageCompressor {
                    override suspend fun compress(
                        imageBytes: ByteArray,
                        maxSize: Long,
                        maxDimensions: Pair<Int, Int>,
                    ): ByteArray = error("Original media must not be decoded or compressed")
                }
            for ((name, type) in listOf("clip.mp4" to "video/mp4", "animation.gif" to "image/gif")) {
                val media = UploadMedia.fromBytes(name, byteArrayOf(1, 2, 3), type)
                assertSame(media, media.compressImage(compressor, ComposeConfig.Media.Compression()))
                assertFailsWith<IllegalArgumentException> { media.validate("Test", maxBytes = 2) }
                assertFailsWith<IllegalArgumentException> { media.validate("Test", acceptedTypes = listOf("image/jpeg")) }
            }
        }

    @Test
    fun unknownLengthIsCountedWithoutRetainingContent() =
        runTest {
            val media = UploadMedia.fromSource("a.mp4", "video/mp4", null) { Buffer().write(ByteArray(200_003)) }
            assertEquals(200_003L, media.size)
        }
}
