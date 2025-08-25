package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.component.FlareDropdownMenu

@Composable
internal actual fun PlatformDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable PlatformDropdownMenuScope.() -> Unit,
) {
    FlareDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
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
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

internal actual typealias PlatformDropdownMenuScope = ColumnScope
