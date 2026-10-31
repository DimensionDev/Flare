package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.component.proxyImageFetcherFactory
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
class AgentMarkdownImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun markdownImageDoesNotConnectDirectlyWhenTheConfiguredProxyIsUnavailable() = checkImages(blocked = true)

    @Test
    fun headingsTablesAndMixedInlineImagesKeepTheirFormattingAndLoad() = checkImages(blocked = false)

    private fun checkImages(blocked: Boolean) {
        val requests = AtomicInteger()
        val paths = ConcurrentHashMap.newKeySet<String>()
        val results = ConcurrentHashMap<String, ImageResult>()
        val inlineResults = ConcurrentLinkedQueue<ImageResult>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val image =
            ByteArrayOutputStream()
                .also {
                    ImageIO.write(
                        BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB),
                        "png",
                        it,
                    )
                }.toByteArray()
        server.createContext("/fixture.png") { exchange ->
            requests.incrementAndGet()
            paths.add(exchange.requestURI.toString())
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { it.write(image) }
            exchange.close()
        }
        server.start()
        val previous = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val loader =
            ImageLoader
                .Builder(PlatformContext.INSTANCE)
                .components {
                    add(proxyImageFetcherFactory())
                    add(
                        object : Interceptor {
                            override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                                chain.proceed().also { result ->
                                    when (val data = chain.request.data) {
                                        is String -> results[data] = result
                                        is ByteArray -> inlineResults.add(result)
                                    }
                                }
                        },
                    )
                }.build()
        SingletonImageLoader.setUnsafe(loader)
        val repository =
            ProxyRepository(
                object : DataStore<ProxySettings> {
                    override val data = MutableStateFlow(ProxySettings())

                    override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                        transform(data.value).also {
                            data.value =
                                it
                        }
                },
            )
        startKoin { modules(module { single { repository } }) }
        try {
            runBlocking {
                if (blocked) {
                    repository.save(ProxyProfile("fixture", "Fixture", ProxyProtocol.HTTPS, "127.0.0.1", 1))
                    repository.selectGlobal(ProxyPolicy(ProxyMode.PROXY, "fixture"))
                    assertEquals(1, ProxyNetwork.snapshot(ProxyRef.Global).connection.port)
                } else {
                    repository.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                }
            }
            val messages = MutableStateFlow(emptyList<AgentChatHistoryMessage>())
            composeRule.setContent {
                val state by messages.collectPagingState()
                FlareTheme(isDarkTheme = false) {
                    Box(Modifier.size(300.dp, 700.dp)) { AgentChatMessageList(state, {}, {}, {}) }
                }
            }
            val embedded =
                "data:image/png;base64," +
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(image)
            val forms: List<(String) -> String> =
                listOf(
                    { "Before **bold** *italic* ~~strike~~ `code` [link](https://example.com) ![Fixture]($it) after" },
                    { "# Heading ![Fixture]($it)" },
                    { "| Image | Detail |\n| --- | --- |\n| ![Fixture]($it) | **after** |" },
                    { "![Inline]($embedded) ![Fixture]($it)" },
                )
            for ((index, form) in forms.withIndex()) {
                val path = "/fixture.png?case=$index"
                val url = "http://127.0.0.1:${server.address.port}$path"
                composeRule.runOnIdle {
                    messages.value =
                        listOf(
                            AgentChatHistoryMessage(
                                "fixture-$index",
                                AgentChatHistoryMessage.Role.Assistant,
                                persistentListOf(AgentMessagePart.Text(form(url))),
                                0,
                            ),
                        )
                }
                composeRule.waitUntil(5_000) { results[url] != null || (blocked && path in paths) }
                if (blocked) {
                    assertEquals("Markdown bypassed Flare's configured proxy", 0, requests.get())
                    assertTrue("The proxy-backed request must actually run and fail", results[url] is ErrorResult)
                } else {
                    assertTrue("Image did not load: ${results[url]}", results[url] is SuccessResult)
                    if (index == 0) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        composeRule
                            .onNodeWithText("Before bold", substring = true, useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val text = layouts.single().layoutInput.text
                        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
                        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
                        assertTrue(text.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
                        assertTrue(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
                        assertTrue(
                            text.getLinkAnnotations(0, text.length).any {
                                (it.item as? LinkAnnotation.Url)?.url ==
                                    "https://example.com"
                            },
                        )
                    }
                }
            }
            if (!blocked) {
                composeRule.waitUntil(5_000) { inlineResults.any { it is SuccessResult } }
                assertEquals(forms.size, requests.get())
            }
        } finally {
            runBlocking { repository.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM)) }
            SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
            stopKoin()
            server.stop(0)
        }
    }
}
