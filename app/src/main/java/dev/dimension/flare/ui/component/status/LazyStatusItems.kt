package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.itemKey
import dev.dimension.flare.ui.model.itemType
import dev.dimension.flare.ui.onError
import dev.dimension.flare.ui.onLoading
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.DisabledAlpha

context(LazyListScope, UiState<LazyPagingItems<UiStatus>>)
internal fun status(
    event: MastodonStatusEvent,
) {
    onSuccess { lazyPagingItems ->
        if (lazyPagingItems.loadState.refresh == LoadState.Loading) {
            items(10) {
                Column {
                    StatusPlaceholder(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.alpha(DisabledAlpha)
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
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        is UiStatus.MastodonNotification -> MastodonNotificationComponent(
                            data = item,
                            state = MastodonStatusState(),
                            event = event,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        null -> {
                            StatusPlaceholder(
                                modifier = Modifier.padding(horizontal = 16.dp)
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
        }
    }
    onLoading {
        items(10) {
            Column {
                StatusPlaceholder(
                    modifier = Modifier.padding(horizontal = 16.dp)
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