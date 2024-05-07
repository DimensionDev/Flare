package dev.dimension.flare.ui.component.status.mastodon

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Retweet
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusActionGroup
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun StatusPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        UserPlaceholder()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies" +
                    " ultrices, nisl nisl aliquet nisl, nec aliquam nisl nisl nec.",
            modifier =
                Modifier
                    .placeholder(true),
        )
    }
}

@Composable
internal fun UserPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .placeholder(true),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Placeholder",
                modifier =
                    Modifier
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "username@Placeholder",
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .alpha(MediumAlpha)
                        .placeholder(true),
            )
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MastodonStatusComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val actualData = data.reblogStatus ?: data
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
        headerTrailing = {
            if (appearanceSettings.mastodon.showVisibility) {
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
        onUserClick = {
            event.onUserClick(accountKey = actualData.accountKey, userKey = it, uriHandler = uriHandler)
        },
        rawContent = actualData.content,
        content = actualData.contentToken,
        contentDirection = actualData.contentDirection,
        contentWarning = actualData.contentWarningText,
        user = actualData.user,
        medias = actualData.media,
        card = actualData.card,
        humanizedTime = actualData.humanizedTime,
        expandedTime = data.expandedTime,
        isDetail = isDetail,
        sensitive = actualData.sensitive,
        poll = actualData.poll,
        headerIcon = data.reblogStatus?.let { FontAwesomeIcons.Solid.Retweet },
        headerTextId = data.reblogStatus?.let { R.string.mastodon_item_reblogged_status },
        headerUser = data.reblogStatus?.let { data.user },
        statusActions = {
            StatusFooterComponent(
                data = data,
                event = event,
            )
        },
        swipeLeftIcon = appearanceSettings.mastodon.swipeLeft.icon,
        swipeLeftText = stringResource(appearanceSettings.mastodon.swipeLeft.id),
        onSwipeLeft =
            appearanceSettings.mastodon.swipeLeft
                .takeUnless { it == AppearanceSettings.Mastodon.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Mastodon.SwipeActions.NONE -> Unit
                            AppearanceSettings.Mastodon.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.Mastodon.SwipeActions.REBLOG -> event.onReblogClick(actualData)
                            AppearanceSettings.Mastodon.SwipeActions.FAVOURITE -> event.onLikeClick(actualData)
                            AppearanceSettings.Mastodon.SwipeActions.BOOKMARK -> event.onBookmarkClick(actualData)
                        }
                    }
                },
        swipeRightIcon = appearanceSettings.mastodon.swipeRight.icon,
        swipeRightText = stringResource(appearanceSettings.mastodon.swipeRight.id),
        onSwipeRight =
            appearanceSettings.mastodon.swipeRight
                .takeUnless { it == AppearanceSettings.Mastodon.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.Mastodon.SwipeActions.NONE -> Unit
                            AppearanceSettings.Mastodon.SwipeActions.REPLY -> event.onReplyClick(actualData, uriHandler)
                            AppearanceSettings.Mastodon.SwipeActions.REBLOG -> event.onReblogClick(actualData)
                            AppearanceSettings.Mastodon.SwipeActions.FAVOURITE -> event.onLikeClick(actualData)
                            AppearanceSettings.Mastodon.SwipeActions.BOOKMARK -> event.onBookmarkClick(actualData)
                        }
                    }
                },
    )
}

@Composable
private fun RowScope.StatusFooterComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
) {
    val actualData = data.reblogStatus ?: data
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
    StatusActionButton(
        icon = FontAwesomeIcons.Solid.Retweet,
        text = actualData.matrices.humanizedReblogCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onReblogClick(actualData)
        },
        color =
            if (actualData.reaction.reblogged) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
    )
    StatusActionButton(
        icon =
            if (actualData.reaction.liked) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FavoriteBorder
            },
        text = actualData.matrices.humanizedFavouriteCount,
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

@Composable
internal fun VisibilityIcon(
    visibility: UiStatus.Mastodon.Visibility,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiStatus.Mastodon.Visibility.Public ->
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(id = R.string.mastodon_visibility_public),
                modifier = modifier,
            )

        UiStatus.Mastodon.Visibility.Unlisted ->
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = stringResource(id = R.string.mastodon_visibility_unlisted),
                modifier = modifier,
            )

        UiStatus.Mastodon.Visibility.Private ->
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(id = R.string.mastodon_visibility_private),
                modifier = modifier,
            )

        UiStatus.Mastodon.Visibility.Direct ->
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = stringResource(id = R.string.mastodon_visibility_direct),
                modifier = modifier,
            )
    }
}

internal interface MastodonStatusEvent {
    fun onUserClick(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    )

    fun onStatusClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReplyClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReblogClick(status: UiStatus.Mastodon)

    fun onLikeClick(status: UiStatus.Mastodon)

    fun onBookmarkClick(status: UiStatus.Mastodon)

    fun onMediaClick(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        index: Int,
        preview: String?,
        uriHandler: UriHandler,
    )

    fun onDeleteClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )
}
