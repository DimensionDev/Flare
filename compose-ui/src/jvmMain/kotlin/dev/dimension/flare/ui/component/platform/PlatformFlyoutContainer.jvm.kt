package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.component.FlyoutContainer
import io.github.composefluent.component.FlyoutPlacement

@Composable
internal actual fun PlatformFlyoutContainer(
    content: @Composable (requestShowFlyout: () -> Boolean) -> Unit,
    flyout: @Composable (() -> Unit),
    modifier: Modifier,
) {
    FlyoutContainer(
        flyout = {
            flyout.invoke()
        },
        content = {
            content.invoke {
                isFlyoutVisible = true
                true
            }
        },
        modifier = modifier,
        adaptivePlacement = true,
        placement = FlyoutPlacement.TopAlignedEnd,
    )
}
