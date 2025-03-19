package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import dev.dimension.flare.ui.screen.media.RawMediaScreen
import dev.dimension.flare.ui.screen.media.StatusMediaScreen

@Composable
internal fun FrameWindowScope.WindowRouter(
    route: WindowRoute,
    onBack: () -> Unit,
) {
    when (route) {
        is WindowRoute.RawImage ->
            RawMediaScreen(url = route.url)
        is WindowRoute.StatusMedia ->
            StatusMediaScreen(
                accountType = route.accountType,
                statusKey = route.statusKey,
                index = route.index,
                window = window,
            )
    }
}
