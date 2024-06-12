package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ProfileRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onNotEmptyOrLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.mastodon.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun AnimatedVisibilityScope.DiscoverRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    tabState: TabState,
    drawerState: DrawerState,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    val scope = rememberCoroutineScope()
    DiscoverScreen(
        accountType = accountType,
        tabState = tabState,
        onUserClick = { navigator.navigate(ProfileRouteDestination(it, accountType)) },
        onAccountClick = {
            scope.launch {
                drawerState.open()
            }
        },
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DiscoverScreen(
    accountType: AccountType,
    tabState: TabState,
    onUserClick: (MicroBlogKey) -> Unit,
    onAccountClick: () -> Unit,
) {
    val windowInfo = currentWindowAdaptiveInfo()
    val state by producePresenter("discover_$accountType") { discoverPresenter(accountType) }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(tabState = tabState, lazyListState = lazyListState)
    BackHandler(enabled = state.isInSearchMode) {
        state.clearSearch()
    }
    FlareScaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SearchBar(
                    state = state,
                    onAccountClick = onAccountClick,
                    onSearch = {
                        state.commitSearch(it)
                    },
                )
            }
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.refreshing,
            onRefresh = state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (state.isInSearchMode) {
                        searchContent(
                            searchUsers = state.searchState.users,
                            searchStatus = state.searchState.status,
                            toUser = onUserClick,
                            statusEvent = state.statusEvent,
                        )
                    } else {
                        state.users.onSuccess { users ->
                            users.onNotEmptyOrLoading {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = stringResource(R.string.discover_users))
                                        },
                                    )
                                }
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    LazyHorizontalGrid(
                                        modifier = Modifier.height(128.dp),
                                        rows = GridCells.Fixed(2),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                    ) {
                                        users
                                            .onSuccess {
                                                items(
                                                    users.itemCount,
                                                    key = users.itemKey { it.itemKey },
                                                ) {
                                                    val user = users[it]
                                                    Card(
                                                        modifier =
                                                            Modifier
                                                                .width(256.dp),
                                                    ) {
                                                        if (user != null) {
                                                            CommonStatusHeaderComponent(
                                                                data = user,
                                                                onUserClick = onUserClick,
                                                                modifier = Modifier.padding(8.dp),
                                                            )
                                                        } else {
                                                            UserPlaceholder(
                                                                modifier = Modifier.padding(8.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }.onLoading {
                                                items(10) {
                                                    Card(
                                                        modifier =
                                                            Modifier
                                                                .width(256.dp),
                                                    ) {
                                                        UserPlaceholder(
                                                            modifier = Modifier.padding(8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        state.hashtags.onSuccess { hashtags ->
                            hashtags.onNotEmptyOrLoading {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = stringResource(R.string.discover_hashtags))
                                        },
                                    )
                                }
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    val maxItemsInEachRow =
                                        if (windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
                                            2
                                        } else if (windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM) {
                                            4
                                        } else {
                                            8
                                        }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                        maxItemsInEachRow = maxItemsInEachRow,
                                    ) {
                                        hashtags
                                            .onSuccess {
                                                repeat(
                                                    hashtags.itemCount,
                                                ) {
                                                    val hashtag = hashtags[it]
                                                    Card(
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            hashtag?.searchContent?.let { it1 ->
                                                                state.commitSearch(
                                                                    it1,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .padding(8.dp),
                                                        ) {
                                                            if (hashtag != null) {
                                                                Text(
                                                                    text = hashtag.hashtag,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = "Lorem Ipsum is simply dummy text",
                                                                    modifier = Modifier.placeholder(true),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }.onLoading {
                                                repeat(10) {
                                                    Card(
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(8.dp),
                                                        ) {
                                                            Text(
                                                                text = "Lorem Ipsum is simply dummy text",
                                                                modifier = Modifier.placeholder(true),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        state.status.onSuccess {
                            it.onNotEmptyOrLoading {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = stringResource(R.string.discover_status))
                                        },
                                    )
                                }
                                with(state.status) {
                                    with(state.statusEvent) {
                                        status()
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun discoverPresenter(
    accountType: AccountType,
    statusEvent: StatusEvent = koinInject(),
) = run {
    val scope = rememberCoroutineScope()
    val state = remember(accountType) { DiscoverPresenter(accountType = accountType) }.invoke()
    val searchBarState = searchBarPresenter(accountType = accountType)
    val searchState =
        remember {
            SearchPresenter(accountType = accountType)
        }.invoke()

    object : DiscoverState by state, SearchBarState by searchBarState {
        val statusEvent = statusEvent
        val searchState = searchState
        val isInSearchMode = query.isNotEmpty()
        val refreshing =
            if (!isInSearchMode) {
                state.users is UiState.Loading ||
                    state.status is UiState.Loading ||
                    state.hashtags is UiState.Loading ||
                    state.users is UiState.Success &&
                    (state.users as UiState.Success<LazyPagingItemsProxy<UiUser>>).data.isRefreshing ||
                    state.status is UiState.Success &&
                    (state.status as UiState.Success<LazyPagingItemsProxy<UiStatus>>).data.isRefreshing ||
                    state.hashtags is UiState.Success &&
                    (state.hashtags as UiState.Success<LazyPagingItemsProxy<UiHashtag>>).data.isRefreshing
            } else {
                searchState.users is UiState.Loading ||
                    searchState.status is UiState.Loading ||
                    searchState.users is UiState.Success &&
                    (searchState.users as UiState.Success<LazyPagingItemsProxy<UiUser>>).data.isRefreshing ||
                    searchState.status is UiState.Success &&
                    (searchState.status as UiState.Success<LazyPagingItemsProxy<UiStatus>>).data.isRefreshing
            }

        fun refresh() {
            if (isInSearchMode) {
                searchState.search(query)
            } else {
                scope.launch {
                    state.users.onSuccess {
                        it.refreshSuspend()
                    }
                    state.status.onSuccess {
                        it.refreshSuspend()
                    }
                    state.hashtags.onSuccess {
                        it.refreshSuspend()
                    }
                }
            }
        }

        fun commitSearch(new: String) {
            searchBarState.setQuery(new)
            searchBarState.addSearchHistory(new)
            searchState.search(new)
        }

        fun clearSearch() {
            searchBarState.setQuery("")
        }
    }
}
