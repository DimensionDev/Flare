package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.dimension.flare.R
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.onError
import dev.dimension.flare.ui.onLoading
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.DisabledAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding

context(LazyListScope, UiState<LazyPagingItems<UiStatus>>)
internal fun status(
    event: MastodonStatusEvent
) {
    onSuccess { lazyPagingItems ->
        if ((
            lazyPagingItems.loadState.refresh == LoadState.Loading ||
                lazyPagingItems.loadState.prepend == LoadState.Loading
            ) &&
            lazyPagingItems.itemCount == 0
        ) {
            items(10) {
                Column {
                    StatusPlaceholder(
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.alpha(DisabledAlpha)
                    )
                }
            }
        } else if ((
            lazyPagingItems.loadState.refresh is LoadState.Error ||
                lazyPagingItems.loadState.prepend is LoadState.Error
            ) &&
            lazyPagingItems.itemCount == 0
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .clickable {
                            lazyPagingItems.retry()
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MoodBad,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.status_loadmore_error_retry),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey {
                    it.itemKey
                },
                contentType = lazyPagingItems.itemContentType {
                    it.itemType
                }
            ) {
                Column {
                    when (val item = lazyPagingItems[it]) {
                        is UiStatus.Mastodon -> MastodonStatusComponent(
                            data = item,
                            state = MastodonStatusState(),
                            event = event,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                        )

                        is UiStatus.MastodonNotification -> MastodonNotificationComponent(
                            data = item,
                            state = MastodonStatusState(),
                            event = event,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                        )

                        null -> {
                            StatusPlaceholder(
                                modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    // draw divider
                    if (it != lazyPagingItems.itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.alpha(DisabledAlpha)
                        )
                    }
                }
            }
            if (lazyPagingItems.itemCount > 0) {
                when (lazyPagingItems.loadState.append) {
                    is LoadState.Error ->
                        item {
                            Column(
                                modifier = Modifier
                                    .clickable {
                                        lazyPagingItems.retry()
                                    }
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.status_loadmore_error)
                                )
                                Text(text = stringResource(id = R.string.status_loadmore_error_retry))
                            }
                        }

                    LoadState.Loading ->
                        items(10) {
                            Column {
                                StatusPlaceholder(
                                    modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.alpha(DisabledAlpha)
                                )
                            }
                        }

                    is LoadState.NotLoading ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.status_loadmore_end)
                                )
                            }
                        }
                }
            }
        }
    }
    onLoading {
        items(10) {
            Column {
                StatusPlaceholder(
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding)
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.alpha(DisabledAlpha)
                )
            }
        }
    }
    onError {
    }
}
