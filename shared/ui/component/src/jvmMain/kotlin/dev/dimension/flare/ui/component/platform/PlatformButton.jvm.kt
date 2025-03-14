package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.konyaco.fluent.component.AccentButton
import com.konyaco.fluent.component.SubtleButton

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
