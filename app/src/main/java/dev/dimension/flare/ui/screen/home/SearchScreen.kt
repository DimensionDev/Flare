package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun SearchScreen(
    initialQuery: String,
    accountType: AccountType,
    onAccountClick: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("search_$accountType") { presenter(accountType, initialQuery) }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = lazyListState)
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
                    searchContent(
                        searchUsers = state.searchState.users,
                        searchStatus = state.searchState.status,
                        toUser = onUserClick,
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
    val searchBarState = searchBarPresenter(accountType, initialQuery)
    val searchState =
        remember {
            SearchPresenter(accountType = accountType, initialQuery)
        }.invoke()

    object : SearchBarState by searchBarState {
        val searchState = searchState

        val refreshing =
            searchState.users.isRefreshing ||
                searchState.status.isRefreshing

        fun refresh() {
            searchState.search(query)
        }

        fun commitSearch(new: String) {
            searchBarState.setQuery(new)
            searchBarState.addSearchHistory(new)
            searchState.search(new)
        }
    }
}
