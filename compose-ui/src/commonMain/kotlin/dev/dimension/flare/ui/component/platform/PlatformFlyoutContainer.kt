package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PlatformFlyoutContainer(
    content: @Composable (requestShowFlyout: () -> Boolean) -> Unit,
    flyout: @Composable () -> Unit,
    modifier: Modifier = Modifier,
)
