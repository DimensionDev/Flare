package dev.dimension.flare.media

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class UgoiraCacheTest {
    private val media =
        UiMedia.Ugoira(
            MicroBlogKey("123", "pixiv.net"),
            MicroBlogKey("reader", "pixiv.net"),
            "cover",
            "https://i.pximg.net/img-original/date/123_ugoira0.png",
            null,
            100f,
            100f,
            false,
        )
    private val info =
        UgoiraMetadata(
            "https://i.pximg.net/img-zip-ugoira/date/123_ugoira600x600.zip",
            listOf(UgoiraFrame("000000.jpg", 40), UgoiraFrame("000001.jpg", 80)),
        )

    @Test
    fun deduplicatesOriginalDownloadsAndReusesCompleteCache(): Unit =
        runBlocking {
            val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
            val root = Files.createTempDirectory("ugoira-test").toString().toPath()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests += request.url.toString()
                        assertNull(request.headers["Authorization"])
                        assertEquals("https://www.pixiv.net/", request.headers["Referer"])
                        delay(10)
                        respond("frame")
                    },
                )
            try {
                val cache = UgoiraCache(root, client, { info }, FileSystem.SYSTEM)
                val a = async { cache.load(media) {} }
                val b = async { cache.load(media) {} }
                val first = a.await()
                assertSame(first, b.await())
                assertEquals(2, requests.size)
                assertEquals("original", first.quality)
                assertEquals(listOf(40, 80), first.frames.map { it.delayMillis })
                val reopened = UgoiraCache(root, client, { error("Should use cache") }, FileSystem.SYSTEM).load(media) {}
                assertEquals(first.frames, reopened.frames)
                assertEquals(2, requests.size)
                cache.release(first)
                cache.release(first)
            } finally {
                client.close()
                FileSystem.SYSTEM.deleteRecursively(root)
            }
        }

    @Test
    fun unavailableOriginalDiscardsEntireSequenceAndUsesLargeZipInMetadataOrder(): Unit =
        runBlocking {
            val zip =
                ByteArrayOutputStream()
                    .also { bytes ->
                        ZipOutputStream(bytes).use { output ->
                            for (index in listOf(1, 0)) {
                                output.putNextEntry(ZipEntry("00000$index.jpg"))
                                output.write("zip-$index".toByteArray())
                                output.closeEntry()
                            }
                        }
                    }.toByteArray()
            val root = Files.createTempDirectory("ugoira-test").toString().toPath()
            val requests = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests += request.url.toString()
                        when {
                            request.url.toString().endsWith("0.png") -> respond("original")
                            request.url.toString().endsWith("1.png") -> respond("", HttpStatusCode.NotFound)
                            request.url.toString().endsWith("1920x1080.zip") -> respond(zip)
                            else -> error("Unexpected fallback ${request.url}")
                        }
                    },
                )
            try {
                val cache = UgoiraCache(root, client, { info }, FileSystem.SYSTEM)
                val animation = cache.load(media) {}
                assertEquals("large", animation.quality)
                assertEquals(listOf("zip-0", "zip-1"), animation.frames.map { FileSystem.SYSTEM.read(it.file.toPath()) { readUtf8() } })
                assertEquals(3, requests.size)
                assertFalse(FileSystem.SYSTEM.list(root / animation.key).any { it.name.endsWith(".png") })
            } finally {
                client.close()
                FileSystem.SYSTEM.deleteRecursively(root)
            }
        }

    @Test
    fun invalidCacheStaysLeasedUntilReleaseAndCanThenBeDownloadedAgain(): Unit =
        runBlocking {
            val root = Files.createTempDirectory("ugoira-test").toString().toPath()
            val client = HttpClient(MockEngine { respond("frame") })
            try {
                val cache = UgoiraCache(root, client, { info }, FileSystem.SYSTEM)
                val animation = cache.load(media) {}
                cache.invalidate(animation)
                cache.clear()
                assertEquals(
                    "frame",
                    FileSystem.SYSTEM.read(
                        animation.frames
                            .first()
                            .file
                            .toPath(),
                    ) { readUtf8() },
                )
                assertFailsWith<IllegalStateException> { cache.load(media) {} }
                cache.release(animation)
                withTimeout(5_000) {
                    while (FileSystem.SYSTEM.exists(root / animation.key)) delay(10)
                }
                val retried = cache.load(media) {}
                assertEquals("original", retried.quality)
                cache.clear()
                cache.release(retried)
                withTimeout(5_000) {
                    while (FileSystem.SYSTEM.exists(root / retried.key)) delay(10)
                }
            } finally {
                client.close()
                FileSystem.SYSTEM.deleteRecursively(root)
            }
        }

    @Test
    fun rejectsUntrustedCdnBeforeSendingRequest(): Unit =
        runBlocking {
            val root = Files.createTempDirectory("ugoira-test").toString().toPath()
            val client = HttpClient(MockEngine { error("Must not send a request") })
            try {
                val cache = UgoiraCache(root, client, { info.copy(zipUrl = "https://example.org/private.zip") }, FileSystem.SYSTEM)
                assertFailsWith<IllegalArgumentException> { cache.load(media) {} }
            } finally {
                client.close()
                FileSystem.SYSTEM.deleteRecursively(root)
            }
        }
}
