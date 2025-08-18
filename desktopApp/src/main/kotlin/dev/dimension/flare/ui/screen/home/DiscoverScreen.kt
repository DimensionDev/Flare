package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.ListItem
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverScreen(accountType: AccountType) {
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
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (true) {
            state.users
                .onSuccess {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        ListItem(
                            text = {
                                Text(text = "Users")
                            },
                            onClick = {},
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
                                            onUserClick = {},
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
                        ListItem(
                            text = {
                                Text(text = "Users")
                            },
                            onClick = {},
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
                    ListItem(
                        text = {
                            Text(text = "Hashtags")
                        },
                        onClick = {},
                    )
                }
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
//                    val maxItemsInEachRow =
//                        when (windowInfo.windowSizeClass.windowWidthSizeClass) {
//                            WindowWidthSizeClass.COMPACT -> {
//                                2
//                            }
//
//                            WindowWidthSizeClass.MEDIUM -> {
//                                4
//                            }
//
//                            else -> {
//                                8
//                            }
//                        }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
//                        maxItemsInEachRow = maxItemsInEachRow,
                    ) {
                        repeat(
                            itemCount,
                        ) {
                            val hashtag = get(it)
                            Card(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hashtag?.searchContent?.let { it1 ->
//                                        state.commitSearch(
//                                            it1,
//                                        )
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
                            text = {
                                Text(text = "status")
                            },
                            onClick = {},
                        )
                    }
                    status(state.status)
                }.onLoading {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        ListItem(
                            text = {
                                Text(text = "status")
                            },
                            onClick = {},
                        )
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

        object : DiscoverState by state {
            fun refresh() {
            }
        }
    }
