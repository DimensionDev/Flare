package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.TimelineRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Mastodon
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.home.mastodon.AllListPresenter
import dev.dimension.flare.ui.presenter.invoke

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun MastodonListScreenRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    ListScreen(
        accountType = accountType,
        toList = { item ->
            navigator.navigate(
                TimelineRouteDestination(
                    tabItem =
                        Mastodon.ListTimelineTabItem(
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
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    accountType: AccountType,
    toList: (UiList) -> Unit,
    createList: () -> Unit,
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
            isRefreshing = state.items.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    state.items
                        .onSuccess {
                            items(count = itemCount) { index ->
                                val item = get(index)
                                if (item == null) {
                                    ItemPlaceHolder()
                                } else {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = item.title)
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                toList(item)
                                            },
                                    )
                                }
                            }
                        }.onLoading {
                            items(count = 10) {
                                ItemPlaceHolder()
                            }
                        }.onEmpty {
                            item {
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
                            }
                        }.onError {
                            item {
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
                            }
                        }
                }
            },
        )
    }
}

@Composable
private fun ItemPlaceHolder(modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = "lore ipsum dolor sit amet",
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
