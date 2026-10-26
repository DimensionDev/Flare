package dev.dimension.flare.common

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowPartialImage
import coil3.size.Size
import coil3.toBitmap
import dev.dimension.flare.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ImageCacheRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val loaders = mutableListOf<ImageLoader>()
    private val url = "https://example.test/${UUID.randomUUID()}.png"
    private val customKey = "custom-$url"
    private val fullImage by lazy { imageBytes() }
    private val client = TestClient()

    @After
    fun tearDown() {
        loaders.forEach { it.shutdown() }
        loaders.firstOrNull()?.diskCache?.let {
            it.remove(url)
            it.remove(customKey)
        }
    }

    @Test
    fun partialDownloadIsRetriedAndOnlyCompletePixelsAreCached() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(fullImage.size / 2), fullImage)
            val loader = loader()

            assertComplete(loader.execute(request()))
            assertEquals(2, client.requests.size)
            assertEquals("no-cache", client.requests.last().headers["Cache-Control"])
            assertEquals(DataSource.MEMORY_CACHE, assertComplete(loader.execute(request())).dataSource)
            loader.memoryCache?.clear()
            assertEquals(DataSource.DISK, assertComplete(loader.execute(request())).dataSource)
            assertEquals(2, client.requests.size)
            assertCachedBytes(loader, url, fullImage)
        }

    @Test
    fun headerOnlyImageCannotBecomeAnAllBlackSuccess() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(100), fullImage)
            assertComplete(loader().execute(request()))
            assertEquals(2, client.requests.size)
        }

    @Test
    fun legacyPartialDiskEntryIsRepairedByANewLoader() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(fullImage.size / 2), fullImage)
            val oldLoader = loader(allowPartial = true)
            assertTrue(oldLoader.execute(request()) is SuccessResult)
            assertCachedBytes(oldLoader, url, client.responses.first())
            oldLoader.shutdown()

            val newLoader = loader()
            assertComplete(newLoader.execute(request()))
            assertEquals(2, client.requests.size)
            assertCachedBytes(newLoader, url, fullImage)
        }

    @Test
    fun repeatedCorruptionStopsAfterOneRetryAndRemovesTheBadEntry() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(fullImage.size / 2))
            val loader = loader()
            assertTrue(loader.execute(request()) is ErrorResult)
            assertEquals(2, client.requests.size)
            assertNull(loader.diskCache?.openSnapshot(url))
            assertEquals(0L, loader.memoryCache?.size)

            client.responses = listOf(fullImage)
            assertComplete(loader.execute(request()))
            assertEquals(3, client.requests.size)
        }

    @Test
    fun malformedImageIsAlsoEvicted() =
        runBlocking {
            client.responses = listOf("not an image".toByteArray(), fullImage)
            assertComplete(loader().execute(request()))
            assertEquals(2, client.requests.size)
        }

    @Test
    fun customCacheKeyAndRequestHeadersSurviveRecovery() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(100), fullImage)
            val loader = loader()
            val request = request().newBuilder().diskCacheKey(customKey).build()
            assertComplete(loader.execute(request))
            assertCachedBytes(loader, customKey, fullImage)
            assertNull(loader.diskCache?.openSnapshot(url))
            assertTrue(client.requests.all { it.headers["Authorization"] == "Bearer test-token" })
        }

    @Test
    fun simultaneousRequestsShareTheRepair() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(fullImage.size / 2), fullImage)
            client.responseDelay = 50
            val loader = loader()
            coroutineScope {
                List(8) { async { loader.execute(request()) } }.awaitAll().forEach(::assertComplete)
            }
            assertEquals(2, client.requests.size)
            assertCachedBytes(loader, url, fullImage)
        }

    @Test
    fun offlineRequestEvictsCorruptionWithoutGoingOnline() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(100))
            val oldLoader = loader(allowPartial = true)
            assertTrue(oldLoader.execute(request()) is SuccessResult)
            oldLoader.shutdown()
            val loader = loader()

            assertTrue(loader.execute(request().newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()) is ErrorResult)
            assertEquals(1, client.requests.size)
            assertNull(loader.diskCache?.openSnapshot(url))
        }

    @Test
    fun disabledCachesStayDisabledDuringRetry() =
        runBlocking {
            client.responses = listOf(fullImage.copyOf(100), fullImage)
            val loader = loader()
            val request =
                request()
                    .newBuilder()
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            assertComplete(loader.execute(request))
            assertEquals(2, client.requests.size)
            assertNull(loader.diskCache?.openSnapshot(url))
            assertEquals(0L, loader.memoryCache?.size)
        }

    @Test
    fun timeoutAndHttpErrorsAreNotRetried() =
        runBlocking {
            val loader = loader()
            client.failure = SocketTimeoutException("timeout")
            assertTrue(loader.execute(request()) is ErrorResult)
            assertEquals(1, client.requests.size)

            client.failure = null
            client.status = 403
            assertTrue(loader.execute(request()) is ErrorResult)
            assertEquals(2, client.requests.size)
        }

    @Test
    fun cancellationIsPropagatedWithoutRetry() {
        client.failure = CancellationException("cancelled")
        val loader = loader()
        assertThrows(CancellationException::class.java) {
            runBlocking { loader.execute(request()) }
        }
        assertEquals(1, client.requests.size)
    }

    @Test
    fun healthyImagesKeepNormalMemoryAndDiskCaching() =
        runBlocking {
            val loader = loader()
            assertEquals(DataSource.NETWORK, assertComplete(loader.execute(request())).dataSource)
            assertEquals(DataSource.MEMORY_CACHE, assertComplete(loader.execute(request())).dataSource)
            loader.memoryCache?.clear()
            assertEquals(DataSource.DISK, assertComplete(loader.execute(request())).dataSource)
            assertEquals(1, client.requests.size)
        }

    private fun loader(allowPartial: Boolean? = null): ImageLoader {
        val base = App().newImageLoader(context)
        loaders += base
        return base
            .newBuilder()
            .mainCoroutineContext(Dispatchers.Unconfined)
            .apply { if (allowPartial != null) allowPartialImage(allowPartial) }
            .build()
            .also { loaders += it }
    }

    private fun request() =
        ImageRequest
            .Builder(context)
            .data(url)
            .fetcherFactory(NetworkFetcher.Factory(networkClient = { client }))
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer test-token").build())
            .build()

    private fun assertComplete(result: ImageResult): SuccessResult {
        assertTrue("Expected a complete image, got ${(result as? ErrorResult)?.throwable}", result is SuccessResult)
        result as SuccessResult
        val bitmap = result.image.toBitmap()
        assertEquals(256, bitmap.height)
        assertEquals(Color.rgb(17, 91, 203), bitmap.getPixel(128, 255))
        return result
    }

    private fun assertCachedBytes(
        loader: ImageLoader,
        key: String,
        expected: ByteArray,
    ) {
        checkNotNull(loader.diskCache?.openSnapshot(key)).use {
            assertArrayEquals(expected, it.data.toFile().readBytes())
        }
    }

    private fun imageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val random = Random(4)
        val pixels = IntArray(256 * 256) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        pixels[255 * 256 + 128] = Color.rgb(17, 91, 203)
        bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 256)
        return ByteArrayOutputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            bitmap.recycle()
            it.toByteArray()
        }
    }

    private inner class TestClient : NetworkClient {
        var responses: List<ByteArray> = emptyList()
        var status = 200
        var failure: Exception? = null
        var responseDelay = 0L
        val requests = mutableListOf<NetworkRequest>()

        override suspend fun <T> executeRequest(
            request: NetworkRequest,
            block: suspend (NetworkResponse) -> T,
        ): T {
            val index =
                synchronized(requests) {
                    requests.add(request)
                    requests.lastIndex
                }
            failure?.let { throw it }
            delay(responseDelay)
            val bytes = if (responses.isEmpty()) fullImage else responses[index.coerceAtMost(responses.lastIndex)]
            val response = NetworkResponse(code = status, body = NetworkResponseBody(Buffer().write(bytes)))
            return response.body!!.use { block(response) }
        }
    }
}
