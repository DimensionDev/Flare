package dev.dimension.flare.ui.component.status.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moriatsushi.koject.Binds
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun BlueskyStatusComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .clickable {
                event.onStatusClick(data)
            }
            .then(modifier)
    ) {
        if (data.repostBy != null) {
            StatusRetweetHeaderComponent(
                icon = Icons.Default.SyncAlt,
                user = data.repostBy,
                text = stringResource(id = R.string.mastodon_item_reblogged_status)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        StatusHeaderComponent(
            data = data,
            event = event
        )
        StatusContentComponent(
            data = data,
            event = event
        )
        if (data.medias.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMediaComponent(
                data = data.medias,
                onMediaClick = event::onMediaClick
            )
        }
        StatusFooterComponent(
            data = data,
            event = event
        )
    }
}

@Composable
private fun StatusHeaderComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier
) {
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it) },
        modifier = modifier
    ) {
        Text(
            text = data.humanizedTime,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .alpha(MediumAlpha)
        )
    }
}

@Composable
private fun StatusContentComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        if (data.content.isNotEmpty() && data.content.isNotBlank()) {
            HtmlText(
                element = data.contentToken,
                layoutDirection = data.contentDirection,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusFooterComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = MediumAlpha)
        ) {
            StatusActionButton(
                icon = Icons.Default.Reply,
                text = data.matrices.humanizedReplyCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onReplyClick(data)
                }
            )
            StatusActionButton(
                icon = Icons.Default.SyncAlt,
                text = data.matrices.humanizedRepostCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onReblogClick(data)
                },
                color = if (data.reaction.reposted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                }
            )
            StatusActionButton(
                icon = if (data.reaction.liked) {
                    Icons.Default.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                text = data.matrices.humanizedLikeCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onLikeClick(data)
                },
                color = if (data.reaction.liked) {
                    Color.Red
                } else {
                    LocalContentColor.current
                }
            )
            StatusActionButton(
                icon = Icons.Default.MoreHoriz,
                text = null,
                onClicked = {
                    event.onMoreClick(data)
                }
            )
        }
    }
}

internal interface BlueskyStatusEvent {
    fun onStatusClick(data: UiStatus.Bluesky)
    fun onUserClick(userKey: MicroBlogKey)
    fun onMediaClick(uiMedia: UiMedia)
    fun onReplyClick(data: UiStatus.Bluesky)
    fun onReblogClick(data: UiStatus.Bluesky)
    fun onLikeClick(data: UiStatus.Bluesky)
    fun onMoreClick(data: UiStatus.Bluesky)
}

@Singleton
@Provides
@Binds(to = BlueskyStatusEvent::class)
internal class DefaultBlueskyStatusEvent : BlueskyStatusEvent {
    override fun onStatusClick(data: UiStatus.Bluesky) {
    }

    override fun onUserClick(userKey: MicroBlogKey) {
    }

    override fun onMediaClick(uiMedia: UiMedia) {
    }

    override fun onReplyClick(data: UiStatus.Bluesky) {
    }

    override fun onReblogClick(data: UiStatus.Bluesky) {
    }

    override fun onLikeClick(data: UiStatus.Bluesky) {
    }

    override fun onMoreClick(data: UiStatus.Bluesky) {
    }
}
