package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.linearColor,
    )
}

@Composable
internal actual fun PlatformLinearProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.linearColor,
    )
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.circularColor,
    )
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.circularColor,
    )
}
