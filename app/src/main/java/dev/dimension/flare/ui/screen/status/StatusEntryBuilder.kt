package dev.dimension.flare.ui.screen.status

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.AltTextSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog

internal fun EntryProviderBuilder<NavKey>.statusEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Status.Detail> { args ->
        StatusScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.VVOComment> { args ->
        VVOCommentScreen(
            commentKey = args.commentKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.VVOStatus> { args ->
        VVOStatusScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.AddReaction> { args ->
        AddReactionSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.AltText> { args ->
        AltTextSheet(
            text = args.text,
            onBack = onBack
        )
    }

    entry<Route.Status.BlueskyReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        BlueskyReportStatusDialog(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.DeleteConfirm>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        DeleteStatusConfirmDialog(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.MastodonReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        MastodonReportDialog(
            userKey = args.userKey,
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.MisskeyReport>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        MisskeyReportDialog(
            userKey = args.userKey,
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }
}