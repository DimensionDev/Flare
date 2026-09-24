package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.xqt.api.MediaApi
import dev.dimension.flare.data.network.xqt.api.TwitterUploadError
import dev.dimension.flare.data.network.xqt.api.TwitterUploadProcessInfo
import dev.dimension.flare.data.network.xqt.api.TwitterUploadResponse
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XQTMediaUploaderTest {
    @Test
    fun missingFinalizeIdCannotBeTreatedAsSuccess() =
        runTest {
            val api = FakeMediaApi().apply { finalId = null }
            assertFailsWith<IllegalStateException> {
                XQTMediaUploader(api).upload(UploadMedia.fromBytes("a.mp4", byteArrayOf(1)))
            }
        }

    @Test
    fun chunksPreserveBytesAndIndicesAcrossTheOldBatchBoundary() =
        runTest {
            for (size in listOf(512 * 1024 - 1, 512 * 1024, 512 * 1024 * 10 + 7)) {
                val bytes = ByteArray(size) { (it % 251).toByte() }
                val api = FakeMediaApi()
                val id = XQTMediaUploader(api).upload(UploadMedia.fromBytes("123", bytes, "video/mp4"))
                assertEquals("123", id)
                assertEquals("video/mp4", api.mime)
                assertEquals(size.toString(), api.size)
                assertEquals("tweet_video", api.category)
                assertContentEquals(bytes, api.received.readByteArray())
                assertEquals((api.indices.indices).toList(), api.indices)
                assertTrue(api.largestChunk <= 512 * 1024)
            }
        }

    @Test
    fun gifIsUploadedUnchangedWithGifCategory() =
        runTest {
            val bytes = "GIF89a original animated payload".encodeToByteArray()
            val api = FakeMediaApi()
            XQTMediaUploader(api).upload(UploadMedia.fromBytes("animation", bytes))
            assertEquals("image/gif", api.mime)
            assertEquals("tweet_gif", api.category)
            assertContentEquals(bytes, api.received.readByteArray())
        }

    @Test
    fun pendingWithoutDelayIsPolledAndFailedProcessingIsNotSuccess() =
        runTest {
            val api =
                FakeMediaApi().apply {
                    finalState = TwitterUploadProcessInfo(state = "pending")
                    statusState = TwitterUploadProcessInfo(state = "failed", error = TwitterUploadError(message = "Unsupported codec"))
                }
            val error =
                assertFailsWith<IllegalStateException> {
                    XQTMediaUploader(api).upload(UploadMedia.fromBytes("clip.MP4", byteArrayOf(1), "video/mp4"))
                }
            assertTrue(error.message.orEmpty().contains("Unsupported codec"))
            assertEquals(1, api.statusCalls)
        }

    private class FakeMediaApi : MediaApi {
        val received = Buffer()
        val indices = mutableListOf<Int>()
        var mime = ""
        var size = ""
        var category: String? = null
        var largestChunk = 0
        var statusCalls = 0
        var finalState: TwitterUploadProcessInfo? = null
        var finalId: String? = "123"
        var statusState = TwitterUploadProcessInfo(state = "succeeded")

        override suspend fun initUpload(
            mediaType: String,
            totalBytes: String,
            category: String?,
            referer: String,
        ): TwitterUploadResponse {
            mime = mediaType
            size = totalBytes
            this.category = category
            return TwitterUploadResponse(mediaIDString = "123")
        }

        override suspend fun appendUpload(
            mediaId: String,
            segmentIndex: String,
            mediaData: String,
            referer: String,
        ) {
            val bytes = Base64.decode(mediaData)
            largestChunk = maxOf(largestChunk, bytes.size)
            indices += segmentIndex.toInt()
            received.write(bytes)
        }

        override suspend fun finalizeUpload(
            mediaId: String,
            referer: String,
        ): TwitterUploadResponse = TwitterUploadResponse(mediaIDString = finalId, processingInfo = finalState)

        override suspend fun uploadStatus(
            mediaId: String,
            referer: String,
        ): TwitterUploadResponse {
            statusCalls++
            return TwitterUploadResponse(mediaIDString = mediaId, processingInfo = statusState)
        }
    }
}
