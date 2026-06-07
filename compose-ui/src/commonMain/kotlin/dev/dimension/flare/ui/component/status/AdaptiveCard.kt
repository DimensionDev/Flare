package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.listCardShape
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun AdaptiveCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    respectTimelineMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
    val displayMode = LocalTimelineAppearance.current.timelineDisplayMode
    if (bigScreen) {
        PlatformCard(
            modifier =
                modifier
                    .padding(
                        horizontal = 2.dp,
                        vertical = 6.dp,
                    ),
            elevated = false,
            containerColor = PlatformTheme.colorScheme.card,
        ) {
            content.invoke()
        }
    } else if (respectTimelineMode && displayMode == TimelineDisplayMode.Plain) {
        Column(modifier = modifier.fillMaxWidth()) {
            content.invoke()
            if (totalCount <= 0 || index < totalCount - 1) {
                HorizontalDivider()
            }
        }
    } else {
        Box(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card),
        ) {
            content.invoke()
        }
    }
}

@Composable
public fun AdaptiveOutlinedCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    respectTimelineMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
    val displayMode = LocalTimelineAppearance.current.timelineDisplayMode
    if (bigScreen || displayMode != TimelineDisplayMode.Plain) {
        AdaptiveCard(
            modifier = modifier,
            index = index,
            totalCount = totalCount,
            respectTimelineMode = respectTimelineMode,
            content = content,
        )
    } else {
        Box(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card)
                    .border(
                        1.dp,
                        PlatformTheme.colorScheme.outline,
                        listCardShape(
                            index = index,
                            totalCount = totalCount,
                        ),
                    ),
        ) {
            content.invoke()
        }
    }
}
