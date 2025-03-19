package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun QuotedStatus(
    data: UiTimeline.ItemContent.Status,
    modifier: Modifier = Modifier,
    maxLines: Int = 6,
    onMediaClick: (UiMedia) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    data.onClicked.invoke(
                        ClickContext(
                            launcher = uriHandler::openUri,
                        ),
                    )
                },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            data.user?.let {
                UserCompat(it) {
                    PlatformText(
                        text = data.createdAt.shortTime.localizedShortTime,
                        style = PlatformTheme.typography.caption,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha),
                        maxLines = 1,
                    )
                }
            }
            if (data.content.innerText.isNotEmpty()) {
                RichText(
                    text = data.content,
                    maxLines = maxLines,
                )
            }
        }
        if (!data.images.isEmpty() && LocalComponentAppearance.current.showMedia) {
            StatusMediaComponent(
                data = data.images,
                onMediaClick = onMediaClick,
                sensitive = data.sensitive,
            )
        }
    }
}

@Composable
internal fun UserCompat(
    user: UiUserV2,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    with(user) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarComponent(
                data = avatar,
                size = AvatarComponentDefaults.compatSize,
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RichText(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlatformText(
                    text = handle,
                    style = PlatformTheme.typography.caption,
                    modifier =
                        Modifier
                            .alpha(MediumAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing.invoke(this)
        }
    }
}
