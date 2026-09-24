package dev.dimension.flare.data.network

import dev.dimension.flare.common.UploadMedia
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.url
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadMediaContentTest {
    @Test
    fun rawBodyAndMultipartCanBothBeReplayed() =
        runTest {
            val bytes = "GIF89a\u0000\u0001\u0002two frames".encodeToByteArray()
            val media = UploadMedia.fromBytes("a.gif", bytes)
            val content = media.asContent()
            assertEquals(bytes.size.toLong(), content.contentLength)
            repeat(2) { assertContentEquals(bytes, content.readBody()) }
            coroutineScope {
                val multipart =
                    MultiPartFormDataContent(
                        formData {
                            appendMedia("file", media, this@coroutineScope)
                            append("description", "animated")
                        },
                        boundary = "test-boundary",
                    )
                val first = multipart.readBody()
                assertContentEquals(first, multipart.readBody())
                assertEquals(first.size.toLong(), multipart.contentLength)
                val body = first.decodeToString()
                assertTrue(body.contains("Content-Type: image/gif", ignoreCase = true))
                assertTrue(body.contains("filename=\"a.gif\""))
                assertTrue(body.contains(bytes.decodeToString()))
            }
        }

    @Test
    fun bodyLoggingExcludesAllUploadPaths() {
        for (path in listOf(
            "i/media/upload.json",
            "xrpc/com.atproto.repo.uploadBlob",
            "xrpc/app.bsky.video.uploadVideo",
            "api/v2/media",
            "api/drive/files/create",
            "upload",
            "api/statuses/uploadPic",
        )) {
            assertTrue(HttpRequestBuilder().apply { url("https://example.com/$path") }.isMediaUpload(), path)
        }
        assertFalse(HttpRequestBuilder().apply { url("https://example.com/api/v1/timelines/home") }.isMediaUpload())
    }

    private suspend fun OutgoingContent.WriteChannelContent.readBody(): ByteArray =
        coroutineScope {
            val channel = ByteChannel(autoFlush = true)
            launch {
                try {
                    writeTo(channel)
                } finally {
                    channel.flushAndClose()
                }
            }
            channel.readBuffer().readByteArray()
        }
}
