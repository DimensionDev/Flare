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
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.localizedShortTime

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun VVOCommentComponent(
    data: UiStatus.VVOComment,
    event: VVOStatusEvent,
    modifier: Modifier = Modifier,
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

        HtmlText(element = data.contentToken)
        if (data.medias.any()) {
            if (LocalAppearanceSettings.current.showMedia) {
                Spacer(modifier = Modifier.height(4.dp))
                StatusMediaComponent(
                    data = data.medias,
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
                        val item = data.medias.firstOrNull()
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
        if (data.comments.any()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    data.comments.forEachIndexed { index, comment ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                        ) {
                            comment.displayUser?.let { UserCompat(user = it) }
                            HtmlText(element = comment.contentToken)
                        }
                        if (index != data.comments.size - 1) {
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
                    text = data.humanizedLikeCount,
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
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
