package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.screen.compose.ComposeDialog
import dev.dimension.flare.ui.screen.media.RawMediaScreen
import dev.dimension.flare.ui.screen.media.StatusMediaScreen

@Composable
internal fun FrameWindowScope.WindowRouter(
    route: Route.WindowRoute,
    onBack: () -> Unit,
) {
    when (route) {
        is Route.RawImage ->
            RawMediaScreen(url = route.rawImage)
        is Route.StatusMedia ->
            StatusMediaScreen(
                accountType = route.accountType,
                statusKey = route.statusKey,
                index = route.index,
                window = window,
            )

        is Route.Compose.New ->
            ComposeDialog(
                onBack = onBack,
                accountType = route.accountType,
            )
        is Route.Compose.Quote ->
            ComposeDialog(
                onBack = onBack,
                status = ComposeStatus.Quote(route.statusKey),
                accountType = AccountType.Specific(accountKey = route.accountKey),
            )
        is Route.Compose.Reply ->
            ComposeDialog(
                onBack = onBack,
                status = ComposeStatus.Reply(route.statusKey),
                accountType = AccountType.Specific(accountKey = route.accountKey),
            )
        is Route.Compose.VVOReplyComment ->
            ComposeDialog(
                onBack = onBack,
                accountType = AccountType.Specific(accountKey = route.accountKey),
                status = ComposeStatus.VVOComment(route.replyTo, route.rootId),
            )
    }
}
