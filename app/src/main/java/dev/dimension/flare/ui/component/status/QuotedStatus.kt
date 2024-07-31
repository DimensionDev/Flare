package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.localizedShortTime
import dev.dimension.flare.ui.theme.MediumAlpha

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun QuotedStatus(
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
                    Text(
                        text = data.createdAt.shortTime.localizedShortTime,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha),
                        maxLines = 1,
                    )
                }
            }
            HtmlText(
                element = data.content.data,
                layoutDirection = data.content.direction,
                maxLines = maxLines,
            )
        }
        if (!data.images.isEmpty() && LocalAppearanceSettings.current.showMedia) {
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
                HtmlText(
                    element = name.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodySmall,
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
