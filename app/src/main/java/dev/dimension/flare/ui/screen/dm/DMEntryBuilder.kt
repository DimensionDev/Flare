package dev.dimension.flare.ui.screen.dm

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.NavigationState

internal fun EntryProviderBuilder<NavKey>.dmEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    navigationState: NavigationState,
) {
    entry<Route.DM.List> { args ->
        DMListScreen(
            accountType = args.accountType,
            onItemClicked = { roomKey ->
                navigate(Route.DM.Conversation(args.accountType, roomKey))
            },
        )
    }
    
    entry<Route.DM.Conversation> { args ->
        DMConversationScreen(
            accountType = args.accountType,
            roomKey = args.roomKey,
            onBack = onBack,
            navigationState = navigationState,
            toProfile = { userKey ->
                navigate(Route.Profile.User(args.accountType, userKey))
            },
        )
    }
    
    entry<Route.DM.UserConversation> { args ->
        UserDMConversationScreen(
            accountType = args.accountType,
            userKey = args.userKey,
            onBack = onBack,
            navigationState = navigationState,
            toProfile = { userKey ->
                navigate(Route.Profile.User(args.accountType, userKey))
            },
        )
    }
}