package dev.dimension.flare.ui.controllers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.ScrollToTopHandler
import dev.dimension.flare.ui.component.dm.DMItem
import dev.dimension.flare.ui.presenter.dm.DMConversationState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import platform.UIKit.UIViewController

@Suppress("FunctionName")
public fun DMConversationController(state: ComposeUIStateProxy<DMConversationState>): UIViewController =
    FlareComposeUIViewController(
        state = state,
    ) { state ->
        val listState = rememberLazyListState()
        ScrollToTopHandler(listState, reverseLayout = true)
        state.items.onSuccess {
            if (listState.firstVisibleItemIndex == 0) {
                LaunchedEffect(itemCount) {
                    listState.scrollToItem(0)
                }
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier =
                Modifier
                    .fillMaxSize(),
//                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(
                state.items,
                key = {
                    get(it)?.id ?: it
                },
//                emptyContent = {
//
//                },
//                errorContent = {
//
//                },
//                loadingContent = {
//
//                },
                itemContent = { item ->
                    DMItem(
                        item = item,
                        onRetry = {
                            state.retry(item.key)
                        },
                        modifier =
                            Modifier
                                .animateItem()
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                ),
                        onUserClicked = {
//                        uriHandler.openUri(
//                            AppDeepLink.Profile(state.items)
//                        )
//                        toProfile.invoke(it.key)
                        },
                    )
                },
            )
        }
    }
