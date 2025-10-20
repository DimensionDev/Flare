package dev.dimension.flare.ui.component.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.dm_deleted
import dev.dimension.flare.compose.ui.dm_sending
import dev.dimension.flare.compose.ui.send
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.QuotedStatus
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@Composable
public fun DMItem(
    item: UiDMItem,
    onRetry: () -> Unit,
    onUserClicked: (UiUserV2) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment =
            if (item.isFromMe) {
                Alignment.End
            } else {
                Alignment.Start
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f),
            contentAlignment =
                if (item.isFromMe) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.showSender) {
                    AvatarComponent(
                        data = item.user.avatar,
                        modifier =
                            Modifier.clickable {
                                onUserClicked.invoke(item.user)
                            },
                    )
                }
                if (item.sendState == UiDMItem.SendState.Failed) {
                    PlatformIconButton(
                        onClick = onRetry,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleExclamation,
                            contentDescription = stringResource(Res.string.send),
                            tint = PlatformTheme.colorScheme.error,
                        )
                    }
                }
                when (val message = item.content) {
                    is UiDMItem.Message.Text ->
                        RichText(
                            text = message.text,
                            modifier =
                                Modifier
                                    .background(
                                        color = PlatformTheme.colorScheme.card,
                                        shape =
                                            if (item.isFromMe) {
                                                PlatformTheme.shapes.dmShapeFromMe
                                            } else {
                                                PlatformTheme.shapes.dmShapeFromOther
                                            },
                                    ).padding(
                                        vertical = 8.dp,
                                        horizontal = 16.dp,
                                    ),
                            color = PlatformTheme.colorScheme.onCard,
                        )

                    UiDMItem.Message.Deleted ->
                        PlatformText(
                            text = stringResource(Res.string.dm_deleted),
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.onCard,
                        )

                    is UiDMItem.Message.Media ->
                        MediaItem(
                            media = message.media,
                            modifier =
                                Modifier
                                    .clip(PlatformTheme.shapes.large)
                                    .clickable {
                                        if (message.media is UiMedia.Image) {
                                            uriHandler.openUri(AppDeepLink.RawImage.invoke(message.media.url))
                                        }
                                    },
                        )

                    is UiDMItem.Message.Status ->
                        QuotedStatus(
                            message.status,
                            modifier =
                                Modifier
                                    .clip(PlatformTheme.shapes.large)
                                    .background(
                                        color =
                                            if (item.isFromMe) {
                                                PlatformTheme.colorScheme.primaryContainer
                                            } else {
                                                PlatformTheme.colorScheme.card
                                            },
                                        shape = PlatformTheme.shapes.large,
                                    ),
                        )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.showSender) {
                Spacer(modifier = Modifier.width(AvatarComponentDefaults.size))
                RichText(
                    text = item.user.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textStyle = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            }
            if (item.sendState == UiDMItem.SendState.Sending) {
                PlatformText(
                    text = stringResource(Res.string.dm_sending),
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            } else if (item.sendState == null || item.sendState != UiDMItem.SendState.Failed) {
                DateTimeText(
                    item.timestamp,
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            }
        }
    }
}

@Composable
public fun DMLoadingItem(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        PlatformText(
            text =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit." +
                    " Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            modifier =
                Modifier
                    .placeholder(true)
                    .background(
                        color = PlatformTheme.colorScheme.card,
                        shape = PlatformTheme.shapes.dmShapeFromOther,
                    ).padding(
                        vertical = 8.dp,
                        horizontal = 16.dp,
                    ),
            color = PlatformTheme.colorScheme.onCard,
        )
    }
}
