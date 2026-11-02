package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.dm.DMItem
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock

@OptIn(coil3.annotation.DelicateCoilApi::class)
class MediaNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val headers = persistentMapOf("Referer" to "https://feed.example/", "User-Agent" to "Feed reader")
    private val url = "https://cdn.example/picture.jpg"
    private val requests = CopyOnWriteArrayList<ImageRequest>()
    private var opened: String? = null
    private val repository =
        ProxyRepository(
            object : DataStore<ProxySettings> {
                override val data = MutableStateFlow(ProxySettings())

                override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                    transform(data.value).also { data.value = it }
            },
        )

    @Before
    fun setUp() {
        startKoin { modules(module { single<ProxyRepository> { repository } }) }
        runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT)) }
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            requests.add(chain.request)
                            ErrorResult(null, chain.request, IOException("Test image"))
                        },
                    )
                }.build()
        }
    }

    @After
    fun tearDown() {
        runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM)) }
        SingletonImageLoader.reset()
        stopKoin()
    }

    @Test
    fun dmImageClickPreservesAccountAndHeaders() {
        val account = MicroBlogKey("alice", "example.social")
        val media = UiMedia.Image(url, url, "DM picture", 100f, 100f, false, headers, ProxyRef.Account(account))
        val item =
            UiDMItem(
                account,
                createSampleUser(),
                UiDMItem.Message.Media(media),
                Clock.System.now().toUi(),
                false,
                UiDMItem.SendState.Sending,
                false,
            )
        content { DMItem(item, onRetry = {}, onUserClicked = {}) }
        composeRule.onNodeWithContentDescription("DM picture").performClick()
        assertEquals(DeeplinkRoute.Media.Image(url, null, headers, media.proxy), DeeplinkRoute.parse(requireNotNull(opened)))
    }

    @Test
    fun playbackSetupFailureIsDisplayed() {
        val media = UiMedia.Video(url, "", null, 100f, 100f, proxy = ProxyRef.Unbound)
        content { VideoPlayer(media, contentDescription = null) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Video failed to load").assertIsDisplayed()
        composeRule.onNodeWithText("Media is missing its account proxy scope").assertDoesNotExist()
    }

    @Test
    fun customPlaybackErrorReplacesTheDefaultWithoutBlockingClicks() {
        var clicks = 0
        val media = UiMedia.Video(url, "", null, 100f, 100f, proxy = ProxyRef.Unbound)
        content {
            VideoPlayer(media, contentDescription = null, onClick = { clicks++ }, errorContent = {
                BasicText("Custom playback error")
            })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom playback error").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Video failed to load").assertDoesNotExist()
        composeRule.onNodeWithText("Media is missing its account proxy scope").assertDoesNotExist()
        assertEquals(1, clicks)
    }

    @Test
    fun rssInlineImagePreservesHeaders() {
        rss()
        composeRule.waitUntil(5_000) { requests.any { it.data == url } }
        val request = requests.last { it.data == url }
        headers.forEach { (name, value) -> assertEquals(value, request.httpHeaders[name]) }
    }

    @Test
    fun rssImageClickPreservesHeadersAndGlobalProxy() {
        rss()
        composeRule.onNodeWithContentDescription("RSS picture").performClick()
        assertEquals(DeeplinkRoute.Media.Image(url, null, headers, ProxyRef.Global), DeeplinkRoute.parse(requireNotNull(opened)))
    }

    private fun rss() =
        content {
            RssRichText(Ksoup.parse("<img src='$url' alt='RSS picture'>").body(), imageHeader = headers)
        }

    private fun content(block: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalTimelineAppearance provides TimelineAppearance(),
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened = uri
                        }
                    },
            ) {
                FlareTheme(isDarkTheme = false) { Box(Modifier.size(400.dp), content = { block() }) }
            }
        }
    }
}
