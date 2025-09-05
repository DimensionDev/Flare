package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformFlyoutContainer(
    content: @Composable ((() -> Boolean) -> Unit),
    flyout: @Composable (() -> Unit),
    modifier: Modifier
) {
}