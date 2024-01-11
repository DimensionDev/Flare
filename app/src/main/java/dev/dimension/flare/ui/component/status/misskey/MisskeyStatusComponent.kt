package dev.dimension.flare.ui.component.status.misskey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.OptionalSwipeToDismissBox
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusMediaComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MisskeyStatusComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val currentData by rememberUpdatedState(data)
    val actualData by rememberUpdatedState(newValue = data.renote ?: data)
    var showMedia by remember {
        mutableStateOf(false)
    }
    val appearanceSettings = LocalAppearanceSettings.current
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd -> appearanceSettings.misskey.swipeRight
                    SwipeToDismissBoxValue.EndToStart -> appearanceSettings.misskey.swipeLeft
                    else -> null
                }?.let {
                    when (it) {
                        AppearanceSettings.Misskey.SwipeActions.NONE -> Unit
                        AppearanceSettings.Misskey.SwipeActions.REPLY ->
                            event.onReplyClick(currentData, uriHandler)

                        AppearanceSettings.Misskey.SwipeActions.RENOTE ->
                            event.onReblogClick(currentData)

                        AppearanceSettings.Misskey.SwipeActions.FAVOURITE ->
                            event.onAddReactionClick(currentData, uriHandler)
                    }
                }
                false
            },
        )

    OptionalSwipeToDismissBox(
        state = dismissState,
        enabled =
            appearanceSettings.swipeGestures &&
                (
                    appearanceSettings.misskey.swipeLeft != AppearanceSettings.Misskey.SwipeActions.NONE ||
                        appearanceSettings.misskey.swipeRight != AppearanceSettings.Misskey.SwipeActions.NONE
                ),
        backgroundContent = {
            val alignment =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            val action =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> appearanceSettings.misskey.swipeRight
                    SwipeToDismissBoxValue.EndToStart -> appearanceSettings.misskey.swipeLeft
                    SwipeToDismissBoxValue.Settled -> null
                }
            if (action != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = screenHorizontalPadding),
                    contentAlignment = alignment,
                ) {
                    StatusSwipeButton(
                        action = action,
                    )
                }
            }
        },
        enableDismissFromEndToStart = appearanceSettings.misskey.swipeLeft != AppearanceSettings.Misskey.SwipeActions.NONE,
        enableDismissFromStartToEnd = appearanceSettings.misskey.swipeRight != AppearanceSettings.Misskey.SwipeActions.NONE,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable {
                        event.onStatusClick(data, uriHandler)
                    }
                    .background(MaterialTheme.colorScheme.background)
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
            )
            if (actualData.media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (appearanceSettings.showMedia || showMedia) {
                    StatusMediaComponent(
                        data = actualData.media,
                        onMediaClick = {
                            event.onMediaClick(it, uriHandler)
                        },
                        sensitive = actualData.sensitive,
                    )
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMedia = true
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(id = R.string.show_media),
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.show_media),
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .alpha(MediumAlpha),
                        )
                    }
                }
            }
//        StatusCardComponent(
//            data = actualData,
//            event = event
//        )
            actualData.quote?.let { quote ->
                Spacer(modifier = Modifier.height(8.dp))
                UiStatusQuoted(
                    status = quote,
                    onMediaClick = {
                        event.onMediaClick(it, uriHandler)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (actualData.reaction.emojiReactions.isNotEmpty() && appearanceSettings.misskey.showReaction) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusReactionComponent(
                    data = actualData,
                    event = event,
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
}

@Composable
private fun StatusSwipeButton(
    action: AppearanceSettings.Misskey.SwipeActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = stringResource(id = action.id),
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(id = action.id),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StatusFooterComponent(
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val actualData = data.renote ?: data
    var showRenoteMenu by remember {
        mutableStateOf(false)
    }
    var showMoreMenu by remember {
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
                text = actualData.matrices.humanizedReplyCount,
                modifier =
                    Modifier
                        .weight(1f),
                onClicked = {
                    event.onReplyClick(actualData, uriHandler)
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
                                event.onQuoteClick(actualData, uriHandler)
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
                    event.onAddReactionClick(actualData, uriHandler)
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
                        if (actualData.isFromMe) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(id = R.string.misskey_item_action_delete),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    event.onDeleteClick(actualData, uriHandler)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(id = R.string.misskey_item_action_report),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    event.onReportClick(actualData, uriHandler)
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
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .alpha(MediumAlpha)
                            .clickable {
                                expanded = !expanded
                            },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(id = R.string.mastodon_item_content_warning),
                        modifier =
                            Modifier
                                .size(12.dp)
                                .alpha(MediumAlpha),
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
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
    data: UiStatus.Misskey.Poll,
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
    data: UiStatus.Misskey,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it, uriHandler) },
        modifier = modifier,
    ) {
        if (LocalAppearanceSettings.current.misskey.showVisibility) {
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
    fun onStatusClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onUserClick(
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    )

    fun onMediaClick(
        media: UiMedia,
        uriHandler: UriHandler,
    )

    fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    )

    fun onReplyClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onReblogClick(data: UiStatus.Misskey)

    fun onQuoteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onAddReactionClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onDeleteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    )
}

internal class DefaultMisskeyStatusEvent(
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
) : MisskeyStatusEvent {
    override fun onStatusClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.StatusRouteDestination(data.statusKey)
                .deeplink(),
        )
    }

    override fun onUserClick(
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination(userKey)
                .deeplink(),
        )
    }

    override fun onMediaClick(
        media: UiMedia,
        uriHandler: UriHandler,
    ) {
        if (media is UiMedia.Image) {
            uriHandler.openUri(
                dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(media.url)
                    .deeplink(),
            )
        }
    }

    override fun onReactionClick(
        data: UiStatus.Misskey,
        reaction: UiStatus.Misskey.EmojiReaction,
    ) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Misskey ?: return@launch
            runCatching {
                account.dataSource.react(data, reaction.name)
            }.onFailure {
            }
        }
    }

    override fun onReplyClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.ReplyRouteDestination(data.statusKey)
                .deeplink(),
        )
    }

    override fun onReblogClick(data: UiStatus.Misskey) {
        scope.launch {
            val account =
                accountRepository.get(data.accountKey) as? UiAccount.Misskey ?: return@launch
            runCatching {
                account.dataSource.renote(data)
            }.onFailure {
            }
        }
    }

    override fun onQuoteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.QuoteDestination(data.statusKey)
                .deeplink(),
        )
    }

    override fun onAddReactionClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.MisskeyReactionRouteDestination(
                statusKey = data.statusKey,
            ).deeplink(),
        )
    }

    override fun onDeleteClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.DeleteStatusConfirmRouteDestination(data.statusKey)
                .deeplink(),
        )
    }

    override fun onReportClick(
        data: UiStatus.Misskey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.MisskeyReportRouteDestination(
                userKey = data.user.userKey,
                statusKey = data.statusKey,
            ).deeplink(),
        )
    }
}
