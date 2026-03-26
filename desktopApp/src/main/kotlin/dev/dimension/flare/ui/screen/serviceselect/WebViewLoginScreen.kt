package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewState
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(
    url: String,
    callback: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val state = rememberWebViewState(url)
    LaunchedEffect(Unit) {
        state.cookieManager.removeAllCookies()
        val urlData = Url(url)
        val actualUrl =
            urlData.protocol.name
                .plus("://")
                .plus(urlData.host.removePrefix("m."))
                .plus("/")
        while (true) {
            delay(2.seconds)
            val cookies = state.webView?.nativeWebView?.getCookiesForUrl(actualUrl) ?: continue
            if (callback.invoke(cookies.joinToString("; ") { "${it.name}=${it.value}" })) {
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
