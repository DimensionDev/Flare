package dev.dimension.flare.ui.component.status.xqt

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Report
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Retweet
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusActionGroup
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun XQTStatusComponent(
    data: UiStatus.XQT,
    event: XQTStatusEvent,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val actualData = data.retweet ?: data
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalAppearanceSettings.current
    CommonStatusComponent(
        modifier =
            Modifier
                .let {
                    if (isDetail) {
                        it
                    } else {
                        it.clickable {
                            event.onStatusClick(data, uriHandler)
                        }
                    }
                }
                .then(modifier),
        statusKey = actualData.statusKey,
        onMediaClick = { statusKey, index, preview ->
            event.onMediaClick(
                accountKey = actualData.accountKey,
                statusKey = statusKey,
                index = index,
                preview = preview,
                uriHandler = uriHandler,
            )
        },
        onUserClick = {
            event.onUserClick(
                accountKey = actualData.accountKey,
                userKey = it,
                uriHandler = uriHandler,
            )
        },
        rawContent = actualData.content,
        content = actualData.contentToken,
        contentDirection = actualData.contentDirection,
        user = actualData.user,
        medias = actualData.medias,
        card = actualData.card,
        humanizedTime = actualData.humanizedTime,
        expandedTime = data.expandedTime,
        isDetail = isDetail,
        poll = actualData.poll,
        headerIcon = data.retweet?.let { FontAwesomeIcons.Solid.Retweet },
        headerTextId = data.retweet?.let { R.string.mastodon_item_reblogged_status },
        headerUser = data.retweet?.let { data.user },
        replyHandle = actualData.replyHandle,
        quotedStatus = actualData.quote,
        onQuotedStatusClick = {
            event.onStatusClick(it as UiStatus.XQT, uriHandler)
        },
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
                            AppearanceSettings.XQT.SwipeActions.REPLY ->
                                event.onReplyClick(
                                    actualData,
                                    uriHandler,
                                )

                            AppearanceSettings.XQT.SwipeActions.REBLOG -> event.onReblogClick(data)
                            AppearanceSettings.XQT.SwipeActions.FAVOURITE -> event.onLikeClick(data)
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
                            AppearanceSettings.XQT.SwipeActions.REPLY ->
                                event.onReplyClick(
                                    actualData,
                                    uriHandler,
                                )

                            AppearanceSettings.XQT.SwipeActions.REBLOG -> event.onReblogClick(data)
                            AppearanceSettings.XQT.SwipeActions.FAVOURITE -> event.onLikeClick(data)
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
        enabled = actualData.canRetweet,
        icon = FontAwesomeIcons.Solid.Retweet,
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
                        imageVector = FontAwesomeIcons.Solid.Retweet,
                        contentDescription = stringResource(id = R.string.blusky_item_action_repost),
                        modifier = Modifier.size(24.dp),
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
                        contentDescription = stringResource(id = R.string.blusky_item_action_quote),
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
            event.onLikeClick(data)
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
                            contentDescription = stringResource(id = R.string.mastodon_item_unbookmark),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(id = R.string.mastodon_item_bookmark),
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
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.mastodon_item_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.mastodon_item_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onDeleteClick(actualData, uriHandler)
                    },
                )
            } else {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Report,
                            contentDescription = stringResource(id = R.string.mastodon_item_report),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.mastodon_item_report),
                            color = MaterialTheme.colorScheme.error,
                        )
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

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun XQTNofiticationComponent(
    data: UiStatus.XQTNotification,
    event: XQTStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    if (data.users.isEmpty() && data.data != null) {
        data.data?.let {
            XQTStatusComponent(data = it, event = event, modifier = modifier)
        }
    } else {
        Row(
            modifier =
                Modifier
                    .let {
                        data.data?.let { data ->
                            it.clickable {
                                event.onStatusClick(data, uriHandler)
                            }
                        } ?: if (data.users.isEmpty()) {
                            it.clickable {
                                uriHandler.openUri(data.url)
                            }
                        } else {
                            it
                        }
                    }
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .then(modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector =
                    when (data.type) {
                        UiStatus.XQTNotification.Type.Follow -> Icons.Default.PersonAdd
                        UiStatus.XQTNotification.Type.Like -> Icons.Default.Favorite
                        UiStatus.XQTNotification.Type.Recommendation -> Icons.Default.Recommend
                        UiStatus.XQTNotification.Type.Logo -> Icons.Default.Info
                    },
                contentDescription = null,
                modifier =
                    Modifier
                        .size(40.dp),
                tint =
                    when (data.type) {
                        UiStatus.XQTNotification.Type.Follow -> MaterialTheme.colorScheme.primary
                        UiStatus.XQTNotification.Type.Like -> Color.Red
                        UiStatus.XQTNotification.Type.Recommendation -> MaterialTheme.colorScheme.primary
                        UiStatus.XQTNotification.Type.Logo -> MaterialTheme.colorScheme.secondary
                    },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (data.users.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(data.users) { user ->
                            AvatarComponent(
                                data = user.avatarUrl,
                                size = 40.dp,
                                modifier =
                                    Modifier.clickable {
                                        event.onUserClick(
                                            accountKey = data.accountKey,
                                            userKey = user.userKey,
                                            uriHandler = uriHandler,
                                        )
                                    },
                            )
                        }
                    }
                }
                Text(text = data.text)
                if (data.data != null) {
                    data.data?.let {
                        Text(
                            text = it.content,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal interface XQTStatusEvent {
    fun onMediaClick(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        index: Int,
        preview: String?,
        uriHandler: UriHandler,
    )

    fun onUserClick(
        accountKey: MicroBlogKey,
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

    fun onStatusClick(
        data: UiStatus.XQT,
        uriHandler: UriHandler,
    )
}
