package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope

@Composable
internal fun FrameWindowScope.WindowRouter(
    route: Route.WindowRoute,
    onBack: () -> Unit,
) {
    RouteContent(
        route = route,
        onBack = onBack,
        navigate = {},
    )
}
