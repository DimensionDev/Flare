package dev.dimension.flare.ui.screen.serviceselect

import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun XQTLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { xQtLoginPresenter(toHome) }
    val webViewState = rememberWebViewState("https://${UiApplication.XQT.host}")
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
            modifier =
                Modifier
                    .padding(it)
                    .fillMaxSize(),
            onCreated = {
                // clea all cookies
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
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
