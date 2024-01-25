package dev.dimension.flare.ui.component.status.xqt

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
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
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection

@Composable
internal fun XQTStatusComponent(
    data: UiStatus.XQT,
    event: XQTStatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData = data.retweet ?: data
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalAppearanceSettings.current
    CommonStatusComponent(
        modifier = modifier,
        onMediaClick = {
            event.onMediaClick(it, uriHandler)
        },
        onUserClick = {
            event.onUserClick(it, uriHandler)
        },
        rawContent = actualData.content,
        content = actualData.contentToken,
        contentDirection = actualData.contentDirection,
        user = actualData.user,
        medias = actualData.medias,
        card = actualData.card,
        humanizedTime = actualData.humanizedTime,
        poll = actualData.poll,
        headerIcon = data.retweet?.let { Icons.Default.SyncAlt },
        headerTextId = data.retweet?.let { R.string.mastodon_item_reblogged_status },
        headerUser = data.retweet?.let { data.user },
        quotedStatus = actualData.quote,
        statusActions = {
            StatusFooterComponent(
                data = data,
                event = event,
            )
        },
        swipeLeftIcon = appearanceSettings.xqt.swipeLeft.icon,
        swipeLeftText = stringResource(appearanceSettings.xqt.swipeLeft.id),
        onSwipeLeft =
            appearanceSettings.xqt.swipeLeft
                .takeUnless { it == AppearanceSettings.XQT.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.XQT.SwipeActions.NONE -> Unit
                            AppearanceSettings.XQT.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.XQT.SwipeActions.REBLOG -> event.onReblogClick(actualData)
                            AppearanceSettings.XQT.SwipeActions.FAVOURITE -> event.onLikeClick(actualData)
                        }
                    }
                },
        swipeRightIcon = appearanceSettings.xqt.swipeRight.icon,
        swipeRightText = stringResource(appearanceSettings.xqt.swipeRight.id),
        onSwipeRight =
            appearanceSettings.xqt.swipeRight
                .takeUnless { it == AppearanceSettings.XQT.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.XQT.SwipeActions.NONE -> Unit
                            AppearanceSettings.XQT.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.XQT.SwipeActions.REBLOG -> event.onReblogClick(actualData)
                            AppearanceSettings.XQT.SwipeActions.FAVOURITE -> event.onLikeClick(actualData)
                        }
                    }
                },
    )
}

@Composable
private fun RowScope.StatusFooterComponent(
    data: UiStatus.XQT,
    event: XQTStatusEvent,
) {
    val actualData = data.retweet ?: data
    val uriHandler = LocalUriHandler.current
    StatusActionButton(
        icon = Icons.AutoMirrored.Filled.Reply,
        text = actualData.matrices.humanizedReplyCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onReplyClick(actualData, uriHandler)
        },
    )
    StatusActionGroup(
        enabled = data.canRetweet,
        icon = Icons.Default.SyncAlt,
        text = actualData.matrices.humanizedRetweetCount,
        modifier =
            Modifier
                .weight(1f),
        color =
            if (actualData.reaction.retweeted) {
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
                    event.onReblogClick(actualData)
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
                    event.onQuoteClick(actualData, uriHandler)
                },
            )
        },
    )
    StatusActionButton(
        icon =
            if (actualData.reaction.liked) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FavoriteBorder
            },
        text = actualData.matrices.humanizedLikeCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onLikeClick(actualData)
        },
        color =
            if (actualData.reaction.liked) {
                Color.Red
            } else {
                LocalContentColor.current
            },
    )
    StatusActionGroup(
        icon = Icons.Default.MoreHoriz,
        text = null,
        subMenus = { closeMenu ->
            DropdownMenuItem(
                text = {
                    if (actualData.reaction.bookmarked) {
                        Text(text = stringResource(id = R.string.mastodon_item_unbookmark))
                    } else {
                        Text(text = stringResource(id = R.string.mastodon_item_bookmark))
                    }
                },
                leadingIcon = {
                    if (actualData.reaction.bookmarked) {
                        Icon(
                            imageVector = Icons.Default.BookmarkRemove,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = null,
                        )
                    }
                },
                onClick = {
                    closeMenu.invoke()
                    event.onBookmarkClick(actualData)
                },
            )

            if (actualData.isFromMe) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.mastodon_item_delete))
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onDeleteClick(actualData, uriHandler)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.mastodon_item_report))
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onReportClick(actualData, uriHandler)
                    },
                )
            }
        },
    )
}

internal interface XQTStatusEvent {
    fun onMediaClick(
        media: UiMedia,
        uriHandler: UriHandler,
    )

    fun onUserClick(
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    )

    fun onReplyClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    )

    fun onReblogClick(data: UiStatus.XQT)

    fun onLikeClick(data: UiStatus.XQT)

    fun onBookmarkClick(data: UiStatus.XQT)

    fun onDeleteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    )

    fun onQuoteClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    )
}
