package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PlatformListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit = {},
    supportingContent: @Composable () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
)
