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
import okio.Buffer
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
            assertOriginalUpload(
                bytes = byteArrayOf(0, 0, 0, 20) + "ftypisom".encodeToByteArray(),
                expectedMimeType = "video/mp4",
                expectedName = "clip.mp4",
            )
        }

    @Test
    fun uploadsOriginalGifThenWaitsForProcessedMp4() =
        runTest {
            assertOriginalUpload(
                bytes = "GIF89a".encodeToByteArray() + ByteArray(150_000) { (it % 251).toByte() },
                expectedMimeType = "image/gif",
                expectedName = "clip.gif",
            )
        }

    @Test
    fun uploadsOriginalQuickTimeEvenWithMp4NameAndDeclaredMime() =
        runTest {
            assertOriginalUpload(
                bytes = byteArrayOf(0, 0, 0, 20) + "ftypqt  ".encodeToByteArray(),
                expectedMimeType = "video/quicktime",
                expectedName = "clip.mov",
            )
        }

    private suspend fun TestScope.assertOriginalUpload(
        bytes: ByteArray,
        expectedMimeType: String,
        expectedName: String,
    ) {
        var requests = 0
        val client =
            videoClient { request ->
                requests++
                if (requests == 1) {
                    assertEquals("Bearer token", request.headers["Authorization"])
                    assertEquals("did:plc:test", request.url.parameters["did"])
                    assertEquals(expectedName, request.url.parameters["name"])
                    val content = assertIs<OutgoingContent.WriteChannelContent>(request.body)
                    assertEquals(expectedMimeType, content.contentType.toString())
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
            val blob =
                BlueskyVideoUploader(
                    client,
                ).upload(UploadMedia.fromBytes("clip.mp4", bytes, "video/mp4"), "did:plc:test", "token")
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
                    uploader.upload(UploadMedia.fromBytes("a.png", byteArrayOf(1), "image/png"), "did:plc:test", "token")
                }
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun rejectsUnprocessedGifBlob() =
        runTest {
            var requests = 0
            val client =
                videoClient {
                    requests++
                    respond("""{"jobId":"job","state":"JOB_STATE_COMPLETED","blob":${blobJson.replace("video/mp4", "image/gif")}}""")
                }
            try {
                val media = UploadMedia.fromBytes("clip.mp4", byteArrayOf(1))
                val error =
                    assertFailsWith<IllegalStateException> {
                        BlueskyVideoUploader(client).upload(media, "did:plc:test", "token")
                    }
                assertEquals("Bluesky video processing did not return an MP4 blob", error.message)
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun oversizedGifAndQuickTimeAreRejectedBeforeReadingUploadBodies() =
        runTest {
            var requests = 0
            val client =
                videoClient {
                    requests++
                    respond("""{"blob":$blobJson}""")
                }
            try {
                for (mimeType in listOf("image/gif", "video/quicktime")) {
                    var readersOpened = 0
                    val media =
                        UploadMedia.fromSource("large", mimeType, 300_000_001) {
                            readersOpened++
                            Buffer().writeByte(1)
                        }
                    val error =
                        assertFailsWith<IllegalArgumentException> {
                            BlueskyVideoUploader(client).upload(media, "did:plc:test", "token")
                        }
                    assertTrue(error.message.orEmpty().contains("exceeds 300000000 bytes"))
                    assertEquals(1, readersOpened) // Only MIME detection opens the source.
                }
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
