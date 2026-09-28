package dev.dimension.flare.data.repository

import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.UgoiraDownloader
import dev.dimension.flare.media.UgoiraFrame
import dev.dimension.flare.media.UgoiraMetadata
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UgoiraRepositoryTest {
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
        runTest {
            val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
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
                val cache =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) { info }
                val a = async { cache.load(media) {} }
                val b = async { cache.load(media) {} }
                val first = a.await()
                assertSame(first, b.await())
                assertEquals(2, requests.size)
                assertEquals("original", first.quality)
                assertEquals(listOf(40, 80), first.frames.map { it.delayMillis })
                val reopened =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) {
                        error("Should use cache")
                    }.load(media) {}
                assertEquals(first.frames, reopened.frames)
                assertEquals(2, requests.size)
                cache.release(first)
                cache.release(first)
            } finally {
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun unavailableOriginalDiscardsEntireSequenceAndUsesLargeZipInMetadataOrder(): Unit =
        runTest {
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
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            val root = directory / "ugoira_cache"
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
                val cache =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) { info }
                val animation = cache.load(media) {}
                assertEquals("large", animation.quality)
                assertEquals(listOf("zip-0", "zip-1"), animation.frames.map { FileSystem.SYSTEM.read(it.file.toPath()) { readUtf8() } })
                assertEquals(3, requests.size)
                assertFalse(FileSystem.SYSTEM.list(root / animation.key).any { it.name.endsWith(".png") })
            } finally {
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun invalidCacheStaysLeasedUntilReleaseAndCanThenBeDownloadedAgain(): Unit =
        runTest {
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            val root = directory / "ugoira_cache"
            val client = HttpClient(MockEngine { respond("frame") })
            try {
                val cache =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) { info }
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
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun rejectsUntrustedCdnBeforeSendingRequest(): Unit =
        runTest {
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            val client = HttpClient(MockEngine { error("Must not send a request") })
            try {
                val cache =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) {
                        info.copy(zipUrl = "https://example.org/private.zip")
                    }
                assertFailsWith<IllegalArgumentException> { cache.load(media) {} }
            } finally {
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun retriesTransientResponsesAndBrokenBodiesWithoutReducingQuality(): Unit =
        runTest {
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            var requests = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertTrue(request.url.toString().endsWith(".png"))
                        when (++requests) {
                            1 -> respond("", HttpStatusCode.ServiceUnavailable)
                            2 -> respond(ByteChannel().apply { cancel(IOException("Broken response body")) })
                            else -> respond("complete-frame")
                        }
                    },
                )
            try {
                val repository =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) { info }
                val animation = repository.load(media) {}
                assertEquals("original", animation.quality)
                assertEquals(4, requests)
                animation.frames.forEach { assertEquals("complete-frame", FileSystem.SYSTEM.read(it.file.toPath()) { readUtf8() }) }
                repository.release(animation)
            } finally {
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun failedLoadDoesNotCancelTheInjectedApplicationScope(): Unit =
        runTest {
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            val applicationJob = Job()
            val applicationScope = CoroutineScope(coroutineContext + applicationJob)
            val client = HttpClient(MockEngine { respond("frame") })
            var fail = true
            try {
                val repository =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        applicationScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) {
                        check(!fail) { "Metadata temporarily unavailable" }
                        info
                    }
                assertFailsWith<IllegalStateException> { repository.load(media) {} }
                runCurrent()
                assertTrue(applicationScope.isActive)
                fail = false
                val animation = repository.load(media) {}
                assertEquals("original", animation.quality)
                repository.release(animation)
            } finally {
                applicationJob.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }

    @Test
    fun onlyTheLastConsumerCancelsTheTransferAndRemovesStagingFiles(): Unit =
        runTest {
            val directory = Files.createTempDirectory("ugoira-test").toString().toPath()
            val started = CompletableDeferred<Unit>()
            val canceled = CompletableDeferred<Unit>()
            val client = HttpClient(MockEngine { error("Metadata has not completed") })
            try {
                val repository =
                    UgoiraRepository(
                        OkioFileStorage(FileSystem.SYSTEM, directory),
                        backgroundScope,
                        UgoiraDownloader(FileSystem.SYSTEM, client),
                    ) {
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            canceled.complete(Unit)
                        }
                    }
                val first = async { repository.load(media) {} }
                val second = async { repository.load(media) {} }
                started.await()
                runCurrent()
                first.cancelAndJoin()
                runCurrent()
                assertFalse(canceled.isCompleted)
                second.cancelAndJoin()
                runCurrent()
                assertTrue(canceled.isCompleted)
                assertTrue(FileSystem.SYSTEM.list(directory / "ugoira_cache").isEmpty())
            } finally {
                backgroundScope.coroutineContext[Job]?.cancelAndJoin()
                client.close()
                FileSystem.SYSTEM.deleteRecursively(directory)
            }
        }
}
