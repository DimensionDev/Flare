package dev.dimension.flare.ui.component.status.mastodon

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.material3.SwipeToDismissValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissState
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
import androidx.compose.ui.graphics.Color
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
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.OptionalSwipeToDismissBox
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
import dev.dimension.flare.ui.theme.screenHorizontalPadding
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
private fun StatusSwipeButton(
    action: AppearanceSettings.Mastodon.SwipeActions,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MastodonStatusComponent(
    data: UiStatus.Mastodon,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    val actualData by rememberUpdatedState(newValue = data.reblogStatus ?: data)
    val uriHandler = LocalUriHandler.current
    var showMedia by remember { mutableStateOf(false) }
    val appearanceSettings = LocalAppearanceSettings.current
    val dismissState =
        rememberSwipeToDismissState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissValue.StartToEnd -> appearanceSettings.mastodon.swipeRight
                    SwipeToDismissValue.EndToStart -> appearanceSettings.mastodon.swipeLeft
                    else -> null
                }?.let {
                    when (it) {
                        AppearanceSettings.Mastodon.SwipeActions.REBLOG ->
                            event.onReblogClick(actualData)

                        AppearanceSettings.Mastodon.SwipeActions.FAVOURITE ->
                            event.onLikeClick(actualData)

                        AppearanceSettings.Mastodon.SwipeActions.BOOKMARK ->
                            event.onBookmarkClick(actualData)

                        AppearanceSettings.Mastodon.SwipeActions.REPLY ->
                            event.onReplyClick(actualData, uriHandler)

                        AppearanceSettings.Mastodon.SwipeActions.NONE -> Unit
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
                    appearanceSettings.mastodon.swipeLeft != AppearanceSettings.Mastodon.SwipeActions.NONE ||
                        appearanceSettings.mastodon.swipeRight != AppearanceSettings.Mastodon.SwipeActions.NONE
                ),
        backgroundContent = {
            val alignment =
                when (dismissState.dismissDirection) {
                    SwipeToDismissValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissValue.Settled -> Alignment.Center
                }
            val action =
                when (dismissState.dismissDirection) {
                    SwipeToDismissValue.StartToEnd -> appearanceSettings.mastodon.swipeRight
                    SwipeToDismissValue.EndToStart -> appearanceSettings.mastodon.swipeLeft
                    SwipeToDismissValue.Settled -> null
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
        enableDismissFromEndToStart = appearanceSettings.mastodon.swipeLeft != AppearanceSettings.Mastodon.SwipeActions.NONE,
        enableDismissFromStartToEnd = appearanceSettings.mastodon.swipeRight != AppearanceSettings.Mastodon.SwipeActions.NONE,
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
    val uriHandler = LocalUriHandler.current
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
                                    event.onDeleteClick(actualData, uriHandler)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(id = R.string.mastodon_item_report))
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
                                .size(12.dp),
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
    val uriHandler = LocalUriHandler.current
    CommonStatusHeaderComponent(
        data = data.user,
        onUserClick = { event.onUserClick(it, uriHandler) },
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
    fun onUserClick(
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    )

    fun onStatusClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReplyClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReblogClick(status: UiStatus.Mastodon)

    fun onLikeClick(status: UiStatus.Mastodon)

    fun onBookmarkClick(status: UiStatus.Mastodon)

    fun onMediaClick(
        media: UiMedia,
        uriHandler: UriHandler,
    )

    fun onDeleteClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )

    fun onReportClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    )
}

internal class DefaultMastodonStatusEvent(
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
) : MastodonStatusEvent {
    override fun onUserClick(
        userKey: MicroBlogKey,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(ProfileRouteDestination(userKey).deeplink())
    }

    override fun onStatusClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(StatusRouteDestination(status.statusKey).deeplink())
    }

    override fun onReplyClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(ReplyRouteDestination(status.statusKey).deeplink())
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

    override fun onMediaClick(
        media: UiMedia,
        uriHandler: UriHandler,
    ) {
        if (media is UiMedia.Image) {
            uriHandler.openUri(MediaRouteDestination(media.url).deeplink())
        }
    }

    override fun onDeleteClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.DeleteStatusConfirmRouteDestination(
                status.statusKey,
            ).deeplink(),
        )
    }

    override fun onReportClick(
        status: UiStatus.Mastodon,
        uriHandler: UriHandler,
    ) {
        uriHandler.openUri(
            dev.dimension.flare.ui.screen.destinations.MastodonReportRouteDestination(
                userKey = status.user.userKey,
                statusKey = status.statusKey,
            ).deeplink(),
        )
    }
}
