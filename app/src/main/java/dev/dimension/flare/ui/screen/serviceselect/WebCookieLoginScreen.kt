package dev.dimension.flare.ui.screen.serviceselect

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.R
import dev.dimension.flare.common.WebViewProxy
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.network.ProxySnapshot
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.ErrorContent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginCookieValue
import dev.dimension.flare.ui.presenter.login.LoginEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import android.webkit.WebView as AndroidWebView

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
    var currentOrigin by remember(request) { mutableStateOf(initialOrigin.orEmpty()) }
    var route by remember { mutableStateOf<ProxySnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableStateOf(0) }
    var cookiesCleared by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        route = null
        errorMessage = null
        ProxyNetwork.changes.collectLatest {
            if (route?.isValid == true) return@collectLatest
            route = null
            errorMessage = null
            try {
                if (!cookiesCleared) {
                    WebStorage.getInstance().deleteAllData()
                    suspendCancellableCoroutine { continuation ->
                        CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
                    }
                    cookiesCleared = true
                }
                val prepared = WebViewProxy.prepare()
                if (prepared.isValid) route = prepared
            } catch (error: CancellationException) {
                throw error
            } catch (_: UnsupportedOperationException) {
                errorMessage = R.string.proxy_webview_unsupported
            } catch (_: Exception) {
                errorMessage = R.string.proxy_webview_error
            }
        }
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = currentOrigin,
                        color =
                            if (currentOrigin.isNotEmpty() &&
                                currentOrigin != initialOrigin
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            val prepared = route
            val error = errorMessage
            if (error != null) {
                ErrorContent(IllegalStateException(stringResource(error)), onRetry = { retry++ })
            } else if (prepared != null && prepared.isValid) {
                key(prepared.key, retry) {
                    ProxyLoginWebView(
                        request,
                        prepared,
                        callback,
                        onBack,
                        onOriginChange = { currentOrigin = it },
                        onError = { errorMessage = R.string.proxy_webview_error },
                    )
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ProxyLoginWebView(
    request: LoginEffect.OpenWebCookieLogin,
    route: ProxySnapshot,
    callback: suspend (LoginCookieSnapshot) -> Boolean,
    onBack: () -> Unit,
    onOriginChange: (String) -> Unit,
    onError: () -> Unit,
) {
    val webViewState = rememberWebViewState("about:blank")
    LaunchedEffect(webViewState.lastLoadedUrl) {
        webViewState.lastLoadedUrl?.httpsOriginOrNull()?.let(onOriginChange)
    }
    var unsubscribe by remember { mutableStateOf<(() -> Unit)?>(null) }
    var disposed by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        while (route.isValid) {
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
                if (route.isValid && callback(LoginCookieSnapshot(values = values, rawHeader = rawHeader))) {
                    onBack()
                    return@LaunchedEffect
                }
            }
            delay(2.seconds)
        }
    }
    WebView(
        webViewState,
        layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        modifier = Modifier.alpha(0.99f).background(MaterialTheme.colorScheme.background).fillMaxSize(),
        client =
            remember {
                object : AccompanistWebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: AndroidWebView?,
                        request: WebResourceRequest?,
                    ): Boolean = !route.isValid || (request?.isForMainFrame == true && !request.url.isHttps())

                    @Deprecated("Deprecated in Android")
                    override fun shouldOverrideUrlLoading(
                        view: AndroidWebView?,
                        url: String?,
                    ): Boolean = !route.isValid || url?.httpsOriginOrNull() == null

                    override fun onReceivedError(
                        view: AndroidWebView,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true && route.isValid) onError()
                    }
                }
            },
        onCreated = { view ->
            view.clearCache(true)
            with(view.settings) {
                userAgentString = userAgent.getValue("user-agent")
                javaScriptEnabled = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                allowContentAccess = false
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                blockNetworkLoads = !route.isValid
            }
            unsubscribe =
                route.onInvalidated {
                    view.post {
                        if (disposed) return@post
                        view.stopLoading()
                        view.settings.blockNetworkLoads = true
                        view.loadUrl("about:blank")
                    }
                }
            if (route.isValid && request.url.httpsOriginOrNull() != null) view.loadUrl(request.url)
        },
        onDispose = { view ->
            disposed = true
            unsubscribe?.invoke()
            unsubscribe = null
            clearLoginWebView(view)
        },
    )
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
