package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.route.APPSCHEMA
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ServiceSelectScreen(
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier =
            modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ServiceSelectionScreenContent(
            onWebViewLogin = onWebViewLogin,
            contentPadding = contentPadding,
            openUri = uriHandler::openUri,
            registerDeeplinkCallback = { callback ->
                OnNewIntent {
                    val url = it.dataString.orEmpty()
                    if (url.startsWith("$APPSCHEMA://", ignoreCase = true) || url.isPixivOAuthCallback()) {
                        callback.invoke(url)
                    }
                }
            },
            onBack = onBack,
        )
    }
}

private fun String.isPixivOAuthCallback(): Boolean =
    startsWith(
        prefix = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback",
        ignoreCase = true,
    ) ||
        startsWith(
            prefix = "pixiv://account/login",
            ignoreCase = true,
        )
