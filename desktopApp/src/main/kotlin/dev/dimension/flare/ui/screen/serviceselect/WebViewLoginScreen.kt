package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(
    url: String,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val state =
        rememberWebViewState(url) {
            desktopWebSettings.incognito = true
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
