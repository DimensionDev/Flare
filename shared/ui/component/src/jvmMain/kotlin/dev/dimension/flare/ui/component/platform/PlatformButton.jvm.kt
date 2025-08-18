package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.SubtleButton

@Composable
internal actual fun PlatformButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal actual fun PlatformTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal actual fun PlatformFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    AccentButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal actual fun PlatformIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
        content = { content.invoke() },
    )
}
