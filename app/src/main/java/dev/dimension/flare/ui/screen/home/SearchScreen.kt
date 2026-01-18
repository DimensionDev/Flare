package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun SearchScreen(
    initialQuery: String,
    accountType: AccountType,
    onUserClick: (AccountType, MicroBlogKey) -> Unit,
) {
    val state by producePresenter("search_${accountType}_$initialQuery") { presenter(accountType, initialQuery) }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
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
                    state.searchState.accounts.onSuccess { accounts ->
                        if (accounts.size > 1) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    items(accounts.size) { index ->
                                        val profile = accounts[index]
                                        FilterChip(
                                            selected = state.searchState.selectedAccount?.key == profile.key,
                                            onClick = {
                                                state.searchState.setAccount(profile)
                                            },
                                            label = {
                                                Text(profile.handle)
                                            },
                                            leadingIcon = {
                                                if (state.searchState.selectedAccount?.key == profile.key) {
                                                    AvatarComponent(
                                                        data = profile.avatar,
                                                        size = 18.dp,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    searchContent(
                        searchUsers = state.searchState.users,
                        searchStatus = state.searchState.status,
                        toUser = { key ->
                            state.searchState.selectedAccount?.let { account ->
                                onUserClick(AccountType.Specific(account.key), key)
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    initialQuery: String,
) = run {
    val searchBarState = searchBarPresenter(initialQuery)
    val searchState =
        remember(initialQuery, accountType) {
            SearchPresenter(accountType = accountType, initialQuery)
        }.invoke()

    object : SearchBarState by searchBarState {
        val searchState = searchState

        val refreshing =
            searchState.users.isRefreshing ||
                searchState.status.isRefreshing

        fun refresh() {
            searchState.search(queryTextState.text.toString())
        }

        fun commitSearch(new: String) {
            searchBarState.setQuery(new)
            searchBarState.addSearchHistory(new)
            searchState.search(new)
        }
    }
}
