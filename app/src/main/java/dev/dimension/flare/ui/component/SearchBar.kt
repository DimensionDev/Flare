package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import dev.dimension.flare.R
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiSearchHistory
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.SearchHistoryPresenter
import dev.dimension.flare.ui.presenter.home.SearchHistoryState
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.screen.profile.CommonProfileHeader
import dev.dimension.flare.ui.screen.profile.ProfileHeaderLoading
import dev.dimension.flare.ui.theme.screenHorizontalPadding

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchBar(
    state: SearchBarState,
    onAccountClick: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    SearchContent(
        user = state.user,
        query = state.query,
        onQueryChange = {
            state.setQuery(it)
        },
        onSearch = {
            onSearch.invoke(it)
            state.setExpanded(false)
            keyboardController?.hide()
        },
        expanded = state.expanded,
        onExpandedChange = state::setExpanded,
        onBack = {
            state.setExpanded(false)
        },
        onAccountClick = onAccountClick,
        historyState = state.searchHistories,
        modifier = modifier,
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
private fun SearchContent(
    user: UiState<UiUser>?,
    historyState: UiState<ImmutableListWrapper<UiSearchHistory>>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                placeholder = {
                    Text(text = stringResource(R.string.discover_search_placeholder))
                },
                trailingIcon = {
                    user?.onSuccess {
                        IconButton(onClick = {
                            onAccountClick.invoke()
                        }) {
                            AvatarComponent(it.avatarUrl, size = 30.dp)
                        }
                    }
                },
                leadingIcon = {
                    AnimatedContent(expanded) {
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
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        LazyColumn {
            historyState.onSuccess { history ->
                items(history.size) { index ->
                    val item = history[index]
                    ListItem(
                        headlineContent = {
                            Text(text = item.keyword)
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    onQueryChange(item.keyword)
                                    onSearch(item.keyword)
                                },
                    )
                }
            }
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun LazyStaggeredGridScope.searchContent(
    searchUsers: UiState<LazyPagingItemsProxy<UiUser>>,
    searchStatus: UiState<LazyPagingItemsProxy<UiStatus>>,
    statusEvent: StatusEvent,
    toUser: (MicroBlogKey) -> Unit,
) {
    searchUsers.onSuccess { users ->
        if (users.loadState.refresh is LoadState.Loading || users.itemCount > 0) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.search_users))
                    },
                )
            }
        }
        item(
            span = StaggeredGridItemSpan.FullLine,
        ) {
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
                                    modifier =
                                        Modifier
                                            .width(256.dp),
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
                                    userKey = item.userKey,
                                    modifier =
                                        Modifier
                                            .width(256.dp)
                                            .clickable {
                                                toUser(item.userKey)
                                            },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    searchStatus.onSuccess {
        item(
            span = StaggeredGridItemSpan.FullLine,
        ) {
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

@Composable
internal fun searchBarPresenter(
    accountType: AccountType,
    initialQuery: String = "",
): SearchBarState =
    run {
        val accountState =
            remember { UserPresenter(accountType = accountType, userKey = null) }.invoke()
        val searchHistoryState = remember { SearchHistoryPresenter() }.invoke()
        var expanded by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf(initialQuery) }
        LaunchedEffect(Unit) {
            if (initialQuery.isNotEmpty()) {
                searchHistoryState.addSearchHistory(initialQuery)
            }
        }

        object :
            SearchBarState,
            UserState by accountState,
            SearchHistoryState by searchHistoryState {
            override val expanded: Boolean
                get() = expanded

            override val query: String
                get() = query

            override fun setExpanded(value: Boolean) {
                expanded = value
            }

            override fun setQuery(value: String) {
                query = value
            }
        }
    }

internal interface SearchBarState : UserState, SearchHistoryState {
    val expanded: Boolean
    val query: String

    fun setExpanded(value: Boolean)

    fun setQuery(value: String)
}
