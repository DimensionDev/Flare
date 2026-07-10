package dev.dimension.flare.ui.component.status.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiTimelineV2
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.background.Layer

@Composable
internal fun DesktopStatusShareImageContent(
    statusKey: MicroBlogKey,
    status: UiTimelineV2?,
    timelineAppearance: TimelineAppearance,
    modifier: Modifier = Modifier,
    blockInteractions: Boolean = false,
) {
    Box(
        modifier = modifier.background(FluentTheme.colors.background.mica.base),
    ) {
        Layer(
            modifier = Modifier.padding(64.dp).width(360.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 32.dp,
        ) {
            CompositionLocalProvider(
                LocalTimelineAppearance provides timelineAppearance.withSharePreviewDefaults(),
                LocalContentColor provides FluentTheme.colors.text.text.primary,
                LocalTextStyle provides LocalTextStyle.current.copy(Color.Unspecified),
            ) {
                StatusItem(
                    item = status,
                    detailStatusKey = statusKey,
                )
            }
        }
        if (blockInteractions) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
            )
        }
    }
}
