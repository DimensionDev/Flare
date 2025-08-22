package dev.dimension.flare.ui.screen.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.feeds_discover_feeds_title
import dev.dimension.flare.feeds_my_feeds_title
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.UiListItem
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.SubtleButton
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeedListScreen(
    accountType: AccountType,
    toFeed: (UiList) -> Unit,
) {
    val state by producePresenter("FeedListScreen_$accountType") {
        presenter(accountType)
    }
    val listState = rememberLazyListState()
    val scrollbarAdapter = rememberScrollbarAdapter(listState)
    RegisterTabCallback(listState, onRefresh = state::refresh)

    Box {
        ScrollbarContainer(
            adapter = scrollbarAdapter,
        ) {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        vertical = 8.dp,
                    ) + LocalWindowPadding.current,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Header(stringResource(Res.string.feeds_my_feeds_title))
                }
                uiListItemComponent(
                    state.myFeeds,
                    onClicked = toFeed,
                )

                item {
                    Header(stringResource(Res.string.feeds_discover_feeds_title))
                }
                items(
                    state.popularFeeds,
                    loadingContent = {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusPlaceholder(
                                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    },
                ) { (item, subscribed) ->
                    UiListItem(
                        item = item,
                        onClicked = toFeed,
                        trailingContent = {
                            SubtleButton(
                                onClick = {
                                    if (subscribed) {
                                        state.unsubscribe(item)
                                    } else {
                                        state.subscribe(item)
                                    }
                                },
                            ) {
                                if (subscribed) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Trash,
                                        contentDescription = null,
                                    )
                                } else {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Plus,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        if (state.myFeeds.isRefreshing || state.popularFeeds.isRefreshing) {
            ProgressBar(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType) {
                BlueskyFeedsPresenter(accountType)
            }.invoke()

        object : BlueskyFeedsState by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
