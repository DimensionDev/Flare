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

@Composable
internal expect fun PlatformSegmentedListItem(
    headlineContent: @Composable () -> Unit,
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit = {},
    supportingContent: @Composable () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
)
