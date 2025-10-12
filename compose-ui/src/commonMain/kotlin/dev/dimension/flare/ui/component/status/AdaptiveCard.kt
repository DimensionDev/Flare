package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun AdaptiveCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
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
