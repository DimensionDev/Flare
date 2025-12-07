package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState
import dev.dimension.flare.common.APPSCHEMA
import dev.dimension.flare.common.DeeplinkHandler
import dev.dimension.flare.ui.route.Route
import io.ktor.http.Url
import kotlinx.coroutines.delay
import org.cef.network.CefCookieManager
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(route: Route.WebViewLogin) {
    val state = rememberWebViewState(url = route.url)
    LaunchedEffect(Unit) {
        state.cookieManager.removeAllCookies()
    }
    if (route.cookieCallback != null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(2.seconds)
                val url = Url(route.url)
                val cookies = mutableListOf<String>()
                val visited =
                    CefCookieManager.getGlobalManager().visitAllCookies { cookie, count, total, delete ->
                        if (cookie.domain.contains(url.host)) {
                            cookies.add("${cookie.name}=${cookie.value}")
                        }
                        true
                    }
                if (cookies.isNotEmpty()) {
                    route.cookieCallback.invoke(cookies.joinToString("; "))
                }
            }
        }
    }
    LaunchedEffect(state.lastLoadedUrl) {
        state.lastLoadedUrl?.let {
            if (it.startsWith(APPSCHEMA)) {
                DeeplinkHandler.handleDeeplink(it)
            }
        }
    }
    WebView(state = state, modifier = Modifier.fillMaxSize())
}
