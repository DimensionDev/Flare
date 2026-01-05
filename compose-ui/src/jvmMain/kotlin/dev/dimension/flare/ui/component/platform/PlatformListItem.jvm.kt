package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal actual fun PlatformListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier,
    leadingContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    ListComponent(
        headlineContent = {
            headlineContent.invoke()
        },
        modifier =
            modifier
                .background(PlatformTheme.colorScheme.card)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            leadingContent.invoke()
        },
        supportingContent = {
            supportingContent.invoke()
        },
        trailingContent = {
            trailingContent.invoke()
        },
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
    // JVM does not have a native segmented list item, so we use the regular list item
    PlatformListItem(
        headlineContent = headlineContent,
        modifier =
            modifier
                .listCard(
                    index = index,
                    totalCount = totalCount,
                ).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                ),
        leadingContent = leadingContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
    )
}
