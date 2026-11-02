package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginEffect
import dev.dimension.flare.ui.presenter.login.ReloginTarget
import dev.dimension.flare.ui.screen.login.ReloginScreenContent
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent

@Composable
internal fun ServiceSelectScreen(
    onBack: () -> Unit,
    onWebViewLogin: (
        request: LoginEffect.OpenWebCookieLogin,
        cookieCallback: suspend (LoginCookieSnapshot) -> Boolean,
    ) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ServiceSelectionScreenContent(
        contentPadding = LocalWindowPadding.current,
        onWebViewLogin = onWebViewLogin,
        openUri = uriHandler::openUri,
        registerDeeplinkCallback = { callback ->
            OnDeepLink {
                callback(it)
            }
        },
        onBack = onBack,
    )
}

@Composable
internal fun ReloginScreen(
    target: ReloginTarget,
    onBack: () -> Unit,
    onWebViewLogin: (
        request: LoginEffect.OpenWebCookieLogin,
        cookieCallback: suspend (LoginCookieSnapshot) -> Boolean,
    ) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ReloginScreenContent(
        target = target,
        contentPadding = LocalWindowPadding.current,
        onWebViewLogin = onWebViewLogin,
        openUri = uriHandler::openUri,
        registerDeeplinkCallback = { callback ->
            OnDeepLink {
                callback(it)
            }
        },
        onBack = onBack,
    )
}
