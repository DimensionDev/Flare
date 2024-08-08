package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiSearchHistory
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.SearchHistoryPresenter
import dev.dimension.flare.ui.presenter.home.SearchHistoryState
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.theme.screenHorizontalPadding

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
        onDelete = {
            state.deleteSearchHistory(it)
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
private fun SearchContent(
    user: UiState<UiUserV2>?,
    historyState: UiState<ImmutableListWrapper<UiSearchHistory>>,
    onDelete: (UiSearchHistory) -> Unit,
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
                            AvatarComponent(it.avatar, size = 30.dp)
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
        LazyColumn(
            modifier = Modifier.imePadding(),
        ) {
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = {
                            IconButton(onClick = {
                                onDelete.invoke(item)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

internal fun LazyStaggeredGridScope.searchContent(
    searchUsers: PagingState<UiUserV2>,
    searchStatus: PagingState<UiTimeline>,
    toUser: (MicroBlogKey) -> Unit,
) {
    searchUsers
        .onSuccess {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.search_users))
                    },
                )
            }
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                ) {
                    items(itemCount) {
                        val item = get(it)
                        Card(
                            modifier =
                                Modifier
                                    .width(256.dp),
                        ) {
                            if (item == null) {
                                UserPlaceholder(
                                    modifier = Modifier.padding(8.dp),
                                )
                            } else {
                                CommonStatusHeaderComponent(
                                    modifier = Modifier.padding(8.dp),
                                    data = item,
                                    onUserClick = {
                                        toUser(item.key)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }.onLoading {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.search_users))
                    },
                )
            }
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
            status()
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

            override fun deleteSearchHistory(history: UiSearchHistory) {
                searchHistoryState.deleteSearchHistory(history.keyword)
            }
        }
    }

internal interface SearchBarState :
    UserState,
    SearchHistoryState {
    val expanded: Boolean
    val query: String

    fun setExpanded(value: Boolean)

    fun setQuery(value: String)

    fun deleteSearchHistory(history: UiSearchHistory)
}
