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
internal fun WebCookieLoginScreen(
    request: LoginEffect.OpenWebCookieLogin,
    callback: suspend (LoginCookieSnapshot) -> Boolean,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState(request.url)
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
            onCreated = {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
                    userAgentString = userAgent.getValue("user-agent")
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            },
        )
    }
}
