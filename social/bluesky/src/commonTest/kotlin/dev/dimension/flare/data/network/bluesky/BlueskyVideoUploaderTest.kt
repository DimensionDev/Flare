package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.common.UploadMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import sh.christian.ozone.api.model.Blob
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlueskyVideoUploaderTest {
    @Test
    fun uploadsOriginalBytesThenWaitsForBlob() =
        runTest {
            var requests = 0
            val bytes = byteArrayOf(0, 0, 0, 20) + "ftypisom".encodeToByteArray()
            val client =
                videoClient { request ->
                    requests++
                    if (requests == 1) {
                        assertEquals("Bearer token", request.headers["Authorization"])
                        assertEquals("did:plc:test", request.url.parameters["did"])
                        val content = assertIs<OutgoingContent.WriteChannelContent>(request.body)
                        assertEquals("video/mp4", content.contentType.toString())
                        assertEquals(bytes.size.toLong(), content.contentLength)
                        val uploaded =
                            coroutineScope {
                                val channel = ByteChannel(true)
                                launch {
                                    try {
                                        content.writeTo(channel)
                                    } finally {
                                        channel.flushAndClose()
                                    }
                                }
                                channel.readBuffer().readByteArray()
                            }
                        assertContentEquals(bytes, uploaded)
                        respond("""{"jobId":"job","state":"PROCESSING"}""")
                    } else {
                        assertEquals("job", request.url.parameters["jobId"])
                        respond("""{"jobStatus":{"jobId":"job","state":"JOB_STATE_COMPLETED","blob":$blobJson}}""")
                    }
                }
            try {
                val blob = BlueskyVideoUploader(client).upload(UploadMedia.fromBytes("clip", bytes), "did:plc:test", "token")
                assertEquals("video/mp4", assertIs<Blob.StandardBlob>(blob).mimeType)
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun alreadyProcessedBlobIsReusedEvenOnConflict() =
        runTest {
            val client =
                videoClient {
                    respond("""{"error":"already_exists","blob":$blobJson}""", HttpStatusCode.Conflict)
                }
            try {
                val blob = BlueskyVideoUploader(client).upload(UploadMedia.fromBytes("a.mp4", byteArrayOf(1)), "did:plc:test", "token")
                assertEquals("video/mp4", assertIs<Blob.StandardBlob>(blob).mimeType)
            } finally {
                client.close()
            }
        }

    @Test
    fun processingFailureAndUnsupportedFormatStopPublishing() =
        runTest {
            var requests = 0
            val client =
                videoClient {
                    requests++
                    respond("""{"jobId":"job","state":"JOB_STATE_FAILED","message":"Invalid video codec"}""")
                }
            try {
                val uploader = BlueskyVideoUploader(client)
                val error =
                    assertFailsWith<IllegalStateException> {
                        uploader.upload(UploadMedia.fromBytes("a.mp4", byteArrayOf(1)), "did:plc:test", "token")
                    }
                assertTrue(error.message.orEmpty().contains("Invalid video codec"))
                assertFailsWith<IllegalArgumentException> {
                    uploader.upload(UploadMedia.fromBytes("a.gif", "GIF89a".encodeToByteArray()), "did:plc:test", "token")
                }
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun quickTimeVideoIsRejectedBeforeUploadEvenWithMp4Name() =
        runTest {
            var requests = 0
            val client =
                videoClient {
                    requests++
                    respond("""{"jobId":"job","state":"JOB_STATE_COMPLETED","blob":$blobJson}""")
                }
            try {
                val media =
                    UploadMedia.fromBytes(
                        "clip.mp4",
                        byteArrayOf(0, 0, 0, 20) + "ftypqt  ".encodeToByteArray(),
                        "video/mp4",
                    )
                val error =
                    assertFailsWith<IllegalArgumentException> {
                        BlueskyVideoUploader(client).upload(media, "did:plc:test", "token")
                    }
                assertEquals("Bluesky does not support video/quicktime: clip.mov", error.message)
                assertEquals(0, requests)
            } finally {
                client.close()
            }
        }

    private fun TestScope.videoClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }
        }

    private val blobJson = """{"${'$'}type":"blob","ref":{"${'$'}link":"bafkreiaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"mimeType":"video/mp4","size":12}"""
}
