package dev.dimension.flare.ui.component.status

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.contentDirection
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.ktml.dom.Element

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonStatusComponent(
    data: CommonStatusComponentData,
    action: CommonStatusComponentAction,
    onMediaClick: (UiMedia) -> Unit,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable RowScope.() -> Unit = {},
) {
    var showMedia by remember { mutableStateOf(false) }
    val appearanceSettings = LocalAppearanceSettings.current
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        action.startToEndSwipeActions?.onClick?.invoke()
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        action.endToStartSwipeActions?.onClick?.invoke()
                    }

                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false
            },
        )
    OptionalSwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enabled =
            appearanceSettings.swipeGestures &&
                (action.startToEndSwipeActions != null || action.endToStartSwipeActions != null),
        backgroundContent = {
            val alignment =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            val action =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> action.startToEndSwipeActions
                    SwipeToDismissBoxValue.EndToStart -> action.endToStartSwipeActions
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
    ) {
        Column {
            if (data.header != null) {
                StatusRetweetHeaderComponent(
                    icon = data.header.icon,
                    user = data.header.user,
                    text = stringResource(id = data.header.textId),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            StatusHeaderComponent(
                user = data.user,
                humanizedTime = data.humanizedTime,
                onUserClick = { },
                headerTrailing = headerTrailing,
            )

            StatusContentComponent(
                data = data,
            )

            if (data.medias.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (appearanceSettings.showMedia || showMedia) {
                    StatusMediaComponent(
                        data = data.medias,
                        onMediaClick = onMediaClick,
                        sensitive = data.sensitive,
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
            if (appearanceSettings.showLinkPreview && data.card != null) {
                StatusCardComponent(
                    card = data.card,
                )
            }

            if (appearanceSettings.showActions) {
                StatusFooterComponent(
                    actions = action.actions,
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Immutable
data class CommonStatusComponentAction(
    val actions: ImmutableList<StatusAction>,
    val startToEndSwipeActions: StatusAction.Item?,
    val endToStartSwipeActions: StatusAction.Item?,
) {
    companion object {
        @Composable
        internal fun fromUiStatus(
            data: UiStatus,
            statusEvent: StatusEvent,
        ): CommonStatusComponentAction {
            return when (data) {
                is UiStatus.Bluesky ->
                    buildBluesky(data, statusEvent)

                is UiStatus.BlueskyNotification -> TODO()
                is UiStatus.Mastodon -> TODO()
                is UiStatus.MastodonNotification -> TODO()
                is UiStatus.Misskey -> TODO()
                is UiStatus.MisskeyNotification -> TODO()
            }
        }

        @Composable
        private fun buildBluesky(
            data: UiStatus.Bluesky,
            statusEvent: StatusEvent,
        ): CommonStatusComponentAction {
            val uriHandler = LocalUriHandler.current
            val appearanceSettings = LocalAppearanceSettings.current
            return CommonStatusComponentAction(
                actions =
                    persistentListOf(
                        StatusAction.Item(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            text = data.matrices.humanizedReplyCount,
                            onClick = {
                                statusEvent.blueskyStatusEvent.onReplyClick(data, uriHandler)
                            },
                        ),
                        StatusAction.Group(
                            icon = Icons.Default.SyncAlt,
                            text = data.matrices.humanizedRepostCount,
                            color = if (data.reaction.reposted) StatusAction.ColorToken.Primary else null,
                            items =
                                persistentListOf(
                                    StatusAction.Item(
                                        icon = Icons.Default.SyncAlt,
                                        text = stringResource(id = R.string.blusky_item_action_repost),
                                        onClick = {
                                            statusEvent.blueskyStatusEvent.onReblogClick(data)
                                        },
                                    ),
                                    StatusAction.Item(
                                        icon = Icons.Default.FormatQuote,
                                        text = stringResource(id = R.string.blusky_item_action_quote),
                                        onClick = {
                                            statusEvent.blueskyStatusEvent.onQuoteClick(data, uriHandler)
                                        },
                                    ),
                                ),
                        ),
                        StatusAction.Item(
                            icon =
                                if (data.reaction.liked) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                            text = data.matrices.humanizedLikeCount,
                            onClick = {
                                statusEvent.blueskyStatusEvent.onLikeClick(data)
                            },
                            color = if (data.reaction.liked) StatusAction.ColorToken.Red else null,
                        ),
                        StatusAction.Group(
                            icon = Icons.Default.MoreHoriz,
                            text = null,
                            items =
                                persistentListOf(
                                    if (data.isFromMe) {
                                        StatusAction.Item(
                                            icon = Icons.Default.Delete,
                                            text = stringResource(id = R.string.blusky_item_action_delete),
                                            onClick = {
                                                statusEvent.blueskyStatusEvent.onDeleteClick(data, uriHandler)
                                            },
                                        )
                                    } else {
                                        StatusAction.Item(
                                            icon = Icons.Default.Report,
                                            text = stringResource(id = R.string.blusky_item_action_report),
                                            onClick = {
                                                statusEvent.blueskyStatusEvent.onReportClick(data, uriHandler)
                                            },
                                        )
                                    },
                                ),
                        ),
                    ),
                startToEndSwipeActions =
                    appearanceSettings.bluesky.swipeRight.takeIf {
                        it != AppearanceSettings.Bluesky.SwipeActions.NONE
                    }?.let {
                        StatusAction.Item(
                            icon = it.icon,
                            text = stringResource(id = it.id),
                            onClick = {
                                when (it) {
                                    AppearanceSettings.Bluesky.SwipeActions.NONE -> Unit
                                    AppearanceSettings.Bluesky.SwipeActions.REPLY ->
                                        statusEvent.blueskyStatusEvent.onReplyClick(data, uriHandler)

                                    AppearanceSettings.Bluesky.SwipeActions.REBLOG ->
                                        statusEvent.blueskyStatusEvent.onReblogClick(data)

                                    AppearanceSettings.Bluesky.SwipeActions.FAVOURITE ->
                                        statusEvent.blueskyStatusEvent.onLikeClick(data)
                                }
                            },
                        )
                    },
                endToStartSwipeActions =
                    appearanceSettings.bluesky.swipeLeft.takeIf {
                        it != AppearanceSettings.Bluesky.SwipeActions.NONE
                    }?.let {
                        StatusAction.Item(
                            icon = it.icon,
                            text = stringResource(id = it.id),
                            onClick = {
                                when (it) {
                                    AppearanceSettings.Bluesky.SwipeActions.NONE -> Unit
                                    AppearanceSettings.Bluesky.SwipeActions.REPLY ->
                                        statusEvent.blueskyStatusEvent.onReplyClick(data, uriHandler)

                                    AppearanceSettings.Bluesky.SwipeActions.REBLOG ->
                                        statusEvent.blueskyStatusEvent.onReblogClick(data)

                                    AppearanceSettings.Bluesky.SwipeActions.FAVOURITE ->
                                        statusEvent.blueskyStatusEvent.onLikeClick(data)
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Immutable
data class CommonStatusComponentData(
    val header: Header?,
    val rawContent: String,
    val content: Element,
    val contentDirection: LayoutDirection,
    val contentWarning: String?,
    val user: UiUser,
    val medias: ImmutableList<UiMedia>,
    val card: UiCard?,
    val humanizedTime: String,
    val sensitive: Boolean,
    val quotedStatus: UiStatus?,
    val poll: UiPoll?,
) {
    data class Header(
        val icon: ImageVector,
        val user: UiUser?,
        @StringRes val textId: Int,
    )

    companion object {
        internal fun fromUiStatus(data: UiStatus) =
            when (data) {
                is UiStatus.Bluesky ->
                    CommonStatusComponentData(
                        header =
                            data.repostBy?.let {
                                Header(
                                    icon = Icons.Default.SyncAlt,
                                    user = it,
                                    textId = R.string.mastodon_item_reblogged_status,
                                )
                            },
                        rawContent = data.content,
                        content = data.contentToken,
                        contentDirection = data.contentDirection,
                        contentWarning = null,
                        user = data.user,
                        medias = data.medias,
                        card = data.card,
                        humanizedTime = data.humanizedTime,
                        sensitive = false,
                        quotedStatus = data.quote,
                        poll = null,
                    )

                is UiStatus.BlueskyNotification -> TODO()
                is UiStatus.Mastodon -> TODO()
                is UiStatus.MastodonNotification -> TODO()
                is UiStatus.Misskey -> TODO()
                is UiStatus.MisskeyNotification -> TODO()
            }
    }
}

sealed interface StatusAction {
    @Immutable
    data class Item(
        val icon: ImageVector,
        val text: String?,
        val enabled: Boolean = true,
        val color: ColorToken? = null,
        val onClick: () -> Unit,
    ) : StatusAction

    @Immutable
    data class Group(
        val icon: ImageVector,
        val text: String?,
        val enabled: Boolean = true,
        val color: ColorToken? = null,
        val items: ImmutableList<Item>,
    ) : StatusAction

    enum class ColorToken {
        Primary,
        Red,
        ;

        @Composable
        fun toColor(): Color {
            return when (this) {
                Primary -> MaterialTheme.colorScheme.primary
                Red -> Color.Red
            }
        }
    }
}

@Composable
private fun StatusHeaderComponent(
    user: UiUser,
    humanizedTime: String,
    onUserClick: (MicroBlogKey) -> Unit,
    headerTrailing: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    CommonStatusHeaderComponent(
        data = user,
        onUserClick = { onUserClick(it) },
        modifier = modifier,
    ) {
        headerTrailing.invoke(this)
        Text(
            text = humanizedTime,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .alpha(MediumAlpha),
        )
    }
}

@Composable
private fun StatusSwipeButton(
    action: StatusAction.Item,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.text,
            modifier = Modifier.size(36.dp),
        )
        if (action.text != null) {
            Text(
                text = action.text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun StatusContentComponent(
    data: CommonStatusComponentData,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(
        modifier = modifier,
    ) {
        data.contentWarning?.let {
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
        AnimatedVisibility(visible = expanded || data.contentWarning.isNullOrEmpty()) {
            Column {
                if (data.rawContent.isNotEmpty() && data.rawContent.isNotBlank()) {
                    HtmlText2(
                        element = data.content,
                        layoutDirection = data.contentDirection,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                data.poll?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusPollComponent(
                        poll = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPollComponent(
    poll: UiPoll,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        poll.options.forEach { option ->
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
            text = poll.humanizedExpiresAt,
        )
    }
}

@Composable
private fun StatusCardComponent(
    card: UiCard,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
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

@Composable
private fun StatusFooterComponent(
    actions: ImmutableList<StatusAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalContentColor.current.copy(alpha = MediumAlpha),
        ) {
            actions.forEach {
                when (it) {
                    is StatusAction.Item -> {
                        StatusActionButton(
                            icon = it.icon,
                            text = it.text,
                            modifier =
                                Modifier
                                    .weight(1f),
                            onClicked = it.onClick,
                            color = it.color?.toColor() ?: LocalContentColor.current,
                            enabled = it.enabled,
                        )
                    }

                    is StatusAction.Group -> {
                        StatusActionGroupComponent(
                            action = it,
                            modifier =
                                Modifier
                                    .weight(1f),
                        )
                    }
                }
            }
        }
    }
}
