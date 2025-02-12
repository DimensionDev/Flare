package dev.dimension.flare.data.network.rss

import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

internal actual class NativeWebScraper {
    actual fun parse(
        url: String,
        scriptToInject: String,
        callback: (String) -> Unit,
    ) {
        val webview = WKWebView()
        webview.navigationDelegate =
            object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(
                    webView: WKWebView,
                    didFinishNavigation: WKNavigation?,
                ) {
                    webView.evaluateJavaScript(scriptToInject) { result, error ->
                        callback.invoke(result?.toString() ?: "null")
                    }
                }
            }
        webview.loadRequest(NSURLRequest(NSURL.URLWithString(url)!!))
    }
}
