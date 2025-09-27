package dev.dimension.flare.ui.screen.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalSharedTransitionApi::class)
internal fun EntryProviderBuilder<NavKey>.composeEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Compose.New>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        ComposeScreen(
            onBack = onBack,
            accountType = args.accountType,
        )
    }

    entry<Route.Compose.Reply>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        ComposeScreen(
            onBack = onBack,
            status = ComposeStatus.Reply(args.statusKey),
            accountType = AccountType.Specific(accountKey = args.accountKey),
        )
    }

    entry<Route.Compose.Quote>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        ComposeScreen(
            onBack = onBack,
            status = ComposeStatus.Quote(args.statusKey),
            accountType = AccountType.Specific(accountKey = args.accountKey),
        )
    }

    entry<Route.Compose.VVOReplyComment>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        ComposeScreen(
            onBack = onBack,
            accountType = AccountType.Specific(accountKey = args.accountKey),
            status = ComposeStatus.VVOComment(args.replyTo, args.rootId),
        )
    }
}
