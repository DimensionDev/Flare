package dev.dimension.flare.ui.screen.serviceselect

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginCookieValue
import dev.dimension.flare.ui.presenter.login.LoginEffect
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val userAgent =
    mapOf(
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WebCookieLoginScreen(
    request: LoginEffect.OpenWebCookieLogin,
    callback: suspend (LoginCookieSnapshot) -> Boolean,
    onBack: () -> Unit,
) {
    val initialOrigin = remember(request.url) { request.url.httpsOriginOrNull() }
    val webViewState = rememberWebViewState("about:blank")
    val currentOrigin = webViewState.lastLoadedUrl?.httpsOriginOrNull() ?: initialOrigin.orEmpty()
    val client =
        remember {
            object : AccompanistWebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    request: WebResourceRequest?,
                ): Boolean = request?.isForMainFrame == true && !request.url.isHttps()

                @Deprecated("Deprecated in Android")
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    url: String?,
                ): Boolean = url?.httpsOriginOrNull() == null
            }
        }
    LaunchedEffect(request) {
        while (true) {
            webViewState.lastLoadedUrl?.let { loadedUrl ->
                val manager = CookieManager.getInstance()
                val rawHeader = manager.getCookie(loadedUrl)
                val values =
                    request.probes
                        .flatMap { probe ->
                            val expected = probe.names.toSet()
                            manager
                                .getCookie(probe.url)
                                .orEmpty()
                                .split(';')
                                .mapNotNull { item ->
                                    val name = item.substringBefore('=', missingDelimiterValue = "").trim()
                                    if (name !in expected) return@mapNotNull null
                                    LoginCookieValue(
                                        sourceUrl = probe.url,
                                        name = name,
                                        value = item.substringAfter('=', missingDelimiterValue = "").trim(),
                                    )
                                }
                        }.distinctBy { it.sourceUrl to it.name }
                if (callback(LoginCookieSnapshot(values = values, rawHeader = rawHeader))) {
                    onBack()
                    break
                }
            }
            delay(2.seconds)
        }
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = currentOrigin,
                        color =
                            if (currentOrigin.isNotEmpty() && currentOrigin != initialOrigin) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) {
        WebView(
            webViewState,
            layoutParams =
                FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            modifier =
                Modifier
                    .alpha(0.99f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(it)
                    .fillMaxSize(),
            onCreated = { webView ->
                WebStorage.getInstance().deleteAllData()
                webView.clearCache(true)
                with(webView.settings) {
                    userAgentString = userAgent.getValue("user-agent")
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    allowContentAccess = false
                    allowFileAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                CookieManager.getInstance().removeAllCookies {
                    webView.post { webView.loadUrl(request.url) }
                }
            },
            onDispose = ::clearLoginWebView,
            client = client,
        )
    }
}

private fun clearLoginWebView(webView: android.webkit.WebView) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearCache(true)
    WebStorage.getInstance().deleteAllData()
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    webView.destroy()
}

private fun String.httpsOriginOrNull(): String? =
    runCatching {
        val uri = Uri.parse(this)
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null,
        )
        "https://${uri.host}${if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"}"
    }.getOrNull()

private fun Uri.isHttps(): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        !host.isNullOrBlank() &&
        userInfo == null
