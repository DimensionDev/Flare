package dev.dimension.flare.ui.screen.home

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ComposeRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ServiceSelectRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.ThemeWrapper
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun HomeTimelineRoute(
    navigator: DestinationsNavigator,
    drawerState: DrawerState,
    accountType: AccountType,
) {
    val scope = rememberCoroutineScope()
    HomeTimelineScreen(
        toCompose = {
            navigator.navigate(ComposeRouteDestination(accountType = accountType))
        },
        toQuickMenu = {
            scope.launch {
                drawerState.open()
            }
        },
        toLogin = {
            navigator.navigate(ServiceSelectRouteDestination)
        },
    )
}

@Composable
private fun HomeTimelineScreen(
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
) {
}
