package dev.dimension.flare.ui.screen.serviceselect

import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kevinnzou.accompanist.web.LoadingState
import com.kevinnzou.accompanist.web.WebView
import com.kevinnzou.accompanist.web.rememberWebViewState
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiApplication

@Composable
@Destination(
    wrappers = [ThemeWrapper::class],
)
internal fun XQTLoginRoute() {
    XQTLoginScreen()
}

@Composable
private fun XQTLoginScreen() {
    val state = rememberWebViewState(UiApplication.XQT.host)
    LaunchedEffect(state.loadingState) {
        if (state.loadingState is LoadingState.Loading) {
            CookieManager.getInstance().getCookie(UiApplication.XQT.host)?.let {
                println(it)
            }
        }
    }
    WebView(
        state,
        onCreated = {
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
