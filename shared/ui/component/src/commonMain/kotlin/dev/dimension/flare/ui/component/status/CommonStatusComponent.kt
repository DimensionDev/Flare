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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
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
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.PlatformShare
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.EmojiImage
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDividerDefaults
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.Res
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.bookmark_add
import dev.dimension.flare.ui.component.bookmark_remove
import dev.dimension.flare.ui.component.delete
import dev.dimension.flare.ui.component.like
import dev.dimension.flare.ui.component.mastodon_item_show_less
import dev.dimension.flare.ui.component.mastodon_item_show_more
import dev.dimension.flare.ui.component.mastodon_visibility_direct
import dev.dimension.flare.ui.component.mastodon_visibility_private
import dev.dimension.flare.ui.component.mastodon_visibility_public
import dev.dimension.flare.ui.component.mastodon_visibility_unlisted
import dev.dimension.flare.ui.component.more
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.component.platform.PlatformCheckbox
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuScope
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformRadioButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.poll_expired
import dev.dimension.flare.ui.component.poll_expired_at
import dev.dimension.flare.ui.component.quote
import dev.dimension.flare.ui.component.reaction_add
import dev.dimension.flare.ui.component.reaction_remove
import dev.dimension.flare.ui.component.reply
import dev.dimension.flare.ui.component.reply_to
import dev.dimension.flare.ui.component.report
import dev.dimension.flare.ui.component.retweet
import dev.dimension.flare.ui.component.retweet_remove
import dev.dimension.flare.ui.component.share
import dev.dimension.flare.ui.component.show_media
import dev.dimension.flare.ui.component.status_detail_tldr
import dev.dimension.flare.ui.component.status_detail_translate
import dev.dimension.flare.ui.component.unlike
import dev.dimension.flare.ui.component.vote
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
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
public fun CommonStatusComponent(
    item: UiTimeline.ItemContent.Status,
    isDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearanceSettings = LocalComponentAppearance.current
    val platformContext = LocalPlatformContext.current
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
                        PlatformText(
                            text = item.createdAt.shortTime.localizedShortTime,
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.caption,
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

        if (isDetail && !item.content.isEmpty) {
            TranslationComponent(
                statusKey = item.statusKey,
                contentWarning = item.contentWarning,
                rawContent = item.content.innerText,
                content = item.content,
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
                            .pointerHoverIcon(PointerIcon.Hand)
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
            is UiTimeline.ItemContent.Status.BottomContent.Reaction -> {
                Spacer(modifier = Modifier.height(4.dp))
                StatusReactionComponent(
                    data = content,
                )
            }

            null -> Unit
        }

        if (isDetail) {
            Spacer(modifier = Modifier.height(8.dp))
            PlatformText(
                text = item.createdAt.value.localizedFullTime,
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
            )
        }
        if (appearanceSettings.showActions || isDetail) {
            Spacer(modifier = Modifier.height(8.dp))
            if (isDetail) {
                CompositionLocalProvider(
                    PlatformContentColor provides PlatformTheme.colorScheme.caption,
                ) {
                    StatusActions(
                        item.actions,
                        onShare = {
                            PlatformShare.shareText(
                                context = platformContext,
                                text = item.url,
                            )
                        },
                    )
                }
            } else {
                CompositionLocalProvider(
                    PlatformContentColor provides PlatformTheme.colorScheme.caption.copy(alpha = MediumAlpha),
                    PlatformTextStyle provides PlatformTheme.typography.caption,
                ) {
                    StatusActions(
                        item.actions,
                        onShare = {
                            PlatformShare.shareText(
                                context = platformContext,
                                text = item.url,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMediasComponent(
    appearanceSettings: ComponentAppearance,
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
                contentDescription = stringResource(resource = Res.string.show_media),
                modifier =
                    Modifier
                        .size(12.dp)
                        .alpha(MediumAlpha),
            )
            Spacer(modifier = Modifier.width(4.dp))
            PlatformText(
                text = stringResource(resource = Res.string.show_media),
                style = PlatformTheme.typography.caption,
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
                    shape = PlatformTheme.shapes.medium,
                ).clip(
                    shape = PlatformTheme.shapes.medium,
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
            val color =
                if (reaction.me) {
                    PlatformTheme.colorScheme.primaryContainer
                } else {
                    PlatformTheme.colorScheme.cardAlt
                }
            val borderColor =
                if (reaction.me) {
                    PlatformTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
            PlatformCard(
                shape = RoundedCornerShape(100),
                containerColor = color,
                modifier =
                    Modifier
                        .border(
                            FlareDividerDefaults.thickness,
                            color = borderColor,
                            shape = RoundedCornerShape(100),
                        ),
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
                        PlatformText(reaction.name)
                    } else {
                        EmojiImage(
                            uri = reaction.url,
                            modifier = Modifier.height(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    PlatformText(
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
    content: UiRichText,
) {
    val componentAppearance = LocalComponentAppearance.current
    var enabledTranslate by rememberSaveable("translate-$statusKey") {
        mutableStateOf(false)
    }
    var enabledTldr by rememberSaveable("tldr-$statusKey") {
        mutableStateOf(false)
    }
    Row {
        PlatformTextButton(
            onClick = {
                if (!enabledTranslate) {
                    enabledTranslate = true
                }
            },
        ) {
            PlatformText(
                text =
                    stringResource(
                        resource = Res.string.status_detail_translate,
                        Locale.current.platformLocale.displayLanguage,
                    ),
            )
        }
        if (componentAppearance.aiConfig.tldr && content.isLongText) {
            PlatformTextButton(
                onClick = {
                    if (!enabledTldr) {
                        enabledTldr = true
                    }
                },
            ) {
                PlatformText(
                    text =
                        stringResource(
                            resource = Res.string.status_detail_tldr,
                        ),
                )
            }
        }
    }
    if (enabledTldr) {
        Spacer(modifier = Modifier.height(4.dp))
        val state by producePresenter(
            "tldr_${contentWarning}_${rawContent}_${Locale.current.platformLocale.language}",
        ) {
            statusTldrPresenter(
                contentWarning = contentWarning,
                content = content,
                targetLanguage = Locale.current.platformLocale.language,
            )
        }
        state
            .onSuccess {
                PlatformText(text = it)
            }.onLoading {
                PlatformText(
                    text = "Lores ipsum dolor sit amet",
                    modifier = Modifier.placeholder(true),
                )
            }.onError {
                PlatformText(text = it.message ?: "Error")
            }
    }
    if (enabledTranslate) {
        Spacer(modifier = Modifier.height(4.dp))
        val state by producePresenter(
            "translate_${contentWarning}_${rawContent}_${Locale.current.platformLocale.language}_${componentAppearance.aiConfig.translation}",
        ) {
            statusTranslatePresenter(
                contentWarning = contentWarning,
                content = content,
                targetLanguage = Locale.current.platformLocale.language,
                useAi = componentAppearance.aiConfig.translation,
            )
        }
        state.contentWarning
            ?.onSuccess {
                PlatformText(text = it)
            }?.onLoading {
                PlatformText(
                    text = "Lores ipsum dolor sit amet",
                    modifier = Modifier.placeholder(true),
                )
            }?.onError {
                PlatformText(text = it.message ?: "Error")
            }
        state.text
            .onSuccess {
                PlatformText(text = it)
            }.onLoading {
                PlatformText(
                    text = "Lores ipsum dolor sit amet",
                    modifier = Modifier.placeholder(true),
                )
            }.onError {
                PlatformText(text = it.message ?: "Error")
            }
    }
}

@Composable
public fun StatusVisibilityComponent(
    visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type,
    modifier: Modifier = Modifier,
) {
    when (visibility) {
        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Globe,
                contentDescription = stringResource(resource = Res.string.mastodon_visibility_public),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.LockOpen,
                contentDescription = stringResource(resource = Res.string.mastodon_visibility_unlisted),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Lock,
                contentDescription = stringResource(resource = Res.string.mastodon_visibility_private),
                modifier = modifier,
            )

        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified ->
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.At,
                contentDescription = stringResource(resource = Res.string.mastodon_visibility_direct),
                modifier = modifier,
            )
    }
}

@Composable
internal fun StatusActions(
    items: ImmutableList<StatusAction>,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
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
                        if (action.displayItem is StatusAction.Item.More) {
                            ShareMenu(
                                onClick = {
                                    onShare.invoke()
                                    closeMenu.invoke()
                                },
                            )
                        }
                        action.actions.forEach { subActions ->
                            when (subActions) {
                                is StatusAction.Item -> {
                                    StatusActionItemMenu(subActions, closeMenu, launcher)
                                }

                                is StatusAction.AsyncActionItem -> {
                                    if (isMenuShown) {
                                        val state by subActions.flow.collectAsUiState()
                                        state
                                            .onSuccess {
                                                StatusActionItemMenu(it, closeMenu, launcher)
                                            }.onLoading {
                                                PlatformDropdownMenuItem(
                                                    text = {
                                                        PlatformText(
                                                            text = "Loading",
                                                            modifier =
                                                                Modifier.placeholder(
                                                                    true,
                                                                    color = PlatformTheme.colorScheme.cardAlt,
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
                                                                            PlatformTextStyle.current.fontSize.toDp() +
                                                                                4.dp
                                                                        },
                                                                    ).placeholder(
                                                                        true,
                                                                        color = PlatformTheme.colorScheme.cardAlt,
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
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
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
private fun PlatformDropdownMenuScope.ShareMenu(onClick: () -> Unit) {
    PlatformDropdownMenuItem(
        leadingIcon = {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.ShareNodes,
                contentDescription = stringResource(Res.string.share),
                modifier =
                    Modifier
                        .size(with(LocalDensity.current) { PlatformTextStyle.current.fontSize.toDp() + 4.dp }),
            )
        },
        text = {
            PlatformText(
                text = stringResource(Res.string.share),
            )
        },
        onClick = {
            onClick.invoke()
        },
    )
}

@Composable
private fun PlatformDropdownMenuScope.StatusActionItemMenu(
    subActions: StatusAction.Item,
    closeMenu: () -> Unit,
    launcher: UriHandler,
) {
    val color = statusActionItemColor(subActions)
    PlatformDropdownMenuItem(
        leadingIcon = {
            FAIcon(
                imageVector = subActions.icon,
                contentDescription = subActions.iconText,
                tint = color,
                modifier =
                    Modifier
                        .size(with(LocalDensity.current) { PlatformTextStyle.current.fontSize.toDp() + 4.dp }),
            )
        },
        text = {
            PlatformText(
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
            StatusAction.Item.Colorized.Color.Error -> PlatformTheme.colorScheme.error
            StatusAction.Item.Colorized.Color.ContentColor -> PlatformContentColor.current
            StatusAction.Item.Colorized.Color.PrimaryColor -> PlatformTheme.colorScheme.primary
        }
    } else {
        PlatformContentColor.current
    }

@Composable
private fun statusActionItemText(item: StatusAction.Item) =
    when (item) {
        is StatusAction.Item.Bookmark -> {
            if (item.bookmarked) {
                stringResource(resource = Res.string.bookmark_remove)
            } else {
                stringResource(resource = Res.string.bookmark_add)
            }
        }

        is StatusAction.Item.Delete -> stringResource(resource = Res.string.delete)
        is StatusAction.Item.Like -> {
            if (item.liked) {
                stringResource(resource = Res.string.unlike)
            } else {
                stringResource(resource = Res.string.like)
            }
        }

        StatusAction.Item.More -> stringResource(resource = Res.string.more)
        is StatusAction.Item.Quote -> stringResource(resource = Res.string.quote)
        is StatusAction.Item.Reaction -> {
            if (item.reacted) {
                stringResource(resource = Res.string.reaction_remove)
            } else {
                stringResource(resource = Res.string.reaction_add)
            }
        }

        is StatusAction.Item.Reply -> stringResource(resource = Res.string.reply)
        is StatusAction.Item.Report -> stringResource(resource = Res.string.report)
        is StatusAction.Item.Retweet -> {
            if (item.retweeted) {
                stringResource(resource = Res.string.retweet_remove)
            } else {
                stringResource(resource = Res.string.retweet)
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
            contentDescription = stringResource(resource = Res.string.reply_to),
            modifier =
                Modifier
                    .size(12.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.reply_to, replyHandle),
            style = PlatformTheme.typography.caption,
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
                    RichText(
                        text = it,
                    )
                    PlatformFilledTonalButton(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        onClick = {
                            expanded = !expanded
                        },
                    ) {
                        if (expanded) {
                            PlatformText(stringResource(Res.string.mastodon_item_show_less))
                        } else {
                            PlatformText(stringResource(Res.string.mastodon_item_show_more))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = expanded || contentWarning?.raw.isNullOrEmpty()) {
            Column {
                if (!content.isEmpty) {
                    RichText(
                        text = content,
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
            PlatformText(
                text = stringResource(resource = Res.string.poll_expired),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .alpha(MediumAlpha),
                style = PlatformTheme.typography.caption,
            )
        } else {
            PlatformText(
                text =
                    stringResource(
                        resource = Res.string.poll_expired_at,
                        poll.expiredAt.value.localizedFullTime,
                    ),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .alpha(MediumAlpha),
                style = PlatformTheme.typography.caption,
            )
        }
        if (poll.canVote) {
            PlatformFilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    poll.onVote.invoke(selectedOptions.toImmutableList())
                },
            ) {
                PlatformText(
                    text = stringResource(resource = Res.string.vote),
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
                    shape = PlatformTheme.shapes.small,
                )
//            .background(
//                color = PlatformTheme.colorScheme.secondaryContainer,
//                shape = PlatformTheme.shapes.small,
//            )
                .clip(
                    shape = PlatformTheme.shapes.small,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(option.percentage)
                    .background(
                        color = PlatformTheme.colorScheme.cardAlt,
                        shape = PlatformTheme.shapes.small,
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
                PlatformText(
                    text = option.title,
                    color = PlatformTheme.colorScheme.caption,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            },
            trailingContent = {
                if (canVote || selected) {
                    if (multiple) {
                        PlatformCheckbox(
                            checked = selected,
                            onCheckedChange = {
                                onClick.invoke()
                            },
                            interactionSource = mutableInteractionSource,
                            enabled = canVote,
                        )
                    } else {
                        PlatformRadioButton(
                            selected = selected,
                            onClick = onClick,
                            interactionSource = mutableInteractionSource,
                            enabled = canVote,
                        )
                    }
                } else {
                    // keep the height consist
                    PlatformRadioButton(
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
    val appearanceSettings = LocalComponentAppearance.current
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
    val appearanceSettings = LocalComponentAppearance.current
    Column(
        modifier =
            Modifier
                .border(
                    FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = PlatformTheme.shapes.medium,
                ).clip(
                    shape = PlatformTheme.shapes.medium,
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
            PlatformText(text = card.title)
            card.description?.let {
                PlatformText(
                    text = it,
                    style = PlatformTheme.typography.caption,
                    modifier =
                        Modifier
                            .alpha(MediumAlpha),
                )
            }
        }
    }
}

@Composable
private fun CompatCard(
    card: UiCard,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            Modifier
                .border(
                    FlareDividerDefaults.thickness,
                    color = FlareDividerDefaults.color,
                    shape = PlatformTheme.shapes.medium,
                ).clip(
                    shape = PlatformTheme.shapes.medium,
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
            PlatformText(
                text = card.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            card.description?.let {
                PlatformText(
                    text = it,
                    style = PlatformTheme.typography.caption,
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
