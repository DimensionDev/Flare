package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?
) {
}

@Composable
internal actual fun PlatformLinearProgressIndicator(
    modifier: Modifier,
    color: Color?
) {
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?
) {
}

@Composable
internal actual fun PlatformCircularProgressIndicator(
    modifier: Modifier,
    color: Color?
) {
}