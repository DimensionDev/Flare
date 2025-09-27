package dev.dimension.flare.ui.screen.serviceselect

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.serviceSelectEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.ServiceSelect.Selection> {
        ServiceSelectScreen(
            onXQT = {
                navigate(Route.ServiceSelect.XQTLogin)
            },
            onVVO = {
                navigate(Route.ServiceSelect.VVOLogin)
            },
            onBack = onBack,
        )
    }

    entry<Route.ServiceSelect.XQTLogin> {
        XQTLoginScreen(
            toHome = {
                onBack()
            }
        )
    }

    entry<Route.ServiceSelect.VVOLogin> {
        VVOLoginScreen(
            toHome = {
                onBack()
            }
        )
    }
}
