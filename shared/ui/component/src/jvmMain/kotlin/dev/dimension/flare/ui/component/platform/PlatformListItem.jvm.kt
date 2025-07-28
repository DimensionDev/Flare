package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.component.status.ListComponent

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
        modifier = modifier,
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
