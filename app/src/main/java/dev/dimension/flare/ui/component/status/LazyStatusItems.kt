package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import com.ramcosta.composedestinations.generated.destinations.BlueskyReportStatusRouteDestination
import com.ramcosta.composedestinations.generated.destinations.DeleteStatusConfirmRouteDestination
import com.ramcosta.composedestinations.generated.destinations.MastodonReportRouteDestination
import com.ramcosta.composedestinations.generated.destinations.MisskeyReactionRouteDestination
import com.ramcosta.composedestinations.generated.destinations.MisskeyReportRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileRouteDestination
import com.ramcosta.composedestinations.generated.destinations.QuoteDestination
import com.ramcosta.composedestinations.generated.destinations.ReplyRouteDestination
import com.ramcosta.composedestinations.generated.destinations.StatusMediaRouteDestination
import com.ramcosta.composedestinations.generated.destinations.StatusRouteDestination
import dev.dimension.flare.R
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.bluesky.BlueskyNotificationComponent
import dev.dimension.flare.ui.component.status.bluesky.BlueskyStatusComponent
import dev.dimension.flare.ui.component.status.bluesky.BlueskyStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.MastodonNotificationComponent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusComponent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.StatusPlaceholder
import dev.dimension.flare.ui.component.status.misskey.MisskeyNotificationComponent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusComponent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusEvent
import dev.dimension.flare.ui.component.status.xqt.XQTStatusComponent
import dev.dimension.flare.ui.component.status.xqt.XQTStatusEvent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.DisabledAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

context(LazyStaggeredGridScope, UiState<LazyPagingItemsProxy<UiStatus>>, StatusEvent)
internal fun status(detailStatusKey: MicroBlogKey? = null) {
    onSuccess { lazyPagingItems ->
        if (lazyPagingItems.itemCount > 0) {
            with(lazyPagingItems) {
                statusItems(detailStatusKey = detailStatusKey)
            }
            when (lazyPagingItems.loadState.append) {
                is LoadState.Error ->
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column(
                            modifier =
                                Modifier
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
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column(
                            modifier =
                                Modifier
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
        } else if (
            lazyPagingItems.loadState.refresh == LoadState.Loading ||
            lazyPagingItems.loadState.prepend == LoadState.Loading ||
            lazyPagingItems.loadState.append == LoadState.Loading
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
        } else if (
            lazyPagingItems.loadState.refresh is LoadState.Error ||
            lazyPagingItems.loadState.prepend is LoadState.Error
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
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
        } else {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
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

context(LazyStaggeredGridScope, LazyPagingItemsProxy<UiStatus>, StatusEvent)
private fun statusItems(detailStatusKey: MicroBlogKey? = null) {
    items(
        itemCount,
        key =
            itemKey {
                it.itemKey
            },
        contentType =
            itemContentType {
                it.itemType
            },
    ) {
        Column {
            val item = get(it)
            StatusItem(item, this@StatusEvent, isDetail = item?.statusKey == detailStatusKey)
            if (it != itemCount - 1) {
                HorizontalDivider(
                    modifier = Modifier.alpha(DisabledAlpha),
                )
            }
        }
    }
}

@Composable
internal fun StatusItem(
    item: UiStatus?,
    event: StatusEvent,
    horizontalPadding: Dp = screenHorizontalPadding,
    isDetail: Boolean = false,
) {
    when (item) {
        is UiStatus.Mastodon ->
            MastodonStatusComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                isDetail = isDetail,
            )

        is UiStatus.MastodonNotification ->
            MastodonNotificationComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )

        null -> {
            StatusPlaceholder(
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        is UiStatus.Misskey ->
            MisskeyStatusComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                isDetail = isDetail,
            )

        is UiStatus.MisskeyNotification ->
            MisskeyNotificationComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )

        is UiStatus.Bluesky ->
            BlueskyStatusComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                isDetail = isDetail,
            )

        is UiStatus.BlueskyNotification ->
            BlueskyNotificationComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            )

        is UiStatus.XQT ->
            XQTStatusComponent(
                data = item,
                event = event,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                isDetail = isDetail,
            )
    }
}

internal sealed interface StatusEvent :
    MastodonStatusEvent,
    MisskeyStatusEvent,
    BlueskyStatusEvent,
    XQTStatusEvent

internal class DefaultStatusEvent(
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
) : StatusEvent {
    override fun onUserClick(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            ProfileRouteDestination(
                accountType = AccountType.Specific(accountKey),
                userKey = userKey,
            ).deeplink(),
        )
    }

    override fun onStatusClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            StatusRouteDestination(
                status.statusKey,
                accountType = AccountType.Specific(status.accountKey),
            ).deeplink(),
        )
    }

    override fun onReplyClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            ReplyRouteDestination(
                accountType = AccountType.Specific(status.accountKey),
                status.statusKey,
            ).deeplink(),
        )
    }

    override fun onReblogClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.reblog(status)
        }
    }

    override fun onLikeClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.like(status)
        }
    }

    override fun onBookmarkClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.bookmark(status)
        }
    }

    override fun onBookmarkClick(data: UiStatus.XQT) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.XQT ?: return@launch
            account.dataSource.bookmark(data)
        }
    }

    override fun onMediaClick(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        index: Int,
        preview: String?,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            StatusMediaRouteDestination(
                accountType = AccountType.Specific(accountKey),
                statusKey = statusKey,
                index = index,
                preview = preview,
            ).deeplink(),
        )
    }

    override fun onDeleteClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            DeleteStatusConfirmRouteDestination(
                accountType = AccountType.Specific(status.accountKey),
                status.statusKey,
            ).deeplink(),
        )
    }

    override fun onReportClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            MastodonReportRouteDestination(
                userKey = status.user.userKey,
                statusKey = status.statusKey,
                accountType = AccountType.Specific(status.accountKey),
            ).deeplink(),
        )
    }

    override fun onStatusClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            StatusRouteDestination(statusKey = data.statusKey, accountType = AccountType.Specific(data.accountKey))
                .deeplink(),
        )
    }

    override fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    ) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Misskey ?: return@launch
            runCatching {
                account.dataSource.react(data, reaction.name)
            }.onFailure {
            }
        }
    }

    override fun onReplyClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            ReplyRouteDestination(accountType = AccountType.Specific(data.accountKey), replyTo = data.statusKey)
                .deeplink(),
        )
    }

    override fun onReblogClick(data: UiStatus.Misskey) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Misskey ?: return@launch
            runCatching {
                account.dataSource.renote(data)
            }.onFailure {
            }
        }
    }

    override fun onQuoteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            QuoteDestination(
                accountType = AccountType.Specific(data.accountKey),
                quoted = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onAddReactionClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            MisskeyReactionRouteDestination(
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }

    override fun onDeleteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            DeleteStatusConfirmRouteDestination(
                accountType = AccountType.Specific(data.accountKey),
                statusKey = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onReportClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            MisskeyReportRouteDestination(
                userKey = data.user.userKey,
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }

    override fun onStatusClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            StatusRouteDestination(
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }

    override fun onReplyClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            ReplyRouteDestination(
                accountType = AccountType.Specific(data.accountKey),
                replyTo = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onReplyClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            ReplyRouteDestination(
                accountType = AccountType.Specific(data.accountKey),
                replyTo = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onReblogClick(data: UiStatus.Bluesky) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Bluesky ?: return@launch
            account.dataSource.reblog(data)
        }
    }

    override fun onReblogClick(data: UiStatus.XQT) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.XQT ?: return@launch
            account.dataSource.retweet(data)
        }
    }

    override fun onLikeClick(data: UiStatus.Bluesky) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Bluesky ?: return@launch
            account.dataSource.like(data)
        }
    }

    override fun onLikeClick(data: UiStatus.XQT) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.XQT ?: return@launch
            account.dataSource.like(data)
        }
    }

    override fun onReportClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            BlueskyReportStatusRouteDestination(
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }

    override fun onReportClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            BlueskyReportStatusRouteDestination(
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }

    override fun onDeleteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            DeleteStatusConfirmRouteDestination(
                accountType = AccountType.Specific(data.accountKey),
                statusKey = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onDeleteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            DeleteStatusConfirmRouteDestination(
                accountType = AccountType.Specific(data.accountKey),
                statusKey = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onQuoteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            QuoteDestination(
                accountType = AccountType.Specific(data.accountKey),
                quoted = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onQuoteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            QuoteDestination(
                accountType = AccountType.Specific(data.accountKey),
                quoted = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onStatusClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            StatusRouteDestination(
                statusKey = data.statusKey,
                accountType = AccountType.Specific(data.accountKey),
            ).deeplink(),
        )
    }
}

internal data object EmptyStatusEvent : StatusEvent {
    override fun onStatusClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) = Unit

    override fun onStatusClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    ) = Unit

    override fun onReplyClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReblogClick(data: UiStatus.Misskey) = Unit

    override fun onQuoteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onAddReactionClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onDeleteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReportClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onStatusClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReplyClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReblogClick(status: UiStatus.Mastodon) = Unit

    override fun onLikeClick(status: UiStatus.Mastodon) = Unit

    override fun onBookmarkClick(status: UiStatus.Mastodon) = Unit

    override fun onBookmarkClick(data: UiStatus.XQT) = Unit

    override fun onMediaClick(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        index: Int,
        preview: String?,
        uriHandler: UriHandler,
    ) = Unit

    override fun onUserClick(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    ) = Unit

    override fun onDeleteClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReportClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReplyClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReplyClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReblogClick(data: UiStatus.Bluesky) = Unit

    override fun onReblogClick(data: UiStatus.XQT) = Unit

    override fun onQuoteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) = Unit

    override fun onLikeClick(data: UiStatus.Bluesky) = Unit

    override fun onLikeClick(data: UiStatus.XQT) = Unit

    override fun onReportClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) = Unit

    override fun onReportClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) = Unit

    override fun onDeleteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    ) = Unit

    override fun onDeleteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) = Unit

    override fun onQuoteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) = Unit

    override fun onStatusClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    ) = Unit
}
