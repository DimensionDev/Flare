package dev.dimension.flare.ui.component.status.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusActionGroup
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection

@Composable
internal fun BlueskyStatusComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalAppearanceSettings.current
    CommonStatusComponent(
        modifier =
            Modifier
                .clickable {
                    event.onStatusClick(data, uriHandler)
                }
                .then(modifier),
        statusKey = data.statusKey,
        onMediaClick = { statusKey, index, preview ->
            event.onMediaClick(
                accountKey = data.accountKey,
                statusKey = statusKey,
                index = index,
                preview = preview,
                uriHandler = uriHandler,
            )
        },
        onUserClick = {
            event.onUserClick(accountKey = data.accountKey, userKey = it, uriHandler = uriHandler)
        },
        rawContent = data.content,
        content = data.contentToken,
        contentDirection = data.contentDirection,
        user = data.user,
        medias = data.medias,
        card = data.card,
        humanizedTime = data.humanizedTime,
        expandedTime = data.expandedTime,
        isDetail = isDetail,
        quotedStatus = data.quote,
        onQuotedStatusClick = {
            event.onStatusClick(it as UiStatus.Bluesky, uriHandler)
        },
        headerIcon = data.repostBy?.let { Icons.Default.SyncAlt },
        headerTextId = data.repostBy?.let { R.string.mastodon_item_reblogged_status },
        headerUser = data.repostBy,
        statusActions = {
            StatusFooterComponent(
                data = data,
                event = event,
            )
        },
        swipeLeftIcon = appearanceSettings.bluesky.swipeLeft.icon,
        swipeLeftText = stringResource(id = appearanceSettings.bluesky.swipeLeft.id),
        onSwipeLeft =
            appearanceSettings.bluesky.swipeLeft
                .takeUnless { it == AppearanceSettings.Bluesky.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Bluesky.SwipeActions.NONE -> Unit
                            AppearanceSettings.Bluesky.SwipeActions.REPLY -> event.onReplyClick(data, uriHandler)
                            AppearanceSettings.Bluesky.SwipeActions.REBLOG -> event.onReblogClick(data)
                            AppearanceSettings.Bluesky.SwipeActions.FAVOURITE -> event.onLikeClick(data)
                        }
                    }
                },
        swipeRightIcon = appearanceSettings.bluesky.swipeRight.icon,
        swipeRightText = stringResource(id = appearanceSettings.bluesky.swipeRight.id),
        onSwipeRight =
            appearanceSettings.bluesky.swipeRight
                .takeUnless { it == AppearanceSettings.Bluesky.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Bluesky.SwipeActions.NONE -> Unit
                            AppearanceSettings.Bluesky.SwipeActions.REPLY -> event.onReplyClick(data, uriHandler)
                            AppearanceSettings.Bluesky.SwipeActions.REBLOG -> event.onReblogClick(data)
                            AppearanceSettings.Bluesky.SwipeActions.FAVOURITE -> event.onLikeClick(data)
                        }
                    }
                },
    )
}

@Composable
private fun RowScope.StatusFooterComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
) {
    val uriHandler = LocalUriHandler.current
    StatusActionButton(
        icon = Icons.AutoMirrored.Filled.Reply,
        text = data.matrices.humanizedReplyCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onReplyClick(data, uriHandler)
        },
    )
    StatusActionGroup(
        icon = Icons.Default.SyncAlt,
        text = data.matrices.humanizedRepostCount,
        modifier =
            Modifier
                .weight(1f),
        color =
            if (data.reaction.reposted) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        subMenus = { closeMenu ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(id = R.string.blusky_item_action_repost),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                    )
                },
                onClick = {
                    closeMenu.invoke()
                    event.onReblogClick(data)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(id = R.string.blusky_item_action_quote),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                    )
                },
                onClick = {
                    closeMenu.invoke()
                    event.onQuoteClick(data, uriHandler)
                },
            )
        },
    )
    StatusActionButton(
        icon =
            if (data.reaction.liked) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FavoriteBorder
            },
        text = data.matrices.humanizedLikeCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onLikeClick(data)
        },
        color =
            if (data.reaction.liked) {
                Color.Red
            } else {
                LocalContentColor.current
            },
    )
    StatusActionGroup(
        icon = Icons.Default.MoreHoriz,
        text = null,
        subMenus = { closeMenu ->
            if (!data.isFromMe) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = R.string.blusky_item_action_report),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Report,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onReportClick(data, uriHandler)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = R.string.blusky_item_action_delete),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onDeleteClick(data, uriHandler)
                    },
                )
            }
        },
    )
}

internal interface BlueskyStatusEvent {
    fun onStatusClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    )

    fun onUserClick(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    )

    fun onMediaClick(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        index: Int,
        preview: String?,
        uriHandler: UriHandler,
    )

    fun onReplyClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    )

    fun onReblogClick(data: UiStatus.Bluesky)

    fun onQuoteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    )

    fun onLikeClick(data: UiStatus.Bluesky)

    fun onReportClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    )

    fun onDeleteClick(
        data: UiStatus.Bluesky,
        uriHandler: UriHandler,
    )
}
