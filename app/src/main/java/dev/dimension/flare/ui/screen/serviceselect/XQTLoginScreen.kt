package dev.dimension.flare.ui.screen.serviceselect

import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultBackNavigator
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
@Destination(
    wrappers = [ThemeWrapper::class],
)
internal fun XQTLoginRoute(resultNavigator: ResultBackNavigator<Boolean>) {
    XQTLoginScreen(
        toHome = {
            resultNavigator.navigateBack(result = true)
        },
    )
}

@EntryNavGraph
@Composable
@Destination(
    wrappers = [ThemeWrapper::class],
)
internal fun EntryXQTLoginRoute(navigator: DestinationsNavigator) {
    XQTLoginScreen(
        toHome = {
            navigator.navigateUp()
        },
    )
}

@Composable
private fun XQTLoginScreen(toHome: () -> Unit) {
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
                        }
                        ?.let {
                            state.login(it)
                        }
                }
            }
            delay(2.seconds)
        }
    }
    WebView(
        webViewState,
        onCreated = {
            // clea all cookies
            CookieManager.getInstance().removeAllCookies(null)
            with(it.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
        },
    )
}

@Composable
private fun xQtLoginPresenter(toHome: () -> Unit) =
    run {
        val state = remember { XQTLoginPresenter(toHome) }.invoke()
        state
    }
