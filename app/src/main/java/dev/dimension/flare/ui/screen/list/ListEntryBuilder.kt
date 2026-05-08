package dev.dimension.flare.ui.screen.list

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.TimelineScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.listEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Lists.List>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Lists",
            detailPlaceholder = {
                ListDetailPlaceholder()
            }
        )
    ) { args ->
        ListScreen(
            accountType = args.accountType,
            toList = { item ->
                navigate(
                    Route.Lists.Detail(
                        accountType = args.accountType,
                        listId = item.id,
                        title = item.title
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
            onBack = onBack,
        )
    }

    entry<Route.Lists.Detail>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Lists",
        )
    ) { args ->
        TimelineScreen(
            tabItem = remember(args) {
                SourceTimelineTabItemV2.runtime(
                    id = "list_${args.accountType}_${args.listId}",
                    title = UiText.Raw(args.title),
                    icon = IconType.Material(UiIcon.List),
                    createPresenter = {
                        ListTimelinePresenter(
                            accountType = args.accountType,
                            listId = args.listId,
                        )
                    },
                )
            },
            onBack = onBack,
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
