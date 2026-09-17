package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.linearColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformLinearProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.linearColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?,
) {
    ContainedLoadingIndicator(
        progress = progress,
        modifier = modifier,
        indicatorColor = color ?: ProgressIndicatorDefaults.circularColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformCircularProgressIndicator(
    modifier: Modifier,
    color: Color?,
) {
    LoadingIndicator(
        modifier = modifier,
        color = color ?: ProgressIndicatorDefaults.circularColor,
    )
}
