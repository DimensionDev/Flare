package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.MenuFlyoutScope

@Composable
internal actual fun PlatformDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable PlatformDropdownMenuScope.() -> Unit,
) {
    MenuFlyout(
        visible = expanded,
        onDismissRequest = onDismissRequest,
        content = {
            content.invoke(this)
        },
        modifier = modifier,
    )
}

@Composable
internal actual fun PlatformDropdownMenuScope.PlatformDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
) {
    MenuFlyoutItem(
        onClick = onClick,
        text = text,
        icon = leadingIcon,
        trailing = trailingIcon,
        modifier = modifier,
    )
}

internal actual typealias PlatformDropdownMenuScope = MenuFlyoutScope
