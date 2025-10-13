package dev.dimension.flare.data.network.rss

import android.content.Context
import android.webkit.WebView
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual class NativeWebScraper(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    actual fun parse(
        url: String,
        scriptToInject: String,
        callback: (String) -> Unit,
    ) {
        WebView(context).apply {
            with(settings) {
                javaScriptEnabled = true
                userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
            }
            scope.launch {
                val html = ktorClient().get(url).bodyAsText()
                withContext(Dispatchers.Main) {
                    loadDataWithBaseURL("", html, "text/html", "UTF-8", null)
                }
            }
            webViewClient =
                object : android.webkit.WebViewClient() {
                    private var finished = false

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        evaluateJavascript(scriptToInject) {
                            if (finished) return@evaluateJavascript
                            finished = true
                            callback(it)
                            destroy()
                        }
                    }
                }
        }
    }
}
