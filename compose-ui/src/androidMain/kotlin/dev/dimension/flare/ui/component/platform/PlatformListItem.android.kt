package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.theme.segmentedShapes2

@Composable
internal actual fun PlatformListItem(
    headlineContent: @Composable (() -> Unit),
    modifier: Modifier,
    leadingContent: @Composable (() -> Unit),
    supportingContent: @Composable (() -> Unit),
    trailingContent: @Composable (() -> Unit),
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        leadingContent = leadingContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
    )
}

@Composable
internal actual fun PlatformSegmentedListItem(
    headlineContent: @Composable (() -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier,
    leadingContent: @Composable (() -> Unit),
    supportingContent: @Composable (() -> Unit),
    trailingContent: @Composable (() -> Unit),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String?,
) {
    SegmentedListItem(
        content = headlineContent,
        shapes = ListItemDefaults.segmentedShapes2(index, totalCount),
        modifier = modifier,
        leadingContent = leadingContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
    )
}
