package dev.dimension.flare.common

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class AndroidMediaShareTest {
    @Test
    fun errorEofDeletesTheIncompleteShareFile() =
        runBlocking {
            val body =
                object : ByteReadChannel by ByteReadChannel(byteArrayOf(1)) {
                    override val closedCause = IOException("Proxy configuration changed")
                }
            val target = File.createTempFile("share-cancelled-", ".png")
            try {
                val client =
                    HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentLength, "2")) }) {
                        // Expose the error EOF to the downloader instead of Ktor's copying coroutine.
                        useDefaultTransformers = false
                    }
                assertFalse(downloadToFile(media(), target, client))
                assertFalse("Incomplete media must not be shared", target.exists())
            } finally {
                target.delete()
            }
        }

    @Test
    fun normalEofKeepsTheCompleteShareFile() =
        runBlocking {
            val bytes = byteArrayOf(1, 2, 3)
            val target = File.createTempFile("share-complete-", ".png")
            try {
                val client = HttpClient(MockEngine { respond(bytes) }) { useDefaultTransformers = false }
                assertTrue(downloadToFile(media(), target, client))
                assertArrayEquals(bytes, target.readBytes())
            } finally {
                target.delete()
            }
        }

    private fun media() =
        UiMedia.Image(
            url = "https://example.social/media.png",
            previewUrl = "https://example.social/media.png",
            description = null,
            height = 0f,
            width = 0f,
            sensitive = false,
            proxy = ProxyRef.Global,
        )
}
