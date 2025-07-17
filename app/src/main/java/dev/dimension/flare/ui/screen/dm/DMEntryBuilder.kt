package dev.dimension.flare.ui.screen.dm

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.NavigationState

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderBuilder<NavKey>.dmEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    navigationState: NavigationState,
) {
    entry<Route.DM.List>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "DirectMessage",
            detailPlaceholder = {
                DMConversationDetailPlaceholder()
            }
        )
    ) { args ->
        DMListScreen(
            accountType = args.accountType,
            onItemClicked = { roomKey ->
                navigate(Route.DM.Conversation(args.accountType, roomKey))
            },
            onBack = onBack,
        )
    }
    
    entry<Route.DM.Conversation>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "DirectMessage",
        )
    ) { args ->
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
    
    entry<Route.DM.UserConversation>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "DirectMessage",
        )
    ) { args ->
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