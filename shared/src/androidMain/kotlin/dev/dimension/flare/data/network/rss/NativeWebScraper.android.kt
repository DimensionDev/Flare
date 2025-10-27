package dev.dimension.flare.data.network.rss

import android.content.Context
import android.webkit.WebView
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer

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
            }
            scope.launch {
                runCatching {
                    val html =
                        ktorClient()
                            .get(url) {
                                this.accept(ContentType.Text.Html)
                            }.bodyAsText()
                    withContext(Dispatchers.Main) {
                        loadDataWithBaseURL("", html, "text/html", "UTF-8", null)
                    }
                }.onFailure {
                    callback("error: network: ${it.message}")
                    destroy()
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
                            if (it == "\"null\"" || it.startsWith("error:")) {
                                callback("error: javascript evaluation failed")
                            } else {
                                callback(it.decodeJson(String.serializer()))
                            }
                            destroy()
                        }
                    }
                }
        }
    }
}
