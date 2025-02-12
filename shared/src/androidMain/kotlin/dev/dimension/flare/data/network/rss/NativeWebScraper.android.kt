package dev.dimension.flare.data.network.rss

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
            var completed = false
            var data = ""
            val job =
                scope.launch {
                    while (!completed) {
                        if (data.isNotEmpty()) {
                            delay(1.seconds)
                        } else {
                            delay(500.milliseconds)
                        }
                        withContext(Dispatchers.Main) {
                            evaluateJavascript(scriptToInject) {
                                if (it != "null") {
                                    callback(it)
                                    if (it != data) {
                                        data = it
                                    } else {
                                        completed = true
                                    }
                                }
                            }
                        }
                    }
                }
            webViewClient =
                object : android.webkit.WebViewClient() {
                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        evaluateJavascript(scriptToInject) { callback(it) }
                        completed = true
                        job.cancel()
                    }
                }
            loadUrl(url)
        }
    }
}
