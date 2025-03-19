package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.ProgressBar
import com.konyaco.fluent.component.ProgressRing

@Composable
internal actual fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    ProgressBar(
        progress = progress.invoke(),
        modifier = modifier,
        color = color ?: FluentTheme.colors.fillAccent.default,
    )
}

@Composable
internal actual fun PlatformLinearProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    ProgressBar(
        modifier = modifier,
        color = color ?: FluentTheme.colors.fillAccent.default,
    )
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    ProgressRing(
        progress = progress.invoke(),
        modifier = modifier,
        color = color ?: FluentTheme.colors.fillAccent.default,
    )
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    ProgressRing(
        modifier = modifier,
        color = color ?: FluentTheme.colors.fillAccent.default,
    )
}
