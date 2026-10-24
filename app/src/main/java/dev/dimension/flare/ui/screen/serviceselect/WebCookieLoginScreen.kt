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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.common.WebViewProxy
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.component.FlareScaffold
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
    url: String,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState(url)

    // WebView has its own network stack and ignores the Ktor/OkHttp proxy selector, so apply the
    // configured proxy to WebView explicitly while this screen is open.
    val settingsRepository: SettingsRepository by koinInject()
    val settings by settingsRepository.appSettings.collectAsState(AppSettings(version = ""))
    val currentProxy = rememberUpdatedState(settings.proxy)
    LaunchedEffect(settings.proxy) {
        WebViewProxy.apply(settings.proxy)
    }
    DisposableEffect(Unit) {
        onDispose {
            WebViewProxy.clear()
        }
    }

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
            onCreated = {
                // Applying here makes sure the WebView provider is already initialized.
                WebViewProxy.apply(currentProxy.value)
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
                    userAgentString = userAgent.toString()
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            },
        )
    }
}
