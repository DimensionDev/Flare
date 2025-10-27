package dev.dimension.flare.data.network.rss

import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKProcessPool
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

internal actual class NativeWebScraper(
    private val scope: CoroutineScope,
) {
    private val processPool = WKProcessPool()

    private val tasks = mutableSetOf<Task>()

    @OptIn(ExperimentalForeignApi::class)
    actual fun parse(
        url: String,
        scriptToInject: String,
        callback: (String) -> Unit,
    ) {
        val task = Task(callback)
        tasks += task

        scope.launch {
            val html =
                try {
                    ktorClient()
                        .get(url) {
                            this.accept(ContentType.Text.Html)
                        }.bodyAsText()
                } catch (t: Throwable) {
                    task.finish("error: network: ${t.message}")
                    tasks -= task
                    return@launch
                }

            withContext(Dispatchers.Main) {
                val config =
                    WKWebViewConfiguration().apply {
                        setProcessPool(processPool)
                    }

                val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
                task.webView = webView

                val delegate =
                    object : NSObject(), WKNavigationDelegateProtocol {
                        override fun webView(
                            webView: WKWebView,
                            didFinishNavigation: WKNavigation?,
                        ) {
                            webView.evaluateJavaScript(scriptToInject) { result, error ->
                                val out =
                                    when {
                                        error != null -> "error: js: ${error.localizedDescription}"
                                        result == null -> "null"
                                        else -> result.toString()
                                    }
                                task.finish(out)
                                cleanup()
                            }
                        }

                        override fun webView(
                            webView: WKWebView,
                            didFailProvisionalNavigation: WKNavigation?,
                            withError: NSError,
                        ) {
                            task.finish("error: provisional: ${withError.localizedDescription}")
                            cleanup()
                        }

                        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
                            task.finish("error: terminated")
                            cleanup()
                        }

                        private fun cleanup() {
                            webView.setNavigationDelegate(null)
                            tasks -= task
                        }
                    }

                task.delegate = delegate
                webView.setNavigationDelegate(delegate)
                webView.loadHTMLString(html, baseURL = null)
                scope.launch {
                    delay(15_000)
                    if (tasks.contains(task)) {
                        task.finish("error: timeout")
                        withContext(Dispatchers.Main) {
                            webView.setNavigationDelegate(null)
                        }
                        tasks -= task
                    }
                }
            }
        }
    }

    private class Task(
        val cb: (String) -> Unit,
    ) {
        var webView: WKWebView? = null
        var delegate: WKNavigationDelegateProtocol? = null

        fun finish(s: String) = cb(s)
    }
}
