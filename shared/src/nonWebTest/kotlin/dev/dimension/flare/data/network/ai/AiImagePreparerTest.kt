package dev.dimension.flare.data.network.ai

import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiImagePreparerTest : RobolectricTest() {
    @Test
    fun localFileExceedingReadBudgetIsRejectedWithoutReadingItsContents() =
        runTest {
            var reads = 0
            val media =
                UploadMedia.fromSource("large.jpg", "image/jpeg", 65L * 1024 * 1024) {
                    reads++
                    okio.Buffer()
                }
            reads = 0
            assertFailsWith<IllegalStateException> { AiImagePreparer(noCompression).prepare(media) }
            assertEquals(0, reads)
        }

    @Test
    fun downloadUsesMediaHeadersAndAccountProxyAndPreservesSmallImage() =
        runTest {
            val media =
                image.copy(
                    proxy = ProxyRef.forAccount(MicroBlogKey("user", "weibo.example")),
                    customHeaders = persistentMapOf("Referer" to "https://weibo.example/", "Cookie" to "media-cookie"),
                )
            val preparer =
                AiImagePreparer(noCompression) { ref ->
                    assertEquals(media.proxy, ref)
                    mockClient { request ->
                        assertEquals(media.url, request.url.toString())
                        assertEquals("https://weibo.example/", request.headers["Referer"])
                        assertEquals("media-cookie", request.headers["Cookie"])
                        assertEquals(null, request.headers["Authorization"])
                        respond(png)
                    }
                }
            val result = preparer.prepare(media, 1024)
            assertEquals("image/png", result.mimeType)
            assertContentEquals(png, result.bytes)
        }

    @Test
    fun oversizedDimensionsUseExistingCompressorAndRevalidateOutput() =
        runTest {
            var calls = 0
            val compressor =
                object : ImageCompressor {
                    override suspend fun compress(
                        imageBytes: ByteArray,
                        maxSize: Long,
                        maxDimensions: Pair<Int, Int>,
                    ): ByteArray {
                        calls++
                        assertContentEquals(widePng, imageBytes)
                        assertEquals(1024, maxSize.toInt())
                        assertEquals(4096 to 4096, maxDimensions)
                        return png
                    }
                }
            val result = AiImagePreparer(compressor) { mockClient { respond(widePng) } }.prepare(image, 1024)
            assertEquals(1, calls)
            assertContentEquals(png, result.bytes)
        }

    @Test
    fun compressorReturningOversizedOutputIsRejected() =
        runTest {
            val compressor =
                object : ImageCompressor {
                    override suspend fun compress(
                        imageBytes: ByteArray,
                        maxSize: Long,
                        maxDimensions: Pair<Int, Int>,
                    ): ByteArray = widePng
                }
            assertFailsWith<IllegalStateException> {
                AiImagePreparer(compressor) { mockClient { respond(widePng) } }.prepare(image, 1024)
            }
        }

    @Test
    fun failedDownloadAndHtmlDisguisedAsImageAreRejected() =
        runTest {
            for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.OK)) {
                assertFailsWith<Exception> {
                    AiImagePreparer(noCompression) {
                        mockClient { respond("<html>Access denied</html>", status, headersOf("Content-Type", "image/jpeg")) }
                    }.prepare(image, 1024)
                }
            }
        }

    @Test
    fun downloadSafetyLimitIsIndependentOfCompressionBudget() =
        runTest {
            assertFailsWith<IllegalStateException> {
                AiImagePreparer(noCompression) {
                    mockClient { respond(png, headers = headersOf("Content-Length", "67108865")) }
                }.prepare(image, 1024)
            }
            var calls = 0
            val padded = png + ByteArray(4096)
            val compressor =
                object : ImageCompressor {
                    override suspend fun compress(
                        imageBytes: ByteArray,
                        maxSize: Long,
                        maxDimensions: Pair<Int, Int>,
                    ): ByteArray {
                        calls++
                        return png
                    }
                }
            AiImagePreparer(compressor) { mockClient { respond(padded) } }.prepare(image, 1024)
            assertEquals(1, calls)
        }

    @Test
    fun downloadTimeoutAndUserCancellationRemainDistinct() =
        runTest {
            assertFailsWith<IllegalStateException> {
                AiImagePreparer(noCompression) {
                    mockClient {
                        delay(31_000)
                        respond(png)
                    }
                }.prepare(image, 1024)
            }
            assertFailsWith<CancellationException> {
                AiImagePreparer(
                    noCompression,
                ) { mockClient { throw CancellationException("cancelled") } }.prepare(image, 1024)
            }
        }

    @Test
    fun streamingDownloadWithoutContentLengthStillStopsAtSafetyLimit() =
        runTest {
            val channel = ByteChannel(autoFlush = true)
            val writer =
                backgroundScope.launch {
                    try {
                        val chunk = ByteArray(64 * 1024)
                        repeat(1025) { channel.writeFully(chunk) }
                    } finally {
                        channel.flushAndClose()
                    }
                }
            try {
                val failure =
                    assertFailsWith<IllegalStateException> {
                        AiImagePreparer(noCompression) { mockClient { respond(channel) } }.prepare(image, 1024)
                    }
                assertTrue(failure.message.orEmpty().contains("safety limit"))
            } finally {
                writer.cancel()
                channel.cancel(null)
            }
        }

    @Test
    fun nonHttpUrlsNeverStartDownload() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                AiImagePreparer(
                    noCompression,
                ) { error("Must not create HTTP client") }.prepare(image.copy(url = "file:///secret.png"), 1024)
            }
        }

    private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler(handler)
                },
            ),
        )

    private companion object {
        val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=")
        val widePng =
            Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAEAEAAAABCAYAAACx4wBCAAAAJ0lEQVR4nO3BMQEAAADCoPVPbQ0PoAAAAAAAAAAAAAAAAAAAAIALA0AFAAFdEb2kAAAAAElFTkSuQmCC",
            )
        val image = UiMedia.Image("https://media.example/image.jpg", "https://media.example/image.jpg", null, 1f, 1f, false)
        val noCompression =
            object : ImageCompressor {
                override suspend fun compress(
                    imageBytes: ByteArray,
                    maxSize: Long,
                    maxDimensions: Pair<Int, Int>,
                ): ByteArray = error("Image should not be compressed")
            }
    }
}
