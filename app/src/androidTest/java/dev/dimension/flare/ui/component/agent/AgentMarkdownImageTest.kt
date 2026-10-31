package dev.dimension.flare.ui.component.agent

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import dev.dimension.flare.App
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.component.media
import dev.dimension.flare.ui.component.proxyImageFetcherFactory
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
class AgentMarkdownImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun markdownImageCanLoadThroughTheAppsImageLoader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = SingletonImageLoader.get(context)
        val results = ConcurrentHashMap<String, ImageResult>()
        val loader =
            (context.applicationContext as App)
                .newImageLoader(context)
                .newBuilder()
                .components {
                    add(proxyImageFetcherFactory())
                    add(
                        object : Interceptor {
                            override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                                chain.proceed().also { if (chain.request.data is String) results[chain.request.data.toString()] = it }
                        },
                    )
                }.build()
        SingletonImageLoader.setUnsafe(loader)
        val socket = ServerSocket(0)
        val hits = AtomicInteger()
        val bytes =
            ByteArrayOutputStream()
                .also { output ->
                    Bitmap
                        .createBitmap(
                            64,
                            64,
                            Bitmap.Config.ARGB_8888,
                        ).apply { eraseColor(Color.RED) }
                        .compress(Bitmap.CompressFormat.PNG, 100, output)
                }.toByteArray()
        val serving =
            Thread {
                try {
                    while (!socket.isClosed) {
                        socket.accept().use { client ->
                            val reader = client.getInputStream().bufferedReader()
                            while (reader.readLine()?.isNotEmpty() == true) { }
                            hits.incrementAndGet()
                            client.getOutputStream().apply {
                                write(
                                    "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                        .toByteArray(),
                                )
                                write(bytes)
                                flush()
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }
        try {
            val base = "http://127.0.0.1:${socket.localPort}"
            val control =
                runBlocking {
                    val media = UiMedia.Image("$base/control.png", "$base/control.png", null, 64f, 64f, false, proxy = ProxyRef.Global)
                    loader.execute(
                        ImageRequest
                            .Builder(context)
                            .media(media, ProxyNetwork.snapshot(media.proxy))
                            .size(64)
                            .build(),
                    )
                }
            assertTrue("Explicitly scoped image should load", control is SuccessResult)
            results.clear()
            val markdown = mutableStateOf("")
            composeRule.setContent {
                MaterialTheme {
                    Box(Modifier.size(300.dp, 400.dp)) {
                        AgentChatMessageBubble(listOf(AgentMessagePart.Text(markdown.value)), isUser = false)
                    }
                }
            }
            val embedded = "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val forms: List<(String) -> String> =
                listOf(
                    { "Before **bold ![Fixture]($it)** after" },
                    { "# Heading ![Fixture]($it)" },
                    { "| Image | Detail |\n| --- | --- |\n| ![Fixture]($it) | **after** |" },
                    { "![Inline]($embedded) ![Fixture]($it)" },
                )
            for (form in forms) {
                val url = "$base/${UUID.randomUUID()}.png"
                composeRule.runOnIdle { markdown.value = form(url) }
                composeRule.waitUntil(10_000) { results[url] != null }
                val result = results[url]
                val reason =
                    (result as? ErrorResult)?.throwable?.let {
                        generateSequence(
                            it,
                        ) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
                    }
                assertTrue("Markdown image failed before downloading: $reason", result is SuccessResult)
            }
        } finally {
            SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
            socket.close()
            serving.join(1000)
        }
    }
}
