package dev.dimension.flare.ui.screen.list

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CreateListRouteDestination
import com.ramcosta.composedestinations.generated.destinations.DeleteListRouteDestination
import com.ramcosta.composedestinations.generated.destinations.EditListRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AllListPresenter
import dev.dimension.flare.ui.screen.home.TimelineRoute
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.parcelize.Parcelize

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun ListScreenRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    drawerState: DrawerState,
) {
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator<ListDetailPaneNavArgs>()
    BackHandler(
        scaffoldNavigator.canNavigateBack(),
    ) {
        scaffoldNavigator.navigateBack()
    }
    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                ListScreen(
                    accountType = accountType,
                    toList = { item ->
                        scaffoldNavigator.navigateTo(
                            ListDetailPaneScaffoldRole.Detail,
                            ListDetailPaneNavArgs(
                                id = item.id,
                                title = item.title,
                            ),
                        )
                    },
                    createList = {
                        navigator.navigate(CreateListRouteDestination(accountType = accountType))
                    },
                    editList = {
                        navigator.navigate(
                            EditListRouteDestination(
                                accountType = accountType,
                                listId = it.id,
                            ),
                        )
                    },
                    deleteList = {
                        navigator.navigate(
                            DeleteListRouteDestination(
                                accountType = accountType,
                                listId = it.id,
                                title = it.title,
                            ),
                        )
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.content?.let { args ->
                    TimelineRoute(
                        navigator = navigator,
                        tabItem =
                            ListTimelineTabItem(
                                account = accountType,
                                listId = args.id,
                                metaData =
                                    TabMetaData(
                                        title = TitleType.Text(args.title),
                                        icon = IconType.Material(IconType.Material.MaterialIcon.List),
                                    ),
                            ),
                        drawerState = drawerState,
                    )
                }
            }
        },
    )
}

@Parcelize
private data class ListDetailPaneNavArgs(
    val id: String,
    val title: String,
) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    accountType: AccountType,
    toList: (UiList) -> Unit,
    createList: () -> Unit,
    editList: (UiList) -> Unit,
    deleteList: (UiList) -> Unit,
) {
    val state by producePresenter {
        presenter(accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_list_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            createList.invoke()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.list_add),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    listItemComponent(
                        state.items,
                        toList,
                        trailingContent = { item ->
                            var showDropdown by remember {
                                mutableStateOf(false)
                            }
                            IconButton(onClick = { showDropdown = true }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(id = R.string.more),
                                )
                                DropdownMenu(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(id = R.string.list_edit),
                                            )
                                        },
                                        onClick = {
                                            editList(item)
                                            showDropdown = false
                                        },
                                        leadingIcon = {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Pen,
                                                contentDescription = stringResource(id = R.string.list_edit),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(id = R.string.list_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            deleteList(item)
                                            showDropdown = false
                                        },
                                        leadingIcon = {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Trash,
                                                contentDescription = stringResource(id = R.string.list_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
        )
    }
}

internal fun LazyListScope.listItemComponent(
    items: PagingState<UiList>,
    onClicked: ((UiList) -> Unit)? = null,
    trailingContent: @Composable (UiList) -> Unit = {},
) {
    items(
        items,
        emptyContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.List,
                    contentDescription = stringResource(id = R.string.list_empty),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(id = R.string.list_empty),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        },
        loadingContent = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ItemPlaceHolder()
                HorizontalDivider()
            }
        },
        errorContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                    contentDescription = stringResource(id = R.string.list_error),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(id = R.string.list_error),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        },
    ) { item ->
        Column(
            modifier =
                Modifier
                    .let {
                        if (onClicked == null) {
                            it
                        } else {
                            it
                                .clickable {
                                    onClicked(item)
                                }
                        }
                    },
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ListComponent(
                headlineContent = {
                    Text(text = item.title)
                },
                leadingContent = {
                    if (item.avatar != null) {
                        NetworkImage(
                            model = item.avatar,
                            contentDescription = item.title,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size)
                                    .clip(MaterialTheme.shapes.medium),
                        )
                    } else {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Rss,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.medium,
                                    ).padding(8.dp),
                        )
                    }
                },
                supportingContent = {
                    if (item.creator != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.feeds_discover_feeds_created_by,
                                    item.creator?.handle ?: "Unknown",
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .alpha(MediumAlpha),
                        )
                    }
                },
                trailingContent = {
                    trailingContent.invoke(item)
                },
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
            item.description?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
        }
    }
}

@Composable
private fun ItemPlaceHolder(modifier: Modifier = Modifier) {
    ListComponent(
        modifier = modifier,
        headlineContent = {
            Text(
                text = "lore ipsum dolor sit amet",
                modifier = Modifier.placeholder(true),
            )
        },
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(AvatarComponentDefaults.size)
                        .clip(MaterialTheme.shapes.medium)
                        .placeholder(true),
            )
        },
        supportingContent = {
            Text(
                text = "lore ipsum",
                modifier = Modifier.placeholder(true),
            )
        },
    )
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) {
            AllListPresenter(accountType)
        }.invoke()
    }
