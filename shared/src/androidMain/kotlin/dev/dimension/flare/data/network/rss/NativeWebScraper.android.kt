package dev.dimension.flare.data.network.rss

import android.content.Context
import android.webkit.WebView

internal actual class NativeWebScraper(
    private val context: Context,
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
            webViewClient =
                object : android.webkit.WebViewClient() {
                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        evaluateJavascript(scriptToInject) { callback(it) }
                    }
                }
            loadUrl(url)
        }
    }
}
