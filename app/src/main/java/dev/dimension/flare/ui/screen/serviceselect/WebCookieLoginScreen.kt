package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.login.WebCookieSeed
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val userAgent =
    mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
    )

private const val ANDROID_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3"

@Composable
internal fun WebCookieLoginScreen(
    url: String,
    initialCookies: List<WebCookieSeed>,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState(url)
    LaunchedEffect(url) {
        while (true) {
            webViewState.lastLoadedUrl?.let { loadedUrl ->
                val cookies =
                    CookieManager
                        .getInstance()
                        .getCookie(loadedUrl)
                if (callback(cookies)) {
                    onBack()
                    break
                }
            }
            delay(2.seconds)
        }
    }
    FlareScaffold {
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
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                with(webView.settings) {
                    userAgentString = ANDROID_USER_AGENT
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                cookieManager.removeAllCookies {
                    initialCookies.forEach { seed ->
                        cookieManager.setCookie(
                            seed.urlForCookieStore(),
                            seed.toAndroidCookieHeader(),
                        )
                    }
                    cookieManager.flush()
                    webView.loadUrl(url, userAgent)
                }
            },
        )
    }
}

private fun WebCookieSeed.urlForCookieStore(): String = "https://$domain$path"

private fun WebCookieSeed.toAndroidCookieHeader(): String =
    buildString {
        append(name)
        append("=")
        append(value)
        append("; Domain=")
        append(domain)
        append("; Path=")
        append(path)
        if (secure) {
            append("; Secure")
        }
    }
