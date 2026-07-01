package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.presenter.login.WebCookieSeed
import io.github.kdroidfilter.webview.web.WebContent
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewState
import io.github.kdroidfilter.webview.wry.WebViewCookie
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(
    url: String,
    initialCookies: List<WebCookieSeed>,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val state =
        remember(url) {
            WebViewState(WebContent.Url("about:blank")).apply {
                webSettings.desktopWebSettings.incognito = true
            }
        }
    LaunchedEffect(state.webView, url, initialCookies) {
        val nativeWebView = state.webView?.nativeWebView ?: return@LaunchedEffect
        while (!nativeWebView.isReady()) {
            delay(50)
        }
        nativeWebView.clearAllCookies()
        initialCookies.forEach { seed ->
            nativeWebView.setCookie(seed.toDesktopCookie())
        }
        nativeWebView.loadUrl(url)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2.seconds)
            val webView = state.webView?.nativeWebView ?: continue
            val cookies =
                listOfNotNull(state.lastLoadedUrl, url)
                    .distinct()
                    .flatMap { webView.getCookiesForUrl(it) }
                    .plus(webView.getCookies())
                    .distinctBy { listOf(it.domain, it.path, it.name) }
                    .joinToString("; ") { "${it.name}=${it.value}" }
                    .takeIf { it.isNotBlank() }
            if (callback.invoke(cookies)) {
                onBack.invoke()
                break
            }
        }
    }
    WebView(
        state,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun WebCookieSeed.toDesktopCookie(): WebViewCookie =
    WebViewCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresDateMs = null,
        isSessionOnly = true,
        maxAgeSec = null,
        sameSite = null,
        isSecure = secure,
        isHttpOnly = null,
    )
