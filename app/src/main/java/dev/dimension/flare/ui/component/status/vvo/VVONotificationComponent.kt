package dev.dimension.flare.ui.component.status.vvo

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusActionGroup
import dev.dimension.flare.ui.component.status.StatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.localizedShortTime

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun VVONotificationComponent(
    data: UiStatus.VVONotification,
    event: VVOStatusEvent,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
    ) {
        data.displayUser?.let {
            StatusHeaderComponent(
                user = it,
                humanizedTime = data.localizedShortTime,
                onUserClick = {
                    event.onUserClick(
                        accountKey = data.accountKey,
                        userKey = it,
                        uriHandler = uriHandler,
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        when (val content = data.content) {
            is UiStatus.VVONotification.Content.Comment -> {
                HtmlText(element = content.contentToken)
                if (content.media.any()) {
                    if (LocalAppearanceSettings.current.showMedia) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusMediaComponent(
                            data = content.media,
                            onMediaClick = {
                                if (it is UiMedia.Image) {
                                    event.onRawMediaClick(
                                        url = it.url,
                                        preview = it.previewUrl,
                                        uriHandler = uriHandler,
                                    )
                                }
                            },
                            sensitive = false,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                val item = content.media.firstOrNull()
                                if (item is UiMedia.Image) {
                                    event.onRawMediaClick(
                                        url = item.url,
                                        preview = item.previewUrl,
                                        uriHandler = uriHandler,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(id = R.string.show_media),
                            )
                        }
                    }
                }
                if (content.comments.any()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            content.comments.forEachIndexed { index, comment ->
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                ) {
                                    comment.displayUser?.let { UserCompat(user = it) }
                                    when (val commentContent = comment.content) {
                                        is UiStatus.VVONotification.Content.Comment -> {
                                            HtmlText(element = commentContent.contentToken)
                                        }
                                        else -> Unit
                                    }
                                }
                                if (index != content.comments.size - 1) {
                                    HorizontalDivider()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatusActionButton(
                            icon = Icons.AutoMirrored.Filled.Comment,
                            text = null,
                            modifier =
                                Modifier
                                    .weight(1f),
                            onClicked = {
//                            event.onCommentClick(data, uriHandler)
                            },
                        )
                        StatusActionButton(
                            icon =
                                if (content.liked) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                            text = content.humanizedLikeCount,
                            modifier =
                                Modifier
                                    .weight(1f),
                            onClicked = {
//                            event.onLikeClick(data)
                            },
                            color =
                                if (content.liked) {
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
//                                            event.onDeleteClick(data, uriHandler)
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
//                                            event.onReportClick(data, uriHandler)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            UiStatus.VVONotification.Content.Like -> {
                Text(
                    text = stringResource(id = R.string.vvo_notification_like),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (showStatus) {
            data.status?.let { status ->
                UiStatusQuoted(
                    status = status,
                    onMediaClick = {
                        event.onMediaClick(
                            accountKey = data.accountKey,
                            statusKey = status.statusKey,
                            index = status.media.indexOf(it),
                            uriHandler = uriHandler,
                            preview =
                                when (it) {
                                    is UiMedia.Image -> it.previewUrl
                                    is UiMedia.Video -> it.thumbnailUrl
                                    is UiMedia.Gif -> it.previewUrl
                                    else -> null
                                },
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            if (data.content !is UiStatus.VVONotification.Content.Comment) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
