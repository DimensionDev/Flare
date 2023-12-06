package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.screen.destinations.QuickMenuDialogRouteDestination
import dev.dimension.flare.ui.screen.profile.CommonProfileHeader
import dev.dimension.flare.ui.screen.profile.ProfileHeaderLoading
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.ktor.http.decodeURLQueryComponent
import org.koin.compose.rememberKoinInject

@Destination(
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
fun SearchRoute(
    keyword: String,
    navigator: DestinationsNavigator,
) {
    SearchScreen(
        initialQuery = keyword,
        onBack = { navigator.navigateUp() },
        onAccountClick = {
            navigator.navigate(QuickMenuDialogRouteDestination)
        },
    )
}

@Composable
internal fun SearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
) {
    val state by producePresenter("discoverSearchPresenter") { discoverSearchPresenter(initialQuery.decodeURLQueryComponent()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    SearchContent(
        user = null,
        query = state.query,
        onQueryChange = { state.setQuery(it) },
        onSearch = {
            state.search(it)
            keyboardController?.hide()
            state.setCommited(true)
        },
        active = true,
        onActiveChange = {
            if (!it) {
                onBack.invoke()
            }
        },
        onBack = onBack,
        committed = true,
        searchUsers = state.user,
        searchStatus = state.status,
        statusEvent = state.statusEvent,
        onAccountClick = onAccountClick,
    )
}

@Composable
internal fun DiscoverSearch(
    user: UiState<UiUser>,
    state: DiscoverSearchState,
    onAccountClick: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    BackHandler(enabled = state.isSearching) {
        state.setSearching(false)
        state.setQuery("")
    }

    SearchContent(
        user = user,
        query = state.query,
        onQueryChange = { state.setQuery(it) },
        onSearch = {
            state.search(it)
            keyboardController?.hide()
            state.setCommited(true)
        },
        active = state.isSearching,
        onActiveChange = {
            state.setSearching(it)
            if (!it) {
                state.setCommited(false)
                state.setQuery("")
            }
        },
        onBack = {
            state.setSearching(false)
            state.setQuery("")
        },
        committed = state.commited,
        searchUsers = state.user,
        searchStatus = state.status,
        statusEvent = state.statusEvent,
        onAccountClick = onAccountClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchContent(
    user: UiState<UiUser>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    committed: Boolean,
    searchUsers: UiState<LazyPagingItemsProxy<UiUser>>,
    searchStatus: UiState<LazyPagingItemsProxy<UiStatus>>,
    modifier: Modifier = Modifier,
    statusEvent: StatusEvent = rememberKoinInject(),
) {
    SearchBar(
        modifier = modifier,
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = active,
        onActiveChange = onActiveChange,
        placeholder = {
            Text(text = stringResource(R.string.discover_search_placeholder))
        },
        trailingIcon = {
            IconButton(onClick = {
                onSearch.invoke(query)
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                )
            }
        },
        leadingIcon = {
            AnimatedContent(active) {
                if (it) {
                    IconButton(onClick = {
                        onBack.invoke()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                } else {
                    user?.onSuccess {
                        IconButton(onClick = {
                            onAccountClick.invoke()
                        }) {
                            NetworkImage(
                                model = it.avatarUrl,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (committed) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                searchUsers.onSuccess { users ->
                    if (users.loadState.refresh is LoadState.Loading || users.itemCount > 0) {
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.search_users))
                                },
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                        ) {
                            users.onLoading {
                                items(10) {
                                    ProfileHeaderLoading(
                                        modifier = Modifier.width(256.dp),
                                    )
                                }
                            }.onSuccess {
                                items(users.itemCount) {
                                    val item = users[it]
                                    Card {
                                        if (item == null) {
                                            ProfileHeaderLoading(
                                                modifier = Modifier.fillParentMaxWidth(0.8f),
                                            )
                                        } else {
                                            CommonProfileHeader(
                                                bannerUrl = item.bannerUrl,
                                                avatarUrl = item.avatarUrl,
                                                displayName = item.nameElement,
                                                handle = item.handle,
                                                content = {
                                                    item.descriptionElement?.let {
                                                        HtmlText2(
                                                            element = it,
                                                            maxLines = 2,
                                                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillParentMaxWidth(0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                searchStatus.onSuccess {
                    stickyHeader {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.search_status))
                            },
                        )
                    }
                    with(searchStatus) {
                        with(statusEvent) {
                            status()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun discoverSearchPresenter(
    initialSearch: String? = null,
    statusEvent: StatusEvent = rememberKoinInject(),
): DiscoverSearchState =
    run {
        var query by remember { mutableStateOf(initialSearch ?: "") }
        var isSearching by remember { mutableStateOf(false) }
        var commited by remember { mutableStateOf(initialSearch != null) }
        val state =
            remember {
                SearchPresenter(initialSearch ?: "")
            }.invoke()

        object : DiscoverSearchState, SearchState by state {
            override val commited = commited
            override val query = query
            override val isSearching = isSearching
            override val statusEvent = statusEvent

            override fun setSearching(new: Boolean) {
                isSearching = new
            }

            override fun setQuery(new: String) {
                query = new
            }

            override fun setCommited(new: Boolean) {
                commited = new
            }
        }
    }

internal interface DiscoverSearchState : SearchState {
    val commited: Boolean
    val isSearching: Boolean
    val statusEvent: StatusEvent
    val query: String

    fun setSearching(new: Boolean)

    fun setQuery(new: String)

    fun setCommited(new: Boolean)
}
