package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent

@Composable
internal fun ServiceSelectScreen(
    onBack: () -> Unit,
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
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
