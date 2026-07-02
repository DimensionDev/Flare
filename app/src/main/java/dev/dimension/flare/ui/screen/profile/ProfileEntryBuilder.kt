package dev.dimension.flare.ui.screen.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.component.DialogSceneStrategy2
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.profileEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Profile.UserNameWithHost> { args ->
        ProfileWithUserNameAndHostDeeplinkRoute(
            userName = args.name,
            host = args.host,
            accountType = args.accountType,
            onBack = onBack,
            onMediaClick = { statusKey, index, preview ->
                navigate(
                    Route.Media.StatusMedia(
                        statusKey = statusKey,
                        accountType = args.accountType,
                        index = index,
                        preview = preview,
                    )
                )
            },
            toEditAccountList = { userKey ->
                navigate(
                    Route.Lists.EditAccountList(
                        accountType = args.accountType,
                        userKey = userKey,
                    )
                )
            },
            toSearchUserUsingAccount = { handle, accountKey ->
                navigate(
                    Route.Search(
                        query = handle,
                        accountType = args.accountType,
                    )
                )
            },
            toStartMessage = {
                navigate(
                    Route.DM.UserConversation(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
            onFollowListClick = {
                navigate(
                    Route.Profile.Following(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onFansListClick = {
                navigate(
                    Route.Profile.Fans(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onProfileInsightClick = {
                navigate(
                    Route.Profile.Insight(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
        )
    }
    entry<Route.Profile.User> { args ->
        ProfileScreen(
            userKey = args.userKey,
            onBack = onBack,
            showBackButton = true,
            onMediaClick = { statusKey, index, preview ->
                navigate(
                    Route.Media.StatusMedia(
                        statusKey = statusKey,
                        accountType = args.accountType,
                        index = index,
                        preview = preview,
                    )
                )
            },
            accountType = args.accountType,
            toEditAccountList = {
                navigate(
                    Route.Lists.EditAccountList(
                        accountType = args.accountType,
                        userKey = args.userKey,
                    )
                )
            },
            toSearchUserUsingAccount = { handle, accountKey ->
                navigate(
                    Route.Search(
                        query = handle,
                        accountType = args.accountType,
                    )
                )
            },
            toStartMessage = {
                navigate(
                    Route.DM.UserConversation(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
            onFollowListClick = {
                navigate(
                    Route.Profile.Following(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onFansListClick = {
                navigate(
                    Route.Profile.Fans(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onProfileInsightClick = {
                navigate(
                    Route.Profile.Insight(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
        )
    }
    
    entry<Route.Profile.Me> { args ->
        ProfileScreen(
            userKey = null,
            onBack = onBack,
            showBackButton = false,
            onMediaClick = { statusKey, index, preview ->
                navigate(
                    Route.Media.StatusMedia(
                        statusKey = statusKey,
                        accountType = args.accountType,
                        index = index,
                        preview = preview,
                    )
                )
            },
            accountType = args.accountType,
            toEditAccountList = {
                // TODO: Add route for edit account list
            },
            toSearchUserUsingAccount = { handle, accountKey ->
                navigate(
                    Route.Search(
                        query = handle,
                        accountType = args.accountType,
                    )
                )
            },
            toStartMessage = {
                navigate(
                    Route.DM.UserConversation(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
            onFollowListClick = {
                navigate(
                    Route.Profile.Following(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onFansListClick = {
                navigate(
                    Route.Profile.Fans(
                        userKey = it,
                        accountType = args.accountType,
                    )
                )
            },
            onProfileInsightClick = {
                navigate(
                    Route.Profile.Insight(
                        accountType = args.accountType,
                        userKey = it,
                    )
                )
            },
        )
    }
    
    entry<Route.Profile.Following> { args ->
        FollowingScreen(
            accountType = args.accountType,
            userKey = args.userKey,
            onBack = onBack,
            onUserClick = { userKey ->
                navigate(
                    Route.Profile.User(
                        userKey = userKey,
                        accountType = args.accountType,
                    )
                )
            }
        )
    }
    
    entry<Route.Profile.Fans> { args ->
        FansScreen(
            accountType = args.accountType,
            userKey = args.userKey,
            onBack = onBack,
            onUserClick = { userKey ->
                navigate(
                    Route.Profile.User(
                        userKey = userKey,
                        accountType = args.accountType,
                    )
                )
            }
        )
    }

    entry<Route.Profile.Insight>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        ProfileInsightSheet(
            accountType = args.accountType,
            userKey = args.userKey,
            navigate = navigate,
        )
    }

    entry<Route.BlockUser>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        BlockUserDialog(
            accountType = it.accountType,
            userKey = it.userKey,
            onBack = onBack,
        )
    }

    entry<Route.MuteUser>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        MuteUserDialog(
            accountType = it.accountType,
            userKey = it.userKey,
            onBack = onBack,
        )
    }

    entry<Route.UnblockUser>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        UnblockUserDialog(
            accountType = it.accountType,
            userKey = it.userKey,
            onBack = onBack,
        )
    }

    entry<Route.UnmuteUser>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        UnmuteUserDialog(
            accountType = it.accountType,
            userKey = it.userKey,
            onBack = onBack,
        )
    }

    entry<Route.ReportUser>(
        metadata = DialogSceneStrategy2.dialog()
    ) {
        ReportUserDialog(
            accountType = it.accountType,
            userKey = it.userKey,
            onBack = onBack,
        )
    }


}
