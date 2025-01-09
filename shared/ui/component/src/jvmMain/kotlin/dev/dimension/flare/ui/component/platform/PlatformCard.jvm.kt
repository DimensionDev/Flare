package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.surface.Card

@Composable
internal actual fun PlatformCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    shape: Shape?,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Card(
            modifier = modifier,
            content = content,
            shape = shape ?: FluentTheme.shapes.overlay,
        )
    } else {
        Card(
            modifier = modifier,
            content = content,
            shape = shape ?: FluentTheme.shapes.overlay,
            onClick = { onClick.invoke() },
        )
    }
}
