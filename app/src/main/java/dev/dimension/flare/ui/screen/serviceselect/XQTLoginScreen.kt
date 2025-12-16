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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

// https://github.com/j-fbriere/squawker/blob/20ab95b1b5cdada080aa8ebd1d5e0f3ad7c719e5/lib/constants.dart#L87-L93
// idk why it works but it works
private val userAgent =
    mapOf(
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
    )

@Composable
internal fun XQTLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { xQtLoginPresenter(toHome) }
    val webViewState = rememberWebViewState("https://${UiApplication.XQT.host}/i/flow/login")
    LaunchedEffect(Unit) {
        while (true) {
            if (!state.loading) {
                webViewState.lastLoadedUrl?.let { url ->
                    CookieManager
                        .getInstance()
                        .getCookie(url)
                        ?.takeIf {
                            state.checkChocolate(it)
                        }?.let {
                            state.login(it)
                        }
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
                // clea all cookies
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

@Composable
private fun xQtLoginPresenter(toHome: () -> Unit) =
    run {
        val state = remember { XQTLoginPresenter(toHome) }.invoke()
        state
    }
