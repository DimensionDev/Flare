package dev.dimension.flare.ui.screen.home

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.delete
import dev.dimension.flare.hashtags
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.statues
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchHistoryPresenter
import dev.dimension.flare.ui.presenter.home.SearchHistoryState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.users
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.AutoSuggestBoxDefaults
import io.github.composefluent.component.AutoSuggestionBox
import io.github.composefluent.component.ListItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import io.github.composefluent.surface.Card
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class, ExperimentalFluentApi::class)
@Composable
internal fun DiscoverScreen(
    accountType: AccountType,
    toUser: (MicroBlogKey) -> Unit,
    toSearch: (String) -> Unit,
) {
    val state by producePresenter(
        key = "discover_$accountType",
    ) {
        presenter(accountType)
    }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = lazyListState, onRefresh = state::refresh)

    LazyStatusVerticalStaggeredGrid(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(vertical = 8.dp) + LocalWindowPadding.current,
    ) {
        if (true) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    AutoSuggestionBox(
                        expanded = state.isHistoryExpanded,
                        onExpandedChange = state::setHistoryExpanded,
                    ) {
                        TextField(
                            state = state.textState,
                            shape = AutoSuggestBoxDefaults.textFieldShape(state.isHistoryExpanded),
                            modifier = Modifier.widthIn(300.dp).flyoutAnchor(),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            onKeyboardAction = {
                                if (state.textState.text.isNotBlank()) {
                                    toSearch(state.textState.text.toString())
                                }
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                        )
                        state.searchHistories.onSuccess { history ->
                            val searchResult by remember(history) {
                                snapshotFlow { state.textState.text }.map {
                                    history.toImmutableList().filter { item ->
                                        item.keyword.contains(
                                            it,
                                            ignoreCase = true,
                                        )
                                    }
                                }
                            }.collectAsState(emptyList())

                            AutoSuggestBoxDefaults.suggestFlyout(
                                expanded = state.isHistoryExpanded,
                                onDismissRequest = { state.setHistoryExpanded(false) },
                                itemsContent = {
                                    items(searchResult) {
                                        ListItem(
                                            onClick = {
                                                toSearch(it.keyword)
                                                state.setHistoryExpanded(false)
                                            },
                                            text = { Text(it.keyword, maxLines = 1) },
                                            modifier = Modifier.fillMaxWidth(),
                                            trailing = {
                                                SubtleButton(
                                                    onClick = {
                                                        state.deleteSearchHistory(it.keyword)
                                                    },
                                                ) {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.Trash,
                                                        contentDescription = stringResource(Res.string.delete),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.flyoutSize(matchAnchorWidth = true),
                            )
                        }
                    }
                }
            }
            state.users
                .onSuccess {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Header(stringResource(Res.string.users))
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
                            items(
                                itemCount,
                            ) {
                                val user = get(it)
                                Card(
                                    modifier =
                                        Modifier
                                            .width(256.dp),
                                    onClick = {
                                    },
                                ) {
                                    if (user != null) {
                                        CommonStatusHeaderComponent(
                                            data = user,
                                            onUserClick = toUser,
                                            modifier = Modifier.padding(8.dp),
                                        )
                                    } else {
                                        UserPlaceholder(
                                            modifier = Modifier.padding(8.dp),
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
                        Header(stringResource(Res.string.users))
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
            state.hashtags.onSuccess {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Header(stringResource(Res.string.hashtags))
                }
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    ) {
                        repeat(
                            itemCount,
                        ) {
                            val hashtag = get(it)
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hashtag?.searchContent?.let { it1 ->
                                        toSearch(it1)
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
                        Header(stringResource(Res.string.statues))
                    }
                    status(state.status)
                }.onLoading {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Header(stringResource(Res.string.statues))
                    }
                    status(state.status)
                }
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val state = remember(accountType) { DiscoverPresenter(accountType = accountType) }.invoke()
        val scope = rememberCoroutineScope()

        val textState = rememberTextFieldState()
        val searchHistory =
            remember {
                SearchHistoryPresenter()
            }.invoke()
        var isHistoryExpanded by remember { mutableStateOf(false) }
        object : DiscoverState by state, SearchHistoryState by searchHistory {
            val textState = textState

            val isHistoryExpanded = isHistoryExpanded

            fun setHistoryExpanded(expanded: Boolean) {
                isHistoryExpanded = expanded
            }

            fun refresh() {
                scope.launch {
                    state.users.refreshSuspend()
                    state.hashtags.refreshSuspend()
                    state.status.refreshSuspend()
                }
            }
        }
    }
