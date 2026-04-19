package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Composable
public fun AdaptiveCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    respectTimelineMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
    val displayMode = LocalAppearanceSettings.current.timelineDisplayMode
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
