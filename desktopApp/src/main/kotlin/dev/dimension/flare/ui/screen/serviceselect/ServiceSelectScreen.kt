package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.presenter.login.VVOLoginPresenter
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun ServiceSelectScreen(
    onBack: () -> Unit,
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
//    val webviewBridge = koinInject<WebViewBridge>()
    val xqtLoginState by producePresenter("xqt_login_state") {
        remember {
            XQTLoginPresenter(toHome = onBack)
        }.body()
    }
    val vvoLoginState by producePresenter("vvo_login_state") {
        remember {
            VVOLoginPresenter(toHome = onBack)
        }.body()
    }
    ServiceSelectionScreenContent(
        contentPadding = LocalWindowPadding.current,
        onXQT = {
            onWebViewLogin.invoke(
                "https://${UiApplication.XQT.host}",
                { cookies ->
                    if (cookies.isNullOrEmpty()) {
                        false
                    } else {
                        xqtLoginState.checkChocolate(cookies).also {
                            if (it) {
                                xqtLoginState.login(cookies)
                            }
                        }
                    }
                },
            )
//            webviewBridge.openAndWaitCookies(
//                "https://${UiApplication.XQT.host}",
//                callback = { cookies ->
//                    if (cookies.isNullOrEmpty()) {
//                        false
//                    } else {
//                        xqtLoginState.checkChocolate(cookies).also {
//                            if (it) {
//                                xqtLoginState.login(cookies)
//                            }
//                        }
//                    }
//                },
//            )
        },
        onVVO = {
            onWebViewLogin.invoke(
                UiApplication.VVo.loginUrl,
                { cookies ->
                    if (cookies.isNullOrEmpty()) {
                        false
                    } else {
                        vvoLoginState.checkChocolate(cookies).also {
                            if (it) {
                                vvoLoginState.login(cookies)
                            }
                        }
                    }
                },
            )
//            webviewBridge.openAndWaitCookies(
//                UiApplication.VVo.loginUrl,
//                callback = { cookies ->
//                    if (cookies.isNullOrEmpty()) {
//                        false
//                    } else {
//                        vvoLoginState.checkChocolate(cookies).also {
//                            if (it) {
//                                vvoLoginState.login(cookies)
//                            }
//                        }
//                    }
//                },
//            )
        },
        openUri = uriHandler::openUri,
        registerDeeplinkCallback = { callback ->
            OnDeepLink {
                callback(it)
                true
            }
        },
        onBack = onBack,
    )
}
