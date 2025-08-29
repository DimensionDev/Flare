package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.WindowScope

@Composable
internal fun WindowScope.WindowRouter(
    route: Route.WindowRoute,
    onBack: () -> Unit,
) {
    RouteContent(
        route = route,
        onBack = onBack,
        navigate = {},
    )
}
