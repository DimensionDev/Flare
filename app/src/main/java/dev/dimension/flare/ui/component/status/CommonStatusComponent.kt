package dev.dimension.flare.ui.component.status

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.fleeksoft.ksoup.nodes.Element
import com.ramcosta.composedestinations.generated.destinations.StatusMediaRouteDestination
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.regular.Bookmark
import compose.icons.fontawesomeicons.regular.Heart
import compose.icons.fontawesomeicons.solid.At
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.Ellipsis
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.Image
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.LockOpen
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.QuoteLeft
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.Retweet
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.localizedFullTime
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.screen.status.statusTranslatePresenter
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.collections.immutable.ImmutableList

@Composable
fun CommonStatusComponent(
    item: UiTimeline.ItemContent.Status,
    isDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalAppearanceSettings.current
    Column(
        modifier =
            modifier
                .let {
                    if (isDetail) {
                        it
                    } else {
                        it.clickable {
                            item.onClicked.invoke(
                                ClickContext(
                                    launcher = { url ->
                                        uriHandler.openUri(url)
                                    },
                                ),
                            )
                        }
                    }
                },
    ) {
        item.user?.let { user ->
            CommonStatusHeaderComponent(
                data = user,
                onUserClick = {
                    user.onClicked.invoke(
                        ClickContext(
                            launcher = {
                                uriHandler.openUri(it)
                            },
                        ),
                    )
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (val content = item.topEndContent) {
                        is UiTimeline.ItemContent.Status.TopEndContent.Visibility -> {
                            StatusVisibilityComponent(
                                visibility = content.visibility,
                                modifier =
                                    Modifier
                                        .size(14.dp)
                                        .alpha(MediumAlpha),
                            )
                        }

                        null -> Unit
                    }
                    if (!isDetail) {
                        Text(
                            text = item.createdAt.shortTime.localizedShortTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        when (val content = item.aboveTextContent) {
            is UiTimeline.ItemContent.Status.AboveTextContent.ReplyTo -> {
                Spacer(modifier = Modifier.height(4.dp))
                StatusReplyComponent(
                    replyHandle = content.handle,
                )
            }

            null -> Unit
        }
        if (isDetail) {
            SelectionContainer {
                StatusContentComponent(
                    rawContent = item.content.innerText,
                    content = item.content.data,
                    contentDirection = item.content.direction,
                    contentWarning = item.contentWarning,
                    poll = item.poll,
                    maxLines = Int.MAX_VALUE,
                )
            }
        } else {
            StatusContentComponent(
                rawContent = item.content.innerText,
                content = item.content.data,
                contentDirection = item.content.direction,
                contentWarning = item.contentWarning,
                poll = item.poll,
                maxLines = 6,
            )
        }

        if (isDetail) {
            TranslationComponent(
                statusKey = item.statusKey,
                contentWarning = item.contentWarning,
                rawContent = item.content.innerText,
                content = item.content.data,
            )
        }

        if (item.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMediasComponent(appearanceSettings, item)
        }
        item.card?.let { card ->
            if (appearanceSettings.showLinkPreview && item.images.isEmpty() && item.quote.isEmpty()) {
                StatusCardComponent(
                    card = card,
                )
            }
        }
        if (item.quote.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            StatusQuoteComponent(
                quotes = item.quote,
            )
        }

        when (val content = item.bottomContent) {
            is UiTimeline.ItemContent.Status.BottomContent.Reaction ->
                StatusReactionComponent(
                    data = content,
                )

            null -> Unit
        }

        if (isDetail) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.createdAt.value.localizedFullTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (appearanceSettings.showActions || isDetail) {
            Spacer(modifier = Modifier.height(8.dp))
            if (isDetail) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    StatusActions(item.actions)
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MediumAlpha),
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                ) {
                    StatusActions(item.actions)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusMediasComponent(
    appearanceSettings: AppearanceSettings,
    item: UiTimeline.ItemContent.Status,
) {
    val uriLauncher = LocalUriHandler.current
    var showMedia by remember { mutableStateOf(false) }
    if (appearanceSettings.showMedia || showMedia) {
        StatusMediaComponent(
            data = item.images,
            onMediaClick = {
                uriLauncher.openUri(
                    StatusMediaRouteDestination(
                        statusKey = item.statusKey,
                        index = item.images.indexOf(it),
                        preview =
                            when (it) {
                                is UiMedia.Image -> it.previewUrl
                                is UiMedia.Video -> it.thumbnailUrl
                                is UiMedia.Gif -> it.previewUrl
                                else -> null
                            },
                        accountType = AccountType.Specific(item.accountKey),
                    ).deeplink(),
                )
            },
            sensitive = item.sensitive,
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
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Image,
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

@Composable
private fun StatusQuoteComponent(
    quotes: ImmutableList<UiTimeline.ItemContent.Status>,
    modifier: Modifier = Modifier,
) {
    val uriLauncher = LocalUriHandler.current
    Card(
        modifier = modifier,
    ) {
        Column {
            quotes.forEachIndexed { index, quote ->
                QuotedStatus(
                    data = quote,
                    onMediaClick = {
                        uriLauncher.openUri(
                            StatusMediaRouteDestination(
                                statusKey = quote.statusKey,
                                index = quote.images.indexOf(it),
                                preview =
                                    when (it) {
                                        is UiMedia.Image -> it.previewUrl
                                        is UiMedia.Video -> it.thumbnailUrl
                                        is UiMedia.Gif -> it.previewUrl
                                        else -> null
                                    },
                                accountType = AccountType.Specific(quote.accountKey),
                            ).deeplink(),
                        )
                    },
                )
                if (index != quotes.lastIndex && quotes.size > 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatusReactionComponent(
    data: UiTimeline.ItemContent.Status.BottomContent.Reaction,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        items(data.emojiReactions) { reaction ->
            Card(
                shape = RoundedCornerShape(100),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clickable {
                                reaction.onClicked.invoke()
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
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
private fun TranslationComponent(
    statusKey: MicroBlogKey,
    contentWarning: String?,
    rawContent: String,
    content: Element,
) {
    var enabledTranslate by rememberSaveable("translate-$statusKey") {
        mutableStateOf(false)
    }
    TextButton(
        onClick = {
            if (!enabledTranslate) {
                enabledTranslate = true
            }
        },
    ) {
        Text(
            text =
                stringResource(
                    id = R.string.status_detail_translate,
                    Locale.current.platformLocale.displayLanguage,
                ),
        )
    }
    if (enabledTranslate) {
        Spacer(modifier = Modifier.height(4.dp))
        val state by producePresenter(
            "translate_${contentWarning}_$rawContent",
        ) {
            statusTranslatePresenter(contentWarning = contentWarning, content = content)
        }
        state.contentWarning
            ?.onSuccess {
                Text(text = it)
            }?.onLoading {
                Text(
                    text = "Lores ipsum dolor sit amet",
                    modifier = Modifier.placeholder(true),
                )
            }?.onError {
                Text(text = it.message ?: "Error")
            }
        state.text
            .onSuccess {
                Text(text = it)
            }.onLoading {
                Text(
                    text = "Lores ipsum dolor sit amet",
                    modifier = Modifier.placeholder(true),
                )
            }.onError {
                Text(text = it.message ?: "Error")
            }
    }
}

@Composable
internal fun StatusVisibilityComponent(
    visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Globe,
                contentDescription = stringResource(id = R.string.mastodon_visibility_public),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.LockOpen,
                contentDescription = stringResource(id = R.string.mastodon_visibility_unlisted),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Lock,
                contentDescription = stringResource(id = R.string.mastodon_visibility_private),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.At,
                contentDescription = stringResource(id = R.string.mastodon_visibility_direct),
                modifier = modifier,
            )
    }
}

@Composable
private fun StatusActions(
    items: ImmutableList<StatusAction>,
    modifier: Modifier = Modifier,
) {
    val launcher = LocalUriHandler.current
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, action ->
            if (index == items.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
            when (action) {
                is StatusAction.Group -> {
                    StatusActionGroup(
                        icon = action.displayItem.icon,
                        text = action.displayItem.iconText,
                        color = statusActionItemColor(item = action.displayItem),
                        withTextMinWidth = index != items.lastIndex,
                    ) {
                        action.actions.forEach { subActions ->
                            if (subActions is StatusAction.Item) {
                                val color = statusActionItemColor(subActions)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = subActions.icon,
                                            contentDescription = subActions.iconText,
                                            tint = color,
                                            modifier =
                                                Modifier
                                                    .size(with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() + 4.dp }),
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = statusActionItemText(item = subActions),
                                            color = color,
                                        )
                                    },
                                    onClick = {
                                        if (subActions is StatusAction.Item.Clickable) {
                                            subActions.onClicked.invoke(
                                                ClickContext(
                                                    launcher = {
                                                        launcher.openUri(it)
                                                    },
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                is StatusAction.Item -> {
                    StatusActionButton(
                        icon = action.icon,
                        text = action.iconText,
                        color = statusActionItemColor(item = action),
                        withTextMinWidth = index != items.lastIndex,
                        onClicked = {
                            if (action is StatusAction.Item.Clickable) {
                                action.onClicked.invoke(
                                    ClickContext(
                                        launcher = {
                                            launcher.openUri(it)
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private val StatusAction.Item.icon: ImageVector
    get() =
        when (this) {
            is StatusAction.Item.Bookmark -> {
                if (bookmarked) {
                    FontAwesomeIcons.Solid.Bookmark
                } else {
                    FontAwesomeIcons.Regular.Bookmark
                }
            }

            is StatusAction.Item.Delete -> FontAwesomeIcons.Solid.Trash
            is StatusAction.Item.Like -> {
                if (liked) {
                    FontAwesomeIcons.Solid.Heart
                } else {
                    FontAwesomeIcons.Regular.Heart
                }
            }

            StatusAction.Item.More -> FontAwesomeIcons.Solid.Ellipsis
            is StatusAction.Item.Quote -> FontAwesomeIcons.Solid.QuoteLeft
            is StatusAction.Item.Reaction -> {
                if (reacted) {
                    FontAwesomeIcons.Solid.Minus
                } else {
                    FontAwesomeIcons.Solid.Plus
                }
            }

            is StatusAction.Item.Reply -> FontAwesomeIcons.Solid.Reply
            is StatusAction.Item.Report -> FontAwesomeIcons.Solid.CircleInfo
            is StatusAction.Item.Retweet -> FontAwesomeIcons.Solid.Retweet
        }

private val StatusAction.Item.iconText: String?
    get() =
        when (this) {
            is StatusAction.Item.Bookmark -> humanizedCount
            is StatusAction.Item.Delete -> null
            is StatusAction.Item.Like -> humanizedCount
            StatusAction.Item.More -> null
            is StatusAction.Item.Quote -> humanizedCount
            is StatusAction.Item.Reaction -> null
            is StatusAction.Item.Reply -> humanizedCount
            is StatusAction.Item.Report -> null
            is StatusAction.Item.Retweet -> humanizedCount
        }

@Composable
private fun statusActionItemColor(item: StatusAction.Item) =
    if (item is StatusAction.Item.Colorized) {
        when (item.color) {
            StatusAction.Item.Colorized.Color.Red -> Color.Red
            StatusAction.Item.Colorized.Color.Error -> MaterialTheme.colorScheme.error
            StatusAction.Item.Colorized.Color.ContentColor -> LocalContentColor.current
            StatusAction.Item.Colorized.Color.PrimaryColor -> MaterialTheme.colorScheme.primary
        }
    } else {
        LocalContentColor.current
    }

@Composable
private fun statusActionItemText(item: StatusAction.Item) =
    when (item) {
        is StatusAction.Item.Bookmark -> {
            if (item.bookmarked) {
                stringResource(id = R.string.bookmark_remove)
            } else {
                stringResource(id = R.string.bookmark_add)
            }
        }

        is StatusAction.Item.Delete -> stringResource(id = R.string.delete)
        is StatusAction.Item.Like -> {
            if (item.liked) {
                stringResource(id = R.string.unlike)
            } else {
                stringResource(id = R.string.like)
            }
        }

        StatusAction.Item.More -> stringResource(id = R.string.more)
        is StatusAction.Item.Quote -> stringResource(id = R.string.quote)
        is StatusAction.Item.Reaction -> {
            if (item.reacted) {
                stringResource(id = R.string.reaction_remove)
            } else {
                stringResource(id = R.string.reaction_add)
            }
        }

        is StatusAction.Item.Reply -> stringResource(id = R.string.reply)
        is StatusAction.Item.Report -> stringResource(id = R.string.report)
        is StatusAction.Item.Retweet -> {
            if (item.retweeted) {
                stringResource(id = R.string.retweet_remove)
            } else {
                stringResource(id = R.string.retweet)
            }
        }
    }

@Composable
private fun StatusReplyComponent(
    replyHandle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .alpha(MediumAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.Reply,
            contentDescription = stringResource(id = R.string.reply_to),
            modifier =
                Modifier
                    .size(12.dp),
        )
        Text(
            text = stringResource(id = R.string.reply_to, replyHandle),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusContentComponent(
    rawContent: String,
    content: Element,
    contentDirection: LayoutDirection,
    contentWarning: String?,
    poll: UiPoll?,
    maxLines: Int,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Lock,
                        contentDescription = stringResource(id = R.string.mastodon_item_content_warning),
                    )
                    Text(
                        text = it,
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded || contentWarning.isNullOrEmpty()) {
            Column {
                if (rawContent.isNotEmpty() && rawContent.isNotBlank()) {
                    HtmlText(
                        element = content,
                        layoutDirection = contentDirection,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = maxLines,
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
            text = poll.expiresAt.localizedFullTime,
        )
    }
}

@Composable
private fun StatusCardComponent(
    card: UiCard,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalAppearanceSettings.current
    if (appearanceSettings.compatLinkPreview) {
        CompatCard(
            card = card,
            modifier = modifier,
        )
    } else {
        ExpandedCard(
            card = card,
            modifier = modifier,
        )
    }
}

@Composable
private fun ExpandedCard(
    card: UiCard,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalAppearanceSettings.current
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
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            card.media?.let {
                AdaptiveGrid(
                    content = {
                        MediaItem(
                            media = it,
                            keepAspectRatio = appearanceSettings.expandMediaSize,
                            modifier =
                                Modifier
                                    .clipToBounds(),
                        )
                    },
                    expandedSize = appearanceSettings.expandMediaSize,
                    modifier =
                        Modifier
                            .clipToBounds(),
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
fun CompatCard(
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
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Row {
                card.media?.let {
                    MediaItem(
                        media = it,
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clipToBounds(),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .padding(8.dp),
                ) {
                    Text(
                        text = card.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    card.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .alpha(MediumAlpha),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
