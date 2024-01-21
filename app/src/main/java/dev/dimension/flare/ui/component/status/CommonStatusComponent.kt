package dev.dimension.flare.ui.component.status

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import moe.tlaster.ktml.dom.Element

// damm the parameters are soooooooooooooooo looooooooooooong
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonStatusComponent(
    rawContent: String,
    content: Element,
    contentDirection: LayoutDirection,
    user: UiUser,
    medias: ImmutableList<UiMedia>,
    humanizedTime: String,
    onMediaClick: (UiMedia) -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
    contentWarning: String? = null,
    card: UiCard? = null,
    quotedStatus: UiStatus? = null,
    poll: UiPoll? = null,
    headerIcon: ImageVector? = null,
    headerUser: UiUser? = null,
    @StringRes headerTextId: Int? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    contentFooter: @Composable ColumnScope.() -> Unit = {},
    statusActions: @Composable RowScope.() -> Unit = {},
    swipeLeftText: String? = null,
    swipeLeftIcon: ImageVector? = null,
    onSwipeLeft: (() -> Unit)? = null,
    swipeRightText: String? = null,
    swipeRightIcon: ImageVector? = null,
    onSwipeRight: (() -> Unit)? = null,
) {
    var showMedia by remember { mutableStateOf(false) }
    val appearanceSettings = LocalAppearanceSettings.current
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onSwipeRight?.invoke()
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        onSwipeLeft?.invoke()
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
                (onSwipeLeft != null || onSwipeRight != null),
        backgroundContent = {
            val alignment =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            val actualAction =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> onSwipeRight
                    SwipeToDismissBoxValue.EndToStart -> onSwipeLeft
                    SwipeToDismissBoxValue.Settled -> null
                }
            val actualText =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> swipeRightText
                    SwipeToDismissBoxValue.EndToStart -> swipeLeftText
                    SwipeToDismissBoxValue.Settled -> null
                }
            val actualIcon =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> swipeRightIcon
                    SwipeToDismissBoxValue.EndToStart -> swipeLeftIcon
                    SwipeToDismissBoxValue.Settled -> null
                }
            if (actualAction != null && actualText != null && actualIcon != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = screenHorizontalPadding),
                    contentAlignment = alignment,
                ) {
                    StatusSwipeButton(
                        text = actualText,
                        icon = actualIcon,
                    )
                }
            }
        },
    ) {
        Column {
            if (headerIcon != null && headerUser != null && headerTextId != null) {
                StatusRetweetHeaderComponent(
                    icon = headerIcon,
                    user = headerUser,
                    text = stringResource(id = headerTextId),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            StatusHeaderComponent(
                user = user,
                humanizedTime = humanizedTime,
                onUserClick = { onUserClick(it) },
                headerTrailing = headerTrailing,
            )

            StatusContentComponent(
                rawContent = rawContent,
                content = content,
                contentDirection = contentDirection,
                contentWarning = contentWarning,
                poll = poll,
            )

            if (medias.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (appearanceSettings.showMedia || showMedia) {
                    StatusMediaComponent(
                        data = medias,
                        onMediaClick = onMediaClick,
                        sensitive = sensitive,
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
            card?.let { card ->
                if (appearanceSettings.showLinkPreview) {
                    StatusCardComponent(
                        card = card,
                    )
                }
            }

            if (quotedStatus != null) {
                Spacer(modifier = Modifier.height(4.dp))
                UiStatusQuoted(quotedStatus, onMediaClick)
            }

            contentFooter.invoke(this)

            if (appearanceSettings.showActions) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides LocalContentColor.current.copy(alpha = MediumAlpha),
                    ) {
                        statusActions.invoke(this)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
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
    text: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(36.dp),
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun StatusContentComponent(
    rawContent: String,
    content: Element,
    contentDirection: LayoutDirection,
    contentWarning: String?,
    poll: UiPoll?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    Column(
        modifier = modifier,
    ) {
        contentWarning?.let {
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
        AnimatedVisibility(visible = expanded || contentWarning.isNullOrEmpty()) {
            Column {
                if (rawContent.isNotEmpty() && rawContent.isNotBlank()) {
                    HtmlText2(
                        element = content,
                        layoutDirection = contentDirection,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                poll?.let {
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
