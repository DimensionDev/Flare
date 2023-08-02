package dev.dimension.flare.ui.component.status

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.getAccountUseCase
import dev.dimension.flare.data.repository.cache.updateStatusUseCase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun StatusPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .placeholder(true)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Placeholder",
                    modifier = Modifier
                        .placeholder(true)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "username@Placeholder",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .alpha(MediumAlpha)
                        .placeholder(true)

                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies ultrices, nisl nisl aliquet nisl, nec aliquam nisl nisl nec.",
            modifier = Modifier
                .placeholder(true)
        )
    }
}

@Composable
internal fun MastodonStatusComponent(
    data: UiStatus.Mastodon,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    val actualData = data.reblogStatus ?: data
    Column(
        modifier = modifier
    ) {
        if (data.reblogStatus != null) {
            StatusRetweetHeaderComponent(
                icon = Icons.Default.SyncAlt,
                user = data.user,
                text = stringResource(id = R.string.mastodon_item_reblogged_status)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        StatusHeaderComponent(
            data = actualData,
            event = event
        )
        StatusContentComponent(
            data = actualData,
            event = event,
            state = state
        )
        StatusMediaComponent(
            data = actualData,
            event = event
        )
        StatusCardComponent(
            data = actualData,
            event = event
        )
        StatusFooterComponent(
            data = data,
            event = event
        )
    }
}

@Composable
internal fun StatusRetweetHeaderComponent(
    icon: ImageVector,
    user: UiUser.Mastodon?,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .alpha(MediumAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = rememberVectorPainter(image = icon),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
        )
        if (user != null) {
            Spacer(modifier = Modifier.width(8.dp))
            HtmlText(
                element = user.nameElement,
                layoutDirection = LocalLayoutDirection.current,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignByBaseline()
        )
    }
}

data class MastodonStatusState(
    val expanded: Boolean = false
)

@Composable
private fun StatusCardComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    if (data.card != null) {
        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        uriHandler.openUri(data.card.url)
                    }
            ) {
                if (data.card.media != null) {
                    MediaItem(
                        media = data.card.media,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                ) {
                    Text(text = data.card.title)
                    if (data.card.description != null) {
                        Text(
                            text = data.card.description,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .alpha(MediumAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFooterComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    val actualData = data.reblogStatus ?: data
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
                text = actualData.matrices.humanizedReplyCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onReplyClick(actualData)
                }
            )
            StatusActionButton(
                icon = Icons.Default.SyncAlt,
                text = actualData.matrices.humanizedReblogCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onReblogClick(actualData)
                },
                color = if (actualData.reaction.reblogged) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                }
            )
            StatusActionButton(
                icon = Icons.Default.Favorite,
                text = actualData.matrices.humanizedFavouriteCount,
                modifier = Modifier
                    .weight(1f),
                onClicked = {
                    event.onLikeClick(actualData)
                },
                color = if (actualData.reaction.liked) {
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

@Composable
private fun StatusActionButton(
    icon: ImageVector,
    text: String?,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClicked
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .indication(
                    interactionSource = interactionSource,
                    indication = rememberRipple(
                        bounded = false,
                        radius = 20.dp
                    )
                )
                .size(16.dp),
            tint = color
        )
        if (!text.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun StatusMediaComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    if (data.media.isNotEmpty()) {
        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AdaptiveGrid(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                content = {
                    data.media.forEach { media ->
                        MediaItem(
                            media = media,
                            modifier = Modifier
                                .clickable {
                                    event.onMediaClick(media)
                                }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun MediaItem(
    media: UiMedia,
    modifier: Modifier = Modifier
) {
    when (media) {
        is UiMedia.Image -> {
            NetworkImage(
                model = media.url,
                contentDescription = media.description,
                modifier = modifier.aspectRatio(media.aspectRatio)
            )
        }

        is UiMedia.Video -> {
            NetworkImage(
                model = media.thumbnailUrl,
                contentDescription = media.description,
                modifier = modifier.aspectRatio(media.aspectRatio)
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
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        if (!data.contentWarningText.isNullOrEmpty()) {
            Text(
                text = data.contentWarningText
            )
            TextButton(
                onClick = {
                    event.onShowMoreClick(data)
                }
            ) {
                Text(
                    text = stringResource(
                        if (state.expanded) R.string.mastodon_item_show_less else R.string.mastodon_item_show_more
                    )
                )
            }
        }
        AnimatedVisibility(visible = state.expanded || data.contentWarningText.isNullOrEmpty()) {
            Column {
                if (data.content.isNotEmpty() && data.content.isNotBlank()) {
                    HtmlText(
                        element = data.contentToken,
                        layoutDirection = data.contentDirection,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (data.poll != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusPollComponent(
                        data = data.poll,
                        event = event
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPollComponent(
    data: UiStatus.Mastodon.Poll,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.options.forEach { option ->
            Column {
                Row {
                    Box {
                        Text(
                            text = option.humanizedPercentage
                        )
                        Text(
                            text = "100%",
                            modifier = Modifier.alpha(0f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.title
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = option.percentage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                )
            }
        }
        Text(
            text = data.humanizedExpiresAt
        )
    }
}

@Composable
private fun StatusHeaderComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarComponent(
            data = data.user.avatarUrl,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    event.onUserClick(data.user.userKey)
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            HtmlText(
                element = data.user.nameElement,
                layoutDirection = data.user.nameDirection,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        event.onUserClick(data.user.userKey)
                    }
            )
            Text(
                text = data.user.handle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .alpha(MediumAlpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        event.onUserClick(data.user.userKey)
                    }
            )
        }
        VisibilityIcon(
            visibility = data.visibility,
            modifier = Modifier
                .size(14.dp)
                .alpha(MediumAlpha)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = data.humanizedTime,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .alpha(MediumAlpha)
        )
    }
}

@Composable
internal fun VisibilityIcon(
    visibility: UiStatus.Mastodon.Visibility,
    modifier: Modifier = Modifier
) {
    when (visibility) {
        UiStatus.Mastodon.Visibility.Public -> Icon(
            imageVector = Icons.Default.Public,
            contentDescription = stringResource(id = R.string.mastodon_visibility_public),
            modifier = modifier
        )

        UiStatus.Mastodon.Visibility.Unlisted -> Icon(
            imageVector = Icons.Default.LockOpen,
            contentDescription = stringResource(id = R.string.mastodon_visibility_unlisted),
            modifier = modifier
        )

        UiStatus.Mastodon.Visibility.Private -> Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringResource(id = R.string.mastodon_visibility_private),
            modifier = modifier
        )

        UiStatus.Mastodon.Visibility.Direct -> Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = stringResource(id = R.string.mastodon_visibility_direct),
            modifier = modifier
        )
    }
}

internal interface MastodonStatusEvent {
    fun onUserClick(userKey: MicroBlogKey)
    fun onStatusClick(status: UiStatus.Mastodon)
    fun onStatusLongClick(status: UiStatus.Mastodon)
    fun onReplyClick(status: UiStatus.Mastodon)
    fun onReblogClick(status: UiStatus.Mastodon)
    fun onLikeClick(status: UiStatus.Mastodon)
    fun onBookmarkClick(status: UiStatus.Mastodon)
    fun onMediaClick(media: UiMedia)
    fun onShowMoreClick(status: UiStatus.Mastodon)
    fun onMoreClick(status: UiStatus.Mastodon)
}

@Provides
@Singleton
internal class DefaultMastodonStatusEvent(
    private val context: Context,
    private val scope: CoroutineScope
) : MastodonStatusEvent {
    override fun onUserClick(userKey: MicroBlogKey) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(ProfileRouteDestination(userKey).deeplink()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onStatusClick(status: UiStatus.Mastodon) {
    }

    override fun onStatusLongClick(status: UiStatus.Mastodon) {
    }

    override fun onReplyClick(status: UiStatus.Mastodon) {
    }

    override fun onReblogClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account = getAccountUseCase<UiAccount.Mastodon>(status.accountKey) ?: return@launch
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                update = {
                    it.copy(
                        data = it.data.copy(
                            reblogged = !status.reaction.reblogged,
                            reblogsCount = if (status.reaction.reblogged) {
                                it.data.reblogsCount?.minus(1)
                            } else {
                                it.data.reblogsCount?.plus(1)
                            }
                        )
                    )
                }
            )

            runCatching {
                if (status.reaction.reblogged) {
                    account.service.unRetweet(status.statusKey.id)
                } else {
                    account.service.retweet(status.statusKey.id)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = status.statusKey,
                    accountKey = status.accountKey,
                    update = {
                        it.copy(
                            data = it.data.copy(
                                reblogged = status.reaction.reblogged,
                                reblogsCount = if (status.reaction.reblogged) {
                                    it.data.reblogsCount?.minus(1)
                                } else {
                                    it.data.reblogsCount?.plus(1)
                                }
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onLikeClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account = getAccountUseCase<UiAccount.Mastodon>(status.accountKey) ?: return@launch
            updateStatusUseCase<StatusContent.Mastodon>(
                statusKey = status.statusKey,
                accountKey = status.accountKey,
                update = {
                    it.copy(
                        data = it.data.copy(
                            favourited = !status.reaction.liked,
                            favouritesCount = if (status.reaction.liked) {
                                it.data.favouritesCount?.minus(1)
                            } else {
                                it.data.favouritesCount?.plus(1)
                            }
                        )
                    )
                }
            )

            runCatching {
                if (status.reaction.liked) {
                    account.service.unlike(status.statusKey.id)
                } else {
                    account.service.like(status.statusKey.id)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.Mastodon>(
                    statusKey = status.statusKey,
                    accountKey = status.accountKey,
                    update = {
                        it.copy(
                            data = it.data.copy(
                                favourited = status.reaction.liked,
                                favouritesCount = if (status.reaction.liked) {
                                    it.data.favouritesCount?.minus(1)
                                } else {
                                    it.data.favouritesCount?.plus(1)
                                }
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onBookmarkClick(status: UiStatus.Mastodon) {
    }

    override fun onMediaClick(media: UiMedia) {
    }

    override fun onShowMoreClick(status: UiStatus.Mastodon) {
    }

    override fun onMoreClick(status: UiStatus.Mastodon) {
    }
}
