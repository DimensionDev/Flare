package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.wrapper.DestinationWrapper
import dev.dimension.flare.ui.common.ProxyUriHandler
import dev.dimension.flare.ui.theme.FlareTheme

internal object ThemeWrapper : DestinationWrapper {
    @Composable
    override fun <T> DestinationScope<T>.Wrap(screenContent: @Composable () -> Unit) {
        FlareTheme(content = screenContent)
    }
}

internal object DialogWrapper : DestinationWrapper {
    @Composable
    override fun <T> DestinationScope<T>.Wrap(screenContent: @Composable (() -> Unit)) {
        val uriHandler = LocalUriHandler.current
        CompositionLocalProvider(
            LocalUriHandler provides
                remember {
                    ProxyUriHandler(
                        navController = navController,
                        actualUriHandler = uriHandler,
                    )
                },
            content = screenContent,
        )
    }
}
