package dev.dimension.flare.ui.component.status.mastodon

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.screen.destinations.MediaRouteDestination
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import dev.dimension.flare.ui.screen.destinations.ReplyRouteDestination
import dev.dimension.flare.ui.screen.destinations.StatusRouteDestination
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

@Composable
internal fun MastodonStatusComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData = data.reblogStatus ?: data
    val appearanceSettings = LocalAppearanceSettings.current
    Column(
        modifier =
            Modifier
                .clickable {
                    event.onStatusClick(data)
                }
                .then(modifier),
    ) {
        if (data.reblogStatus != null) {
            StatusRetweetHeaderComponent(
                icon = Icons.Default.SyncAlt,
                user = data.user,
                text = stringResource(id = R.string.mastodon_item_reblogged_status),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        StatusHeaderComponent(
            data = actualData,
            event = event,
        )
        StatusContentComponent(
            data = actualData,
        )
        if (actualData.media.isNotEmpty() && appearanceSettings.showMedia) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMediaComponent(
                data = actualData.media,
                onMediaClick = event::onMediaClick,
            )
        }
        if (appearanceSettings.showLinkPreview) {
            StatusCardComponent(
                data = actualData,
            )
        }
        if (appearanceSettings.showActions) {
            StatusFooterComponent(
                data = data,
                event = event,
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusCardComponent(
    data: UiStatus.Mastodon,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    data.card?.let { card ->
        Column(
            modifier = modifier,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            uriHandler.openUri(card.url)
                        },
            ) {
                card.media?.let {
                    MediaItem(
                        media = it,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .padding(8.dp),
                ) {
                    Text(text = card.title)
                    card.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .alpha(MediumAlpha),
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
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val actualData = data.reblogStatus ?: data
    Row(
        modifier =
            modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = MediumAlpha),
        ) {
            StatusActionButton(
                icon = Icons.AutoMirrored.Filled.Reply,
                text = actualData.matrices.humanizedReplyCount,
                modifier =
                    Modifier
                        .weight(1f),
                onClicked = {
                    event.onReplyClick(actualData)
                },
            )
            StatusActionButton(
                icon = Icons.Default.SyncAlt,
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
            StatusActionButton(
                icon = Icons.Default.MoreHoriz,
                text = null,
                onClicked = {
                    showMoreMenu = true
                },
                content = {
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                if (actualData.reaction.bookmarked) {
                                    Text(text = stringResource(id = R.string.mastodon_item_unbookmark))
                                } else {
                                    Text(text = stringResource(id = R.string.mastodon_item_bookmark))
                                }
                            },
                            onClick = {
                                showMoreMenu = false
                                event.onBookmarkClick(actualData)
                            },
                        )

                        if (actualData.isFromMe) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.mastodon_item_delete))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    event.onDeleteClick(actualData)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.mastodon_item_report))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    event.onReportClick(actualData)
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusContentComponent(
    data: UiStatus.Mastodon,
    modifier: Modifier = Modifier,
) {
    // TODO: not a best way to handle this
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(
        modifier = modifier,
    ) {
        data.contentWarningText?.let {
            if (it.isNotEmpty()) {
                Text(
                    text = it,
                )
                TextButton(
                    onClick = {
                        expanded = !expanded
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                if (expanded) R.string.mastodon_item_show_less else R.string.mastodon_item_show_more,
                            ),
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded || data.contentWarningText.isNullOrEmpty()) {
            Column {
                if (data.content.isNotEmpty() && data.content.isNotBlank()) {
                    HtmlText2(
                        element = data.contentToken,
                        layoutDirection = data.contentDirection,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                data.poll?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusPollComponent(
                        data = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPollComponent(
    data: UiStatus.Mastodon.Poll,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        data.options.forEach { option ->
            Column {
                Row {
                    Box {
                        Text(
                            text = option.humanizedPercentage,
                        )
                        Text(
                            text = "100%",
                            modifier = Modifier.alpha(0f),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.title,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { option.percentage },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                )
            }
        }
        Text(
            text = data.humanizedExpiresAt,
        )
    }
}

@Composable
private fun StatusHeaderComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it) },
        modifier = modifier,
    ) {
        if (LocalAppearanceSettings.current.mastodon.showVisibility) {
            VisibilityIcon(
                visibility = data.visibility,
                modifier =
                    Modifier
                        .size(14.dp)
                        .alpha(MediumAlpha),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = data.humanizedTime,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .alpha(MediumAlpha),
        )
    }
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
    fun onUserClick(userKey: MicroBlogKey)

    fun onStatusClick(status: UiStatus.Mastodon)

    fun onReplyClick(status: UiStatus.Mastodon)

    fun onReblogClick(status: UiStatus.Mastodon)

    fun onLikeClick(status: UiStatus.Mastodon)

    fun onBookmarkClick(status: UiStatus.Mastodon)

    fun onMediaClick(media: UiMedia)

    fun onDeleteClick(status: UiStatus.Mastodon)

    fun onReportClick(status: UiStatus.Mastodon)
}

internal class DefaultMastodonStatusEvent(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
) : MastodonStatusEvent {
    override fun onUserClick(userKey: MicroBlogKey) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(ProfileRouteDestination(userKey).deeplink()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onStatusClick(status: UiStatus.Mastodon) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(StatusRouteDestination(status.statusKey).deeplink()),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onReplyClick(status: UiStatus.Mastodon) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(ReplyRouteDestination(status.statusKey).deeplink()),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onReblogClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.reblog(status)
        }
    }

    override fun onLikeClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.like(status)
        }
    }

    override fun onBookmarkClick(status: UiStatus.Mastodon) {
        scope.launch {
            val account =
                accountRepository.get(status.accountKey) as? UiAccount.Mastodon ?: return@launch
            account.dataSource.bookmark(status)
        }
    }

    override fun onMediaClick(media: UiMedia) {
        if (media is UiMedia.Image) {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(MediaRouteDestination(media.url).deeplink()),
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onDeleteClick(status: UiStatus.Mastodon) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    dev.dimension.flare.ui.screen.destinations.DeleteStatusConfirmRouteDestination(status.statusKey)
                        .deeplink(),
                ),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onReportClick(status: UiStatus.Mastodon) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    dev.dimension.flare.ui.screen.destinations.MastodonReportRouteDestination(
                        userKey = status.user.userKey,
                        statusKey = status.statusKey,
                    ).deeplink(),
                ),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
