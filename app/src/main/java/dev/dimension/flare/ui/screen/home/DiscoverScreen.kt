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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverScreen(onUserClick: (AccountType, MicroBlogKey) -> Unit) {
    val state by producePresenter("discover") { discoverPresenter() }
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
                    state.accounts.onSuccess { accounts ->
                        if (accounts.size > 1) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    items(accounts) { profile ->
                                        FilterChip(
                                            selected = state.selectedAccount?.key == profile.key,
                                            onClick = {
                                                state.setAccount(profile)
                                            },
                                            label = {
                                                Text(profile.handle)
                                            },
                                            leadingIcon = {
                                                AvatarComponent(
                                                    data = profile.avatar,
                                                    size = 18.dp,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.isInSearchMode) {
                        searchContent(
                            searchUsers = state.searchState.users,
                            searchStatus = state.searchState.status,
                            toUser = { key ->
                                state.searchState.selectedAccount?.let { account ->
                                    onUserClick(AccountType.Specific(account.key), key)
                                }
                            },
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
                                                    state.selectedAccount?.let { account ->
                                                        onUserClick(
                                                            AccountType.Specific(account.key),
                                                            item.key,
                                                        )
                                                    }
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    repeat(
                                        itemCount,
                                    ) {
                                        val hashtag = get(it)
                                        AdaptiveCard(
                                            modifier =
                                                Modifier
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
private fun discoverPresenter() =
    run {
        val scope = rememberCoroutineScope()
        val state = remember { DiscoverPresenter() }.invoke()
        val searchBarState = searchBarPresenter()
        val searchState =
            remember {
                SearchPresenter(accountType = state.selectedAccountType)
            }.invoke()

        LaunchedEffect(state.selectedAccount) {
            state.selectedAccount?.let { profile ->
                searchState.setAccount(profile)
            }
        }

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
