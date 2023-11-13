package dev.dimension.flare.ui.component.status.bluesky

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
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
internal fun BlueskyStatusComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            Modifier
                .clickable {
                    event.onStatusClick(data)
                }
                .then(modifier),
    ) {
        if (data.repostBy != null) {
            StatusRetweetHeaderComponent(
                icon = Icons.Default.SyncAlt,
                user = data.repostBy,
                text = stringResource(id = R.string.mastodon_item_reblogged_status),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        StatusHeaderComponent(
            data = data,
            event = event,
        )
        StatusContentComponent(
            data = data,
        )
        if (data.medias.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMediaComponent(
                data = data.medias,
                onMediaClick = event::onMediaClick,
            )
        }
        data.quote?.let { quote ->
            Spacer(modifier = Modifier.height(8.dp))
            UiStatusQuoted(
                status = quote,
                onMediaClick = event::onMediaClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StatusFooterComponent(
            data = data,
            event = event,
        )
    }
}

@Composable
private fun StatusHeaderComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier,
) {
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it) },
        modifier = modifier,
    ) {
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
private fun StatusContentComponent(
    data: UiStatus.Bluesky,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        if (data.content.isNotEmpty() && data.content.isNotBlank()) {
            HtmlText2(
                element = data.contentToken,
                layoutDirection = data.contentDirection,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusFooterComponent(
    data: UiStatus.Bluesky,
    event: BlueskyStatusEvent,
    modifier: Modifier = Modifier,
) {
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
                icon = Icons.AutoMirrored.Filled.Reply,
                text = data.matrices.humanizedReplyCount,
                modifier =
                    Modifier
                        .weight(1f),
                onClicked = {
                    event.onReplyClick(data)
                },
            )
            StatusActionButton(
                icon = Icons.Default.SyncAlt,
                text = data.matrices.humanizedRepostCount,
                modifier =
                    Modifier
                        .weight(1f),
                onClicked = {
                    showRenoteMenu = true
                },
                color =
                    if (data.reaction.reposted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                content = {
                    DropdownMenu(
                        expanded = showRenoteMenu,
                        onDismissRequest = { showRenoteMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.blusky_item_action_repost),
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
                                event.onReblogClick(data)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.blusky_item_action_quote),
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
                                event.onQuoteClick(data)
                            },
                        )
                    }
                },
            )
            StatusActionButton(
                icon =
                    if (data.reaction.liked) {
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
                    if (data.reaction.liked) {
                        Color.Red
                    } else {
                        LocalContentColor.current
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

internal interface BlueskyStatusEvent {
    fun onStatusClick(data: UiStatus.Bluesky)

    fun onUserClick(userKey: MicroBlogKey)

    fun onMediaClick(uiMedia: UiMedia)

    fun onReplyClick(data: UiStatus.Bluesky)

    fun onReblogClick(data: UiStatus.Bluesky)

    fun onQuoteClick(data: UiStatus.Bluesky)

    fun onLikeClick(data: UiStatus.Bluesky)

    fun onMoreClick(data: UiStatus.Bluesky)
}

internal class DefaultBlueskyStatusEvent(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
) : BlueskyStatusEvent {
    override fun onStatusClick(data: UiStatus.Bluesky) {
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

    override fun onMediaClick(uiMedia: UiMedia) {
        if (uiMedia is UiMedia.Image) {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(uiMedia.url)
                            .deeplink(),
                    ),
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onReplyClick(data: UiStatus.Bluesky) {
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

    override fun onReblogClick(data: UiStatus.Bluesky) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Bluesky ?: return@launch
            account.dataSource.reblog(data)
        }
    }

    override fun onLikeClick(data: UiStatus.Bluesky) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Bluesky ?: return@launch
            account.dataSource.like(data)
        }
    }

    override fun onMoreClick(data: UiStatus.Bluesky) {
    }

    override fun onQuoteClick(data: UiStatus.Bluesky) {
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
}
