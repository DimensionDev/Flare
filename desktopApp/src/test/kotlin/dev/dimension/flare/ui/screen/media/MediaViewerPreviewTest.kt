package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalNetworkImageAllowHardware
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(coil3.annotation.DelicateCoilApi::class)
class MediaViewerPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun viewerKeepsTheWarmPreviewWhileOriginalIsPending() = checkPreview(expectedPreview = true)

    @Test
    fun viewerDoesNotReuseAnotherAccountsPreview() =
        checkPreview(expectedPreview = false, viewerMedia = { it.copy(proxy = ProxyRef.Account(MicroBlogKey("bob", "example.social"))) })

    @Test
    fun viewerDoesNotReuseAPreviewFromThePreviousRoute() =
        checkPreview(expectedPreview = false, beforeOpen = { it.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM)) })

    @Test
    fun viewerDoesNotReuseAPreviewWithDifferentHeaders() =
        checkPreview(expectedPreview = false, viewerMedia = {
            it.copy(
                customHeaders =
                    persistentMapOf("Authorization" to "Bearer other-fixture"),
            )
        })

    private fun checkPreview(
        expectedPreview: Boolean,
        viewerMedia: (UiMedia.Image) -> UiMedia.Image = { it },
        beforeOpen: suspend (ProxyRepository) -> Unit = {},
    ) {
        val store =
            object : DataStore<ProxySettings> {
                override val data = MutableStateFlow(ProxySettings())

                override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                    transform(data.value).also { data.value = it }
            }
        val repository = ProxyRepository(store)
        startKoin { modules(module { single<ProxyRepository> { repository } }) }
        runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT)) }
        val id = UUID.randomUUID()
        val media =
            UiMedia.Image(
                "https://media.invalid/$id/original.png",
                "https://media.invalid/$id/preview.png",
                "Test image",
                64f,
                64f,
                false,
                customHeaders = persistentMapOf("Authorization" to "Bearer preview-fixture"),
                proxy = ProxyRef.Account(MicroBlogKey("alice", "example.social")),
            )
        val key = runBlocking { ProxyNetwork.snapshot(media.proxy).key } + "|" + media.preview!!.cacheKey
        val originalStarted = AtomicBoolean()
        val loader = AtomicReference<ImageLoader>()
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Fetcher.Factory<Uri> { data, _, _ ->
                            when (data.toString()) {
                                media.previewUrl -> {
                                    Fetcher {
                                        ImageFetchResult(ColorImage(Color.Red.toArgb(), 64, 64, 16384L, true), false, DataSource.NETWORK)
                                    }
                                }

                                media.url -> {
                                    Fetcher {
                                        originalStarted.set(true)
                                        awaitCancellation()
                                    }
                                }

                                else -> {
                                    null
                                }
                            }
                        },
                    )
                }.build()
                .also { loader.set(it) }
        }
        var original by mutableStateOf(false)
        try {
            composeRule.setContent {
                FlareTheme(isDarkTheme = false) {
                    Box(Modifier.size(100.dp).background(Color.Black).testTag("viewer")) {
                        if (original) {
                            ImageItem(viewerMedia(media), "Test image", {}, false, Modifier.fillMaxSize())
                        } else {
                            CompositionLocalProvider(LocalNetworkImageAllowHardware provides false) {
                                NetworkImage(media.preview, "preview", Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                loader
                    .get()
                    ?.memoryCache
                    ?.keys
                    ?.any { it.key == key } == true
            }

            fun redPixel(): Boolean {
                val pixels = composeRule.onNodeWithTag("viewer").captureToImage().toPixelMap()
                val color = pixels[5, 5]
                return color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            assertTrue("The real preview request should paint red before navigation", redPixel())
            composeRule.runOnIdle {
                runBlocking { beforeOpen(repository) }
                original = true
            }
            composeRule.waitUntil(5_000) { originalStarted.get() }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            assertEquals("Only a preview with the same account, route and headers may remain visible", expectedPreview, redPixel())
        } finally {
            loader.get()?.shutdown()
            SingletonImageLoader.reset()
            runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM)) }
            stopKoin()
        }
    }
}
