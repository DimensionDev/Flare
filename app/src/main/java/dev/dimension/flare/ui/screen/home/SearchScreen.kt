package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.generated.destinations.ProfileRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.isLoading
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
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Search.ROUTE,
        ),
    ],
)
@Composable
internal fun AnimatedVisibilityScope.SearchDeepLink(
    accountKey: MicroBlogKey,
    keyword: String,
    navigator: DestinationsNavigator,
    drawerState: DrawerState,
    tabState: TabState,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    SearchRoute(
        keyword = keyword,
        navigator = navigator,
        accountType = AccountType.Specific(accountKey),
        drawerState = drawerState,
        sharedTransitionScope = sharedTransitionScope,
        tabState = tabState,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
)
@Composable
internal fun AnimatedVisibilityScope.SearchRoute(
    keyword: String,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    drawerState: DrawerState,
    tabState: TabState,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    val scope = rememberCoroutineScope()
    SearchScreen(
        initialQuery = keyword,
        accountType = accountType,
        tabState = tabState,
        onAccountClick = {
            scope.launch {
                drawerState.open()
            }
        },
        onUserClick = { navigator.navigate(ProfileRouteDestination(it, accountType)) },
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchScreen(
    initialQuery: String,
    accountType: AccountType,
    tabState: TabState,
    onAccountClick: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("search_$accountType") { presenter(accountType, initialQuery) }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(tabState = tabState, lazyListState = lazyListState)
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
            searchState.users is UiState.Loading ||
                searchState.status is UiState.Loading ||
                searchState.users is UiState.Success &&
                (searchState.users as UiState.Success<LazyPagingItems<UiUserV2>>).data.isLoading ||
                searchState.status is UiState.Success &&
                (searchState.status as UiState.Success<LazyPagingItems<UiTimeline>>).data.isLoading

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
