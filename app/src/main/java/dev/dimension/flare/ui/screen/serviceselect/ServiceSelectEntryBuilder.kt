package dev.dimension.flare.ui.screen.serviceselect

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderScope<NavKey>.serviceSelectEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.ServiceSelect.Selection> {
        ServiceSelectScreen(
            onWebViewLogin = { url, callback ->
                navigate(
                    Route.ServiceSelect.WebCookieLogin(
                        url = url,
                        callback = callback,
                    ),
                )
            },
            onBack = onBack,
        )
    }

    entry<Route.ServiceSelect.Relogin> { args ->
        ReloginScreen(
            target = args.target,
            onWebViewLogin = { url, callback ->
                navigate(
                    Route.ServiceSelect.WebCookieLogin(
                        url = url,
                        callback = callback,
                    ),
                )
            },
            onBack = onBack,
        )
    }

    entry<Route.ServiceSelect.WebCookieLogin> { args ->
        WebCookieLoginScreen(
            url = args.url,
            callback = args.callback,
            onBack = onBack,
        )
    }
}
