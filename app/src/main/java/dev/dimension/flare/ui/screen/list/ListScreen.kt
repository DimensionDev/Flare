package dev.dimension.flare.ui.screen.list

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.ramcosta.composedestinations.generated.destinations.TimelineRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
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
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AllListPresenter
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun ListScreenRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    ListScreen(
        accountType = accountType,
        toList = { item ->
            navigator.navigate(
                TimelineRouteDestination(
                    tabItem =
                        ListTimelineTabItem(
                            account = accountType,
                            listId = item.id,
                            metaData =
                                TabMetaData(
                                    title = TitleType.Text(item.title),
                                    icon = IconType.Material(IconType.Material.MaterialIcon.List),
                                ),
                        ),
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
                        Icon(
                            imageVector = Icons.Default.Add,
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
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
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
                                            Icon(
                                                imageVector = Icons.Default.Edit,
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
                                            Icon(
                                                imageVector = Icons.Default.Delete,
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
    trailingContent: @Composable (UiList) -> Unit,
) {
    items(
        items,
        emptyContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
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
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
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
                    }.padding(horizontal = screenHorizontalPadding),
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
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.medium,
                                    ),
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
            )
            item.description?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
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
