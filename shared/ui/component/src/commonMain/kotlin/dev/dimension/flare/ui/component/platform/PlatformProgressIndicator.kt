package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal expect fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
)

@Composable
internal expect fun PlatformLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color? = null,
)

@Composable
internal expect fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
)

@Composable
internal expect fun PlatformCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color? = null,
)
