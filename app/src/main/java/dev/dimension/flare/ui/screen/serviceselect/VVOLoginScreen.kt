package dev.dimension.flare.ui.screen.serviceselect

import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
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
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.VVOLoginPresenter
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun VVOLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { presenter(toHome) }
    val webViewState = rememberWebViewState(UiApplication.VVo.loginUrl)
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(it)
                    .fillMaxSize(),
            onCreated = {
//                it.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                it.setBackgroundColor(Color.TRANSPARENT)
                // clea all cookies
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
                    javaScriptEnabled = true
//                    domStorageEnabled = true
//                    databaseEnabled = true
//                    javaScriptCanOpenWindowsAutomatically = false
//                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            },
        )
    }
}

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { VVOLoginPresenter(toHome) }.invoke()
    }
