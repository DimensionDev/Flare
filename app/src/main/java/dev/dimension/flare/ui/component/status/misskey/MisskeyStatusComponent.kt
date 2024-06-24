package dev.dimension.flare.ui.component.status.misskey

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Retweet
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusActionGroup
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.model.localizedFullTime
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.theme.MediumAlpha

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MisskeyStatusComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val currentData by rememberUpdatedState(data)
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalAppearanceSettings.current
    val actualData = currentData.renote ?: currentData
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
                }.then(modifier),
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
            event.onUserClick(accountKey = actualData.accountKey, userKey = it, uriHandler = uriHandler)
        },
        headerTrailing = {
            if (appearanceSettings.misskey.showVisibility) {
                VisibilityIcon(
                    visibility = actualData.visibility,
                    modifier =
                        Modifier
                            .size(14.dp)
                            .alpha(MediumAlpha),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        },
        contentFooter = {
            actualData.let { data ->
                if (data.reaction.emojiReactions.isNotEmpty()) {
                    StatusReactionComponent(
                        data = data,
                        event = event,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        rawContent = actualData.content,
        content = actualData.contentToken,
        contentDirection = actualData.contentDirection,
        contentWarning = actualData.contentWarningText,
        user = actualData.user,
        medias = actualData.medias,
        card = actualData.card,
        humanizedTime = actualData.localizedShortTime,
        expandedTime = data.localizedFullTime,
        isDetail = isDetail,
        sensitive = actualData.sensitive,
        quotedStatus = actualData.quote,
        onQuotedStatusClick = {
            event.onStatusClick(it as UiStatus.Misskey, uriHandler)
        },
        poll = actualData.poll,
        headerIcon = currentData.renote?.let { FontAwesomeIcons.Solid.Retweet },
        headerTextId = currentData.renote?.let { R.string.misskey_item_reblogged_status },
        headerUser = currentData.renote?.let { currentData.user },
        statusActions = {
            StatusFooterComponent(
                data = actualData,
                event = event,
            )
        },
        swipeLeftIcon = appearanceSettings.misskey.swipeLeft.icon,
        swipeLeftText = stringResource(id = appearanceSettings.misskey.swipeLeft.id),
        onSwipeLeft =
            appearanceSettings.misskey.swipeLeft
                .takeUnless { it == AppearanceSettings.Misskey.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Misskey.SwipeActions.NONE -> Unit
                            AppearanceSettings.Misskey.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.Misskey.SwipeActions.RENOTE -> event.onReblogClick(actualData)
                            AppearanceSettings.Misskey.SwipeActions.ADDREACTION -> event.onAddReactionClick(actualData, uriHandler)
                        }
                    }
                },
        swipeRightIcon = appearanceSettings.misskey.swipeRight.icon,
        swipeRightText = stringResource(id = appearanceSettings.misskey.swipeRight.id),
        onSwipeRight =
            appearanceSettings.misskey.swipeRight
                .takeUnless { it == AppearanceSettings.Misskey.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Misskey.SwipeActions.NONE -> Unit
                            AppearanceSettings.Misskey.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.Misskey.SwipeActions.RENOTE -> event.onReblogClick(actualData)
                            AppearanceSettings.Misskey.SwipeActions.ADDREACTION -> event.onAddReactionClick(actualData, uriHandler)
                        }
                    }
                },
    )
}

@Composable
private fun StatusReactionComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        items(data.reaction.emojiReactions) { reaction ->
            Card(
                shape = RoundedCornerShape(100),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clickable {
                                event.onReactionClick(data, reaction)
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    EmojiImage(
                        uri = reaction.url,
                        modifier = Modifier.height(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reaction.humanizedCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatusFooterComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
) {
    val uriHandler = LocalUriHandler.current
    val actualData = data.renote ?: data
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
        icon = FontAwesomeIcons.Solid.Retweet,
        text = actualData.matrices.humanizedReNoteCount,
        modifier =
            Modifier
                .weight(1f),
        subMenus = { closeMenu ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(id = R.string.misskey_item_action_renote),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.Retweet,
                        contentDescription = stringResource(id = R.string.misskey_item_action_renote),
                        modifier = Modifier.size(24.dp),
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
                        text = stringResource(id = R.string.misskey_item_action_quote),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = stringResource(id = R.string.misskey_item_action_quote),
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
            if (actualData.reaction.myReaction != null) {
                Icons.Default.Remove
            } else {
                Icons.Default.Add
            },
        text = null,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onAddReactionClick(actualData, uriHandler)
        },
    )
    StatusActionGroup(
        icon = Icons.Default.MoreHoriz,
        text = null,
        subMenus = { closeMenu ->
            if (actualData.isFromMe) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.misskey_item_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.misskey_item_action_delete),
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
                            contentDescription = stringResource(id = R.string.misskey_item_action_report),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.misskey_item_action_report),
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

@Composable
internal fun VisibilityIcon(
    visibility: UiStatus.Misskey.Visibility,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiStatus.Misskey.Visibility.Public ->
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(id = R.string.misskey_visibility_public),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Home ->
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = stringResource(id = R.string.misskey_visibility_home),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Followers ->
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(id = R.string.misskey_visibility_followers),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Specified ->
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = stringResource(id = R.string.misskey_visibility_specified),
                modifier = modifier,
            )
    }
}

internal interface MisskeyStatusEvent {
    fun onStatusClick(
        data: UiStatus.Misskey,
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

    fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    )

    fun onReplyClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onReblogClick(data: UiStatus.Misskey)

    fun onQuoteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onAddReactionClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onDeleteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )
}
