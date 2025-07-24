package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.isLoading
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.isSuccess
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.isCompat
import dev.dimension.flare.ui.common.isNormal
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverScreen(
    accountType: AccountType,
    onUserClick: (MicroBlogKey) -> Unit,
    onAccountClick: () -> Unit,
) {
    val windowInfo = currentWindowAdaptiveInfo()
    val state by producePresenter("discover_$accountType") { discoverPresenter(accountType) }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
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
                        )
                    } else {
                        if (state.users.isLoading || state.users.isSuccess()) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(R.string.discover_users))
                                    },
                                    colors =
                                        ListItemDefaults
                                            .colors(
                                                containerColor = Color.Transparent,
                                            ),
                                )
                            }
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(
                                        state.users,
                                        loadingContent = {
                                            AdaptiveCard(
                                                modifier =
                                                    Modifier
                                                        .width(256.dp),
                                            ) {
                                                UserPlaceholder(
                                                    modifier = Modifier.padding(8.dp),
                                                )
                                            }
                                        },
                                    ) { item ->
                                        AdaptiveCard(
                                            modifier =
                                                Modifier
                                                    .width(256.dp),
                                        ) {
                                            CommonStatusHeaderComponent(
                                                modifier = Modifier.padding(8.dp),
                                                data = item,
                                                onUserClick = {
                                                    onUserClick(item.key)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.hashtags.onSuccess {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(R.string.discover_hashtags))
                                    },
                                    colors =
                                        ListItemDefaults
                                            .colors(
                                                containerColor = Color.Transparent,
                                            ),
                                )
                            }
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                val maxItemsInEachRow =
                                    if (windowInfo.windowSizeClass.isCompat()
                                    ) {
                                        2
                                    } else if (windowInfo.windowSizeClass.isNormal()
                                    ) {
                                        4
                                    } else {
                                        8
                                    }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    maxItemsInEachRow = maxItemsInEachRow,
                                ) {
                                    repeat(
                                        itemCount,
                                    ) {
                                        val hashtag = get(it)
                                        AdaptiveCard(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .clickable {
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
                                                        modifier =
                                                            Modifier.placeholder(
                                                                true,
                                                            ),
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
                        state.status
                            .onSuccess {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = stringResource(R.string.discover_status))
                                        },
                                        colors =
                                            ListItemDefaults
                                                .colors(
                                                    containerColor = Color.Transparent,
                                                ),
                                    )
                                }
                                status(state.status)
                            }.onLoading {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(text = stringResource(R.string.discover_status))
                                        },
                                        colors =
                                            ListItemDefaults
                                                .colors(
                                                    containerColor = Color.Transparent,
                                                ),
                                    )
                                }
                                status(state.status)
                            }
                    }
                }
            },
        )
    }
}

@Composable
private fun discoverPresenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state = remember(accountType) { DiscoverPresenter(accountType = accountType) }.invoke()
        val searchBarState = searchBarPresenter(accountType = accountType)
        val searchState =
            remember {
                SearchPresenter(accountType = accountType)
            }.invoke()

        object : DiscoverState by state, SearchBarState by searchBarState {
            val searchState = searchState
            val isInSearchMode = queryTextState.text.isNotEmpty()
            val refreshing =
                if (!isInSearchMode) {
                    state.users.isRefreshing ||
                        state.status.isRefreshing ||
                        state.hashtags.isRefreshing
                } else {
                    searchState.users.isRefreshing ||
                        searchState.status.isRefreshing
                }

            fun refresh() {
                if (isInSearchMode) {
                    searchState.search(queryTextState.text.toString())
                } else {
                    scope.launch {
                        state.users.onSuccess {
                            refreshSuspend()
                        }
                        state.status.onSuccess {
                            refreshSuspend()
                        }
                        state.hashtags.onSuccess {
                            refreshSuspend()
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
