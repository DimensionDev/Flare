package dev.dimension.flare.ui.screen.list

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.listEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Lists.List> { args ->
        ListScreen(
            accountType = args.accountType,
            toList = { item ->
                navigate(
                    Route.Timeline(
                        args.accountType,
                        ListTimelineTabItem(
                            account = args.accountType,
                            listId = item.id,
                            metaData = TabMetaData(
                                title = TitleType.Text(item.title),
                                icon = IconType.Material(IconType.Material.MaterialIcon.List),
                            ),
                        )
                    )
                )
            },
            createList = {
                navigate(Route.Lists.Create(args.accountType))
            },
            editList = { item ->
                navigate(Route.Lists.Edit(args.accountType, item.id))
            },
            deleteList = { item ->
                navigate(Route.Lists.Delete(args.accountType, item.id, item.title))
            },
        )
    }

    entry<Route.Lists.Create>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        CreateListDialog(
            accountType = args.accountType,
            onDismissRequest = onBack,
        )
    }

    entry<Route.Lists.Edit> { args ->
        EditListScreen(
            accountType = args.accountType,
            listId = args.listId,
            onBack = onBack,
            toEditUser = {
                navigate(Route.Lists.EditMember(args.accountType, args.listId))
            },
        )
    }

    entry<Route.Lists.EditAccountList> { args ->
        EditAccountListScreen(
            accountType = args.accountType,
            userKey = args.userKey,
            onBack = onBack,
        )
    }

    entry<Route.Lists.EditMember> { args ->
        EditListMemberScreen(
            accountType = args.accountType,
            listId = args.listId,
            onBack = onBack,
        )
    }

    entry<Route.Lists.Delete>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        DeleteListDialog(
            accountType = args.accountType,
            listId = args.listId,
            title = args.title,
            onDismissRequest = onBack,
        )
    }
}
