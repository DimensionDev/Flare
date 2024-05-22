package dev.dimension.flare.ui.component.status.vvo

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
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
import dev.dimension.flare.ui.model.localizedFullTime
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.model.medias

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun VVOStatusComponent(
    data: UiStatus.VVO,
    event: VVOStatusEvent,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val appearanceSettings = LocalAppearanceSettings.current
    val uriHandler = LocalUriHandler.current
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
            event.onUserClick(
                accountKey = data.accountKey,
                userKey = it,
                uriHandler = uriHandler,
            )
        },
        rawContent = data.content,
        content = data.contentToken,
        contentDirection = data.contentDirection,
        user = data.displayUser,
        medias = data.medias,
        card = null,
        humanizedTime = data.localizedShortTime,
        expandedTime = data.localizedFullTime,
        isDetail = isDetail,
        poll = null,
        headerIcon = null,
        headerTextId = null,
        headerUser = null,
        replyHandle = null,
        quotedStatus = data.quote,
        onQuotedStatusClick = {
            event.onStatusClick(it as UiStatus.VVO, uriHandler)
        },
        statusActions = {
            StatusFooterComponent(
                data = data,
                event = event,
            )
        },
        swipeLeftIcon = appearanceSettings.vvo.swipeLeft.icon,
        swipeLeftText = stringResource(appearanceSettings.vvo.swipeLeft.id),
        onSwipeLeft =
            appearanceSettings.vvo.swipeLeft
                .takeUnless { it == AppearanceSettings.VVO.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.VVO.SwipeActions.NONE -> Unit
                            AppearanceSettings.VVO.SwipeActions.COMMENT ->
                                event.onCommentClick(
                                    data,
                                    uriHandler,
                                )

                            AppearanceSettings.VVO.SwipeActions.REBLOG -> event.onReblogClick(data, uriHandler)
                            AppearanceSettings.VVO.SwipeActions.FAVOURITE -> event.onLikeClick(data)
                        }
                    }
                },
        swipeRightIcon = appearanceSettings.vvo.swipeRight.icon,
        swipeRightText = stringResource(appearanceSettings.vvo.swipeRight.id),
        onSwipeRight =
            appearanceSettings.vvo.swipeRight
                .takeUnless { it == AppearanceSettings.VVO.SwipeActions.NONE }
                ?.let {
                    {
                        when (it) {
                            AppearanceSettings.VVO.SwipeActions.NONE -> Unit
                            AppearanceSettings.VVO.SwipeActions.COMMENT ->
                                event.onCommentClick(
                                    data,
                                    uriHandler,
                                )

                            AppearanceSettings.VVO.SwipeActions.REBLOG -> event.onReblogClick(data, uriHandler)
                            AppearanceSettings.VVO.SwipeActions.FAVOURITE -> event.onLikeClick(data)
                        }
                    }
                },
    )
}

@Composable
private fun RowScope.StatusFooterComponent(
    data: UiStatus.VVO,
    event: VVOStatusEvent,
) {
    val uriHandler = LocalUriHandler.current
    StatusActionButton(
        enabled = data.canReblog,
        icon = FontAwesomeIcons.Solid.Retweet,
        text = data.matrices.humanizedRepostCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onReblogClick(data, uriHandler)
        },
    )
    StatusActionButton(
        icon = Icons.AutoMirrored.Filled.Comment,
        text = data.matrices.humanizedCommentCount,
        modifier =
            Modifier
                .weight(1f),
        onClicked = {
            event.onCommentClick(data, uriHandler)
        },
    )
    StatusActionButton(
        icon =
            if (data.liked) {
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
            if (data.liked) {
                Color.Red
            } else {
                LocalContentColor.current
            },
    )
    StatusActionGroup(
        icon = Icons.Default.MoreHoriz,
        text = null,
        subMenus = { closeMenu ->
            if (data.isFromMe) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.xqt_item_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.xqt_item_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onDeleteClick(data, uriHandler)
                    },
                )
            } else {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Report,
                            contentDescription = stringResource(id = R.string.xqt_item_report),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.xqt_item_report),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        closeMenu.invoke()
                        event.onReportClick(data, uriHandler)
                    },
                )
            }
        },
    )
}

internal interface VVOStatusEvent {
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

    fun onCommentClick(
        data: UiStatus.VVO,
        uriHandler: UriHandler,
    )

    fun onReblogClick(
        data: UiStatus.VVO,
        uriHandler: UriHandler,
    )

    fun onLikeClick(data: UiStatus.VVO)

    fun onDeleteClick(
        data: UiStatus.VVO,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        data: UiStatus.VVO,
        uriHandler: UriHandler,
    )

    fun onStatusClick(
        data: UiStatus.VVO,
        uriHandler: UriHandler,
    )
}
