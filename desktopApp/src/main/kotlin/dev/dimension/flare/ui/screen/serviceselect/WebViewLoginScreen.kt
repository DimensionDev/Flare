package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginCookieValue
import dev.dimension.flare.ui.presenter.login.LoginEffect
import io.github.kdroidfilter.webview.web.WebContent
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(
    request: LoginEffect.OpenWebCookieLogin,
    callback: suspend (LoginCookieSnapshot) -> Boolean,
    onBack: () -> Unit,
) {
    val state =
        remember(request.url) {
            WebViewState(WebContent.Url("about:blank")).apply {
                webSettings.desktopWebSettings.incognito = true
            }
        }
    LaunchedEffect(state.webView, request.url) {
        val nativeWebView = state.webView?.nativeWebView ?: return@LaunchedEffect
        while (!nativeWebView.isReady()) {
            delay(50)
        }
        nativeWebView.clearAllCookies()
        nativeWebView.loadUrl(request.url)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2.seconds)
            val webView = state.webView?.nativeWebView ?: continue
            val rawHeader =
                listOfNotNull(state.lastLoadedUrl, request.url)
                    .distinct()
                    .flatMap { webView.getCookiesForUrl(it) }
                    .plus(webView.getCookies())
                    .distinctBy { listOf(it.domain, it.path, it.name) }
                    .joinToString("; ") { "${it.name}=${it.value}" }
                    .takeIf { it.isNotBlank() }
            val values =
                request.probes
                    .flatMap { probe ->
                        val expected = probe.names.toSet()
                        webView
                            .getCookiesForUrl(probe.url)
                            .filter { it.name in expected }
                            .map { cookie ->
                                LoginCookieValue(
                                    sourceUrl = probe.url,
                                    name = cookie.name,
                                    value = cookie.value,
                                )
                            }
                    }.distinctBy { it.sourceUrl to it.name }
            if (callback(LoginCookieSnapshot(values = values, rawHeader = rawHeader))) {
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
