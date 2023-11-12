package dev.dimension.flare.ui.component.status.misskey

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun MisskeyStatusComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData = data.renote ?: data
    Column(
        modifier =
            Modifier
                .clickable {
                    event.onStatusClick(data)
                }
                .then(modifier),
    ) {
        if (data.renote != null) {
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
            event = event,
        )
        if (actualData.media.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMediaComponent(
                data = actualData.media,
                onMediaClick = event::onMediaClick,
            )
        }
//        StatusCardComponent(
//            data = actualData,
//            event = event
//        )
        actualData.quote?.let { quote ->
            Spacer(modifier = Modifier.height(8.dp))
            UiStatusQuoted(
                status = quote,
                onMediaClick = event::onMediaClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (actualData.reaction.emojiReactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusReactionComponent(
                data = actualData,
                event = event,
            )
        }
        StatusFooterComponent(
            data = data,
            event = event,
        )
    }
}

@Composable
private fun StatusFooterComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData = data.renote ?: data
    var showRenoteMenu by remember {
        mutableStateOf(false)
    }
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
                icon = Icons.Default.Reply,
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
                text = actualData.matrices.humanizedReNoteCount,
                modifier =
                    Modifier
                        .weight(1f),
                onClicked = {
                    showRenoteMenu = !showRenoteMenu
                },
                content = {
                    DropdownMenu(
                        expanded = showRenoteMenu,
                        onDismissRequest = { showRenoteMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.misskey_item_action_renote),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showRenoteMenu = false
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
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showRenoteMenu = false
                                event.onQuoteClick(actualData)
                            },
                        )
                    }
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
                    event.onAddReactionClick(actualData)
                },
            )
            StatusActionButton(
                icon = Icons.Default.MoreHoriz,
                text = null,
                onClicked = {
                    event.onMoreClick(data)
                },
            )
        }
    }
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
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
private fun StatusContentComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
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
                        event = event,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPollComponent(
    data: UiStatus.Misskey.Poll,
    event: MisskeyStatusEvent,
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
                    progress = option.percentage,
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
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it) },
        modifier = modifier,
    ) {
        VisibilityIcon(
            visibility = data.visibility,
            modifier =
                Modifier
                    .size(14.dp)
                    .alpha(MediumAlpha),
        )
        Spacer(modifier = Modifier.width(4.dp))
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
    visibility: UiStatus.Misskey.Visibility,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiStatus.Misskey.Visibility.Public ->
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(id = R.string.mastodon_visibility_public),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Home ->
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = stringResource(id = R.string.mastodon_visibility_unlisted),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Followers ->
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(id = R.string.mastodon_visibility_private),
                modifier = modifier,
            )

        UiStatus.Misskey.Visibility.Specified ->
            Icon(
                imageVector = Icons.Default.MailOutline,
                contentDescription = stringResource(id = R.string.mastodon_visibility_direct),
                modifier = modifier,
            )
    }
}

internal interface MisskeyStatusEvent {
    fun onStatusClick(data: UiStatus.Misskey)

    fun onUserClick(userKey: MicroBlogKey)

    fun onMediaClick(media: UiMedia)

    fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    )

    fun onReplyClick(data: UiStatus.Misskey)

    fun onReblogClick(data: UiStatus.Misskey)

    fun onQuoteClick(data: UiStatus.Misskey)

    fun onAddReactionClick(data: UiStatus.Misskey)

    fun onMoreClick(data: UiStatus.Misskey)
}

internal class DefaultMisskeyStatusEvent(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
) : MisskeyStatusEvent {
    override fun onStatusClick(data: UiStatus.Misskey) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    dev.dimension.flare.ui.screen.destinations.StatusRouteDestination(data.statusKey)
                        .deeplink(),
                ),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onUserClick(userKey: MicroBlogKey) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(ProfileRouteDestination(userKey).deeplink()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onMediaClick(media: UiMedia) {
        if (media is UiMedia.Image) {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(media.url)
                            .deeplink(),
                    ),
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    ) {
    }

    override fun onReplyClick(data: UiStatus.Misskey) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    dev.dimension.flare.ui.screen.destinations.ReplyRouteDestination(data.statusKey)
                        .deeplink(),
                ),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onReblogClick(data: UiStatus.Misskey) {
        scope.launch {
            val account = accountRepository.get(data.accountKey) as? UiAccount.Misskey ?: return@launch
            runCatching {
                account.dataSource.renote(data)
            }.onFailure {
            }
        }
    }

    override fun onQuoteClick(data: UiStatus.Misskey) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    dev.dimension.flare.ui.screen.destinations.QuoteDestination(data.statusKey)
                        .deeplink(),
                ),
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onAddReactionClick(data: UiStatus.Misskey) {
    }

    override fun onMoreClick(data: UiStatus.Misskey) {
    }
}
