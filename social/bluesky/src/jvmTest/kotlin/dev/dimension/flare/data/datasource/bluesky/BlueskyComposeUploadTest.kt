package dev.dimension.flare.data.datasource.bluesky

import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlueskyComposeUploadTest {
    @Test
    fun gifUsesVideoUploadWithoutImageCompression() =
        runBlocking {
            assertComposeUpload("animation.gif", "GIF89a".encodeToByteArray(), FileType.Image)
        }

    @Test
    fun quickTimeUsesVideoUploadWithoutConversion() =
        runBlocking {
            assertComposeUpload("clip.mov", byteArrayOf(0, 0, 0, 20) + "ftypqt  ".encodeToByteArray(), FileType.Video)
        }

    @Test
    fun gifCannotBeMixedWithStillImages() =
        runBlocking {
            assertComposeUpload("animation.gif", "GIF89a".encodeToByteArray(), FileType.Image, includeStillImage = true)
        }

    private suspend fun assertComposeUpload(
        name: String,
        bytes: ByteArray,
        type: FileType,
        includeStillImage: Boolean = false,
    ) {
        val pds = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${pds.address.port}"
        val requests = Collections.synchronizedList(mutableListOf<String>())
        pds.createContext("/") { exchange ->
            requests += exchange.requestURI.path
            val (status, body) =
                if (exchange.requestURI.path == "/xrpc/com.atproto.server.getSession") {
                    200 to """{"did":"did:plc:alice","handle":"alice.bsky.social"}"""
                } else {
                    // Stop before the live video service. Reaching this request proves the compose route.
                    400 to """{"error":"InvalidRequest","message":"Video auth reached"}"""
                }
            val response = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        val file = Files.createTempFile("bluesky-compose-", ".bin").toFile()
        val stillImage = if (includeStillImage) Files.createTempFile("bluesky-image-", ".png").toFile() else null
        pds.start()
        try {
            file.writeBytes(bytes)
            stillImage?.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            val medias =
                buildList {
                    add(ComposeData.Media(FileItem(file, name = name, type = type), altText = "An animation"))
                    stillImage?.let { add(ComposeData.Media(FileItem(it), altText = "A still image")) }
                }
            val source =
                BlueskyDataSource(
                    accountKey = MicroBlogKey("did:plc:alice", "bsky.social"),
                    credentialFlow = flowOf(BlueskyCredential.Password(origin, "test-access", "test-refresh")),
                    updateCredential = {},
                )
            val error =
                withTimeout(10_000) {
                    assertFailsWith<IllegalArgumentException> {
                        source.compose(
                            ComposeData(
                                content = "Original animation",
                                medias = medias,
                            ),
                        ) {}
                    }
                }
            if (includeStillImage) {
                assertEquals("Bluesky supports one video or GIF per post, without other media", error.message)
                assertTrue(requests.isEmpty())
            } else {
                assertTrue(error.message.orEmpty().contains("Video auth reached"), "Unexpected compose failure: ${error.message}")
                assertEquals(
                    listOf("/xrpc/com.atproto.server.getSession", "/xrpc/com.atproto.server.getServiceAuth"),
                    requests.toList(),
                )
            }
        } finally {
            pds.stop(0)
            file.delete()
            stillImage?.delete()
        }
    }
}
