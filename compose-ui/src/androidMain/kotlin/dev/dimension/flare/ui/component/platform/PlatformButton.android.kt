package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.ElevatedButton(onClick = onClick, modifier = modifier, content = content, elevation = null)
}

@Composable
internal actual fun PlatformTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, content = content)
}

@Composable
internal actual fun PlatformFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier, content = content)
}

@Composable
internal actual fun PlatformIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, content = content)
}
