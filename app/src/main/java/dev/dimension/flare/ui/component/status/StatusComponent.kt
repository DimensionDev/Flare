package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
internal fun MastodonStatusComponent(
    data: UiStatus.Mastodon,
    state: MastodonStatusState,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData = data.reblogStatus ?: data
    Column(
        modifier = modifier,
    ) {
        StatusHeaderComponent(
            data = actualData,
            event = event,
        )
        StatusContentComponent(
            data = actualData,
            event = event,
            state = state,
        )
        StatusMediaComponent(
            data = actualData,
            event = event,
        )
        StatusCardComponent(
            data = actualData,
            event = event,
        )
        StatusFooterComponent(
            data = actualData,
            event = event,
        )
    }
}

data class MastodonStatusState(
    val expanded: Boolean = false,
)

@Composable
private fun StatusCardComponent(
    data: UiStatus.Mastodon,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    if (data.card != null) {
        Column(
            modifier = modifier,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (data.card.media != null) {
                MediaItem(
                    media = data.card.media,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(text = data.card.title)
            if (data.card.description != null) {
                Text(text = data.card.description)
            }
        }
    }
}


@Composable
private fun StatusFooterComponent(
    data: UiStatus,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = {
                event.onReplyClick(data.statusKey)
            },
        ) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = null,
            )
        }
        IconButton(
            onClick = {
                event.onReblogClick(data.statusKey)
            },
        ) {
            Icon(
                imageVector = Icons.Default.SyncAlt,
                contentDescription = null,
            )
        }
        IconButton(
            onClick = {
                event.onLikeClick(data.statusKey)
            },
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
            )
        }
        IconButton(
            onClick = {
                event.onMoreClick(data.statusKey)
            },
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun StatusMediaComponent(
    data: UiStatus.Mastodon,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    if (data.media.isNotEmpty()) {
        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Layout(
                modifier = Modifier,
                content = {
                    data.media.forEach { media ->
                        MediaItem(
                            media = media,
                            modifier = Modifier
                                .size(100.dp)
                                .clickable {
                                    event.onMediaClick(media)
                                },
                        )
                    }
                },
                measurePolicy = { measurables, constraints ->
                    val columns = ceil(sqrt(measurables.size.toDouble())).toInt()
                    val rows = ceil(measurables.size.toDouble() / columns)
                    val itemSize = constraints.maxWidth / columns
                    val itemConstraints = constraints.copy(
                        minWidth = itemSize,
                        maxWidth = itemSize,
                        minHeight = itemSize,
                        maxHeight = itemSize,
                    )
                    val placeables = measurables.map {
                        it.measure(itemConstraints)
                    }
                    layout(
                        width = constraints.maxWidth,
                        height = (rows * itemSize).toInt(),
                    ) {
                        var row = 0
                        var column = 0
                        placeables.forEach { placeable ->
                            placeable.place(
                                x = column * itemSize,
                                y = row * itemSize,
                            )
                            column++
                            if (column == columns) {
                                column = 0
                                row++
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier,
) {
    when (media) {
        is UiMedia.Image -> {
            AsyncImage(
                model = media.url,
                contentDescription = media.description,
                placeholder = rememberVectorPainter(image = Icons.TwoTone.Image),
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }

        is UiMedia.Video -> {
            AsyncImage(
                model = media.thumbnailUrl,
                contentDescription = media.description,
                placeholder = rememberVectorPainter(image = Icons.TwoTone.Image),
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }

        is UiMedia.Audio -> Unit
        is UiMedia.Gif -> Unit
    }
}

@Composable
private fun StatusContentComponent(
    data: UiStatus.Mastodon,
    state: MastodonStatusState,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        if (!data.contentWarningText.isNullOrEmpty()) {
            Text(
                text = data.contentWarningText,
            )
            TextButton(
                onClick = {
                    event.onShowMoreClick(data.statusKey)
                },
            ) {
                Text(
                    text = if (state.expanded) "Show less" else "Show more",
                )
            }
        }
        AnimatedVisibility(visible = state.expanded || data.contentWarningText.isNullOrEmpty()) {
            Column {
                HtmlText(
                    element = data.contentToken,
                    layoutDirection = data.contentDirection,
                )
                if (data.poll != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusPollComponent(
                        data = data.poll,
                        event = event,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPollComponent(
    data: UiStatus.Mastodon.Poll,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        data.options.forEach { option ->
            Row {
                Text(
                    text = option.title,
                )
                Text(
                    text = option.votesCount.toString(),
                )
            }
            LinearProgressIndicator(
                progress = option.percentage,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = data.humanizedExpiresAt,
        )
    }
}

@Composable
private fun StatusHeaderComponent(
    data: UiStatus.Mastodon,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarComponent(
            data = data.user.avatarUrl,
            modifier = Modifier
                .clickable {
                    event.onUserClick(data.user.userKey)
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f),
        ) {
            HtmlText(
                element = data.user.nameElement,
                layoutDirection = data.user.contentDirection,
                modifier = Modifier
                    .clickable {
                        event.onUserClick(data.user.userKey)
                    }
            )
            Text(
                text = data.user.displayHandle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clickable {
                        event.onUserClick(data.user.userKey)
                    }
            )
        }
        VisibilityIcon(
            visibility = data.visibility,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = data.humanizedTime,
        )
    }
}

@Composable
private fun VisibilityIcon(
    visibility: UiStatus.Mastodon.Visibility,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiStatus.Mastodon.Visibility.Public -> Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            modifier = modifier,
        )

        UiStatus.Mastodon.Visibility.Unlisted -> Icon(
            imageVector = Icons.Default.LockOpen,
            contentDescription = null,
            modifier = modifier,
        )

        UiStatus.Mastodon.Visibility.Private -> Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = modifier,
        )

        UiStatus.Mastodon.Visibility.Direct -> Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Composable
fun AvatarComponent(
    data: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = data,
        contentDescription = null,
        placeholder = rememberVectorPainter(image = Icons.Default.AccountCircle),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
    )
}

interface StatusEvent {
    fun onUserClick(userKey: MicroBlogKey)
    fun onStatusClick(statusKey: MicroBlogKey)
    fun onStatusLongClick(statusKey: MicroBlogKey)
    fun onReplyClick(statusKey: MicroBlogKey)
    fun onReblogClick(statusKey: MicroBlogKey)
    fun onLikeClick(statusKey: MicroBlogKey)
    fun onBookmarkClick(statusKey: MicroBlogKey)
    fun onMediaClick(media: UiMedia)
    fun onShowMoreClick(statusKey: MicroBlogKey)
    fun onMoreClick(statusKey: MicroBlogKey)
}
