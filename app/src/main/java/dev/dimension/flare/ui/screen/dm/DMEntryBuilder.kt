package dev.dimension.flare.ui.screen.dm

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.dmEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
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
            toProfile = { userKey ->
                navigate(Route.Profile.User(args.accountType, userKey))
            },
        )
    }
}