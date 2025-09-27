package dev.dimension.flare.ui.screen.profile

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.profileEntryBuilder(
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
}