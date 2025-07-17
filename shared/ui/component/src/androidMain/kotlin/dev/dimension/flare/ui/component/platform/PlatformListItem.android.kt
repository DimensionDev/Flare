package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
