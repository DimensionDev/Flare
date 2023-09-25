package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.moriatsushi.koject.Provides
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.status.mastodon.MastodonNotificationComponent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusComponent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.StatusPlaceholder
import dev.dimension.flare.ui.component.status.misskey.MisskeyNotificationComponent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusComponent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusEvent
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.DisabledAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding

context(LazyListScope, UiState<LazyPagingItems<UiStatus>>, StatusEvent)
internal fun status() {
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
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.alpha(DisabledAlpha),
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.MoodBad,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.status_loadmore_error_retry),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else if (lazyPagingItems.itemCount == 0) {
            item {
                Column(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .clickable {
                            lazyPagingItems.refresh()
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.status_empty),
                        modifier = Modifier.padding(16.dp),
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
                },
            ) {
                Column {
                    when (val item = lazyPagingItems[it]) {
                        is UiStatus.Mastodon -> MastodonStatusComponent(
                            data = item,
                            event = mastodonStatusEvent,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                        )

                        is UiStatus.MastodonNotification -> MastodonNotificationComponent(
                            data = item,
                            event = mastodonStatusEvent,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                        )

                        null -> {
                            StatusPlaceholder(
                                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        is UiStatus.Misskey -> MisskeyStatusComponent(
                            data = item,
                            event = misskeyStatusEvent,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                        )

                        is UiStatus.MisskeyNotification -> MisskeyNotificationComponent(
                            data = item,
                            event = misskeyStatusEvent,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                        )

//                        is UiStatus.Bluesky -> BlueskyStatusComponent(
//                            data = item,
//                            event = blueskyStatusEvent,
//                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
//                        )
//                        is UiStatus.BlueskyNotification -> BlueskyNotificationComponent(
//                            data = item,
//                            event = blueskyStatusEvent,
//                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
//                        )
                    }
                    // draw divider
                    if (it != lazyPagingItems.itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.alpha(DisabledAlpha),
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
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.status_loadmore_error),
                                )
                                Text(text = stringResource(id = R.string.status_loadmore_error_retry))
                            }
                        }

                    LoadState.Loading ->
                        items(10) {
                            Column {
                                StatusPlaceholder(
                                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.alpha(DisabledAlpha),
                                )
                            }
                        }

                    is LoadState.NotLoading ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.status_loadmore_end),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
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
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.alpha(DisabledAlpha),
                )
            }
        }
    }
    onError {
    }
}

@Provides
internal data class StatusEvent(
    val mastodonStatusEvent: MastodonStatusEvent,
    val misskeyStatusEvent: MisskeyStatusEvent,
//    val blueskyStatusEvent: BlueskyStatusEvent,
)
