package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformFlyoutContainer(
    content: @Composable (requestShowFlyout: () -> Boolean) -> Unit,
    flyout: @Composable (() -> Unit),
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        content.invoke({ false })
    }
}
