package dev.dimension.flare.common

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.component.ComposeInAppNotification
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

class DesktopDownloadManagerTest {
    @Test
    fun errorEofPreservesExistingFileAndRemovesPartialDownload() =
        runBlocking {
            val original = byteArrayOf(9, 8, 7)
            val target = File.createTempFile("desktop-cancelled-", ".png").apply { writeBytes(original) }
            val body =
                object : ByteReadChannel by ByteReadChannel(byteArrayOf(1)) {
                    override val closedCause = IOException("Proxy configuration changed")
                }
            val client =
                HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentLength, "2")) }) {
                    // Expose the error EOF to the downloader instead of Ktor's copying coroutine.
                    useDefaultTransformers = false
                }
            try {
                DesktopDownloadManager(ComposeInAppNotification(), client).use { manager ->
                    assertFalse(manager.download(media(), target, notify = false))
                    assertArrayEquals(original, target.readBytes())
                    assertFalse(File(target.path + ".part").exists())
                }
            } finally {
                target.delete()
                client.engine.close()
            }
        }

    @Test
    fun normalEofReplacesTheTargetWithCompleteBytes() =
        runBlocking {
            val bytes = byteArrayOf(1, 2, 3)
            val target = File.createTempFile("desktop-complete-", ".png").apply { writeText("old") }
            val client = HttpClient(MockEngine { respond(bytes) }) { useDefaultTransformers = false }
            try {
                DesktopDownloadManager(ComposeInAppNotification(), client).use { manager ->
                    assertTrue(manager.download(media(), target, notify = false))
                    assertArrayEquals(bytes, target.readBytes())
                    assertFalse(File(target.path + ".part").exists())
                }
            } finally {
                target.delete()
                client.engine.close()
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
