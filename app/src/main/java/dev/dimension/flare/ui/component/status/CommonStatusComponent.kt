package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import com.fleeksoft.ksoup.nodes.Element
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
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.localizedFullTime
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.screen.status.statusTranslatePresenter
import dev.dimension.flare.ui.theme.MediumAlpha
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter

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
            Modifier
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
                }.then(modifier),
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
                    content = item.content,
                    contentWarning = item.contentWarning,
                    poll = item.poll,
                    maxLines = Int.MAX_VALUE,
                )
            }
        } else {
            StatusContentComponent(
                content = item.content,
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
                Spacer(modifier = Modifier.height(8.dp))
                StatusCardComponent(
                    card = card,
                    modifier =
                        Modifier
                            .clickable {
                                uriHandler.openUri(card.url)
                            }.fillMaxWidth(),
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
            } else {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MediumAlpha),
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                ) {
                    StatusActions(item.actions)
                }
            }
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
            onMediaClick = { media ->
                item.onMediaClicked.invoke(
                    ClickContext(
                        launcher = {
                            uriLauncher.openUri(it)
                        },
                    ),
                    media,
                    item.images.indexOf(media),
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
    Box(
        modifier =
            modifier
                .border(
                    FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = MaterialTheme.shapes.medium,
                ).clip(
                    shape = MaterialTheme.shapes.medium,
                ),
    ) {
        Column {
            quotes.forEachIndexed { index, quote ->
                QuotedStatus(
                    data = quote,
                    onMediaClick = { media ->
                        quote.onMediaClicked.invoke(
                            ClickContext(
                                launcher = {
                                    uriLauncher.openUri(it)
                                },
                            ),
                            media,
                            quote.images.indexOf(media),
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
                    if (reaction.isUnicode) {
                        Text(reaction.name)
                    } else {
                        EmojiImage(
                            uri = reaction.url,
                            modifier = Modifier.height(16.dp),
                        )
                    }
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
    contentWarning: UiRichText?,
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
                    ) { closeMenu, isMenuShown ->
                        action.actions.forEach { subActions ->
                            when (subActions) {
                                is StatusAction.Item -> {
                                    StatusActionItemMenu(subActions, closeMenu)
                                }

                                is StatusAction.AsyncActionItem -> {
                                    if (isMenuShown) {
                                        val state by subActions.flow.collectAsUiState()
                                        state
                                            .onSuccess {
                                                StatusActionItemMenu(it, closeMenu)
                                            }.onLoading {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "Loading",
                                                            modifier =
                                                                Modifier.placeholder(
                                                                    true,
                                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                                ),
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        FAIcon(
                                                            imageVector = FontAwesomeIcons.Solid.Ellipsis,
                                                            contentDescription = "Loading",
                                                            modifier =
                                                                Modifier
                                                                    .size(
                                                                        with(LocalDensity.current) {
                                                                            LocalTextStyle.current.fontSize.toDp() +
                                                                                4.dp
                                                                        },
                                                                    ).placeholder(
                                                                        true,
                                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                                    ),
                                                        )
                                                    },
                                                    onClick = {
                                                    },
                                                )
                                            }
                                    }
                                }

                                // nested group is not supported
                                is StatusAction.Group -> Unit
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

                // async action item is only supported in group
                is StatusAction.AsyncActionItem -> Unit
            }
        }
    }
}

@Composable
private fun StatusActionItemMenu(
    subActions: StatusAction.Item,
    closeMenu: () -> Unit,
) {
    val launcher = LocalUriHandler.current
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
            closeMenu.invoke()
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
    content: UiRichText,
    contentWarning: UiRichText?,
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
            if (it.raw.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HtmlText(
                        element = it.data,
                        layoutDirection = it.direction,
                    )
                    FilledTonalButton(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        onClick = {
                            expanded = !expanded
                        },
                    ) {
                        if (expanded) {
                            Text(stringResource(R.string.mastodon_item_show_less))
                        } else {
                            Text(stringResource(R.string.mastodon_item_show_more))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = expanded || contentWarning?.raw.isNullOrEmpty()) {
            Column {
                if (!content.isEmpty) {
                    HtmlText(
                        element = content.data,
                        layoutDirection = content.direction,
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
    val selectedOptions =
        remember {
            mutableStateListOf(*poll.ownVotes.toTypedArray())
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        poll.options.forEachIndexed { index, option ->
            PollOption(
                option = option,
                modifier = Modifier.fillMaxWidth(),
                canVote = poll.canVote,
                multiple = poll.multiple,
                selected = selectedOptions.contains(index),
                onClick = {
                    if (poll.multiple) {
                        if (selectedOptions.contains(index)) {
                            selectedOptions.remove(index)
                        } else {
                            selectedOptions.add(index)
                        }
                    } else {
                        selectedOptions.clear()
                        selectedOptions.add(index)
                    }
                },
            )
        }
        if (poll.expired) {
            Text(
                text = stringResource(id = R.string.poll_expired),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .alpha(MediumAlpha),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text =
                    stringResource(
                        id = R.string.poll_expired_at,
                        poll.expiredAt.value.localizedFullTime,
                    ),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .alpha(MediumAlpha),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (poll.canVote) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    poll.onVote.invoke(selectedOptions.toImmutableList())
                },
            ) {
                Text(
                    text = stringResource(id = R.string.vote),
                )
            }
        }
    }
}

@Composable
private fun PollOption(
    canVote: Boolean,
    multiple: Boolean,
    option: UiPoll.Option,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(IntrinsicSize.Min)
                .border(
                    width = FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = MaterialTheme.shapes.small,
                )
//            .background(
//                color = MaterialTheme.colorScheme.secondaryContainer,
//                shape = MaterialTheme.shapes.small,
//            )
                .clip(
                    shape = MaterialTheme.shapes.small,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(option.percentage)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ),
        )
        val mutableInteractionSource =
            remember {
                MutableInteractionSource()
            }
        ListComponent(
            modifier =
                Modifier.clickable(
                    onClick = onClick,
                    interactionSource = mutableInteractionSource,
                    indication = LocalIndication.current,
                    enabled = canVote,
                ),
            headlineContent = {
                Text(
                    text = option.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            },
            trailingContent = {
                if (canVote || selected) {
                    if (multiple) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                onClick.invoke()
                            },
                            interactionSource = mutableInteractionSource,
                            enabled = canVote,
                        )
                    } else {
                        RadioButton(
                            selected = selected,
                            onClick = onClick,
                            interactionSource = mutableInteractionSource,
                            enabled = canVote,
                        )
                    }
                } else {
                    // keep the height consist
                    RadioButton(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.alpha(0f),
                    )
                }
            },
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
    Column(
        modifier =
            Modifier
                .border(
                    FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = MaterialTheme.shapes.medium,
                ).clip(
                    shape = MaterialTheme.shapes.medium,
                ).then(modifier),
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

@Composable
fun CompatCard(
    card: UiCard,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            Modifier
                .border(
                    FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = MaterialTheme.shapes.medium,
                ).clip(
                    shape = MaterialTheme.shapes.medium,
                ).then(modifier),
    ) {
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

internal object FlareDividerDefaults {
    val color: Color
        @Composable
        get() = DividerDefaults.color.copy(alpha = 0.87f)

    val thickness = 0.8.dp
}
