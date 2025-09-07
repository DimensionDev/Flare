package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.slapps.cupertino.CupertinoActivityIndicator
import com.slapps.cupertino.ExperimentalCupertinoApi
import com.slapps.cupertino.theme.CupertinoColors
import com.slapps.cupertino.theme.Gray

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?
) {
    CupertinoActivityIndicator(
        modifier = modifier,
        progress = progress.invoke(),
        color = color ?: CupertinoColors.Gray,
    )
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformLinearProgressIndicator(
    modifier: Modifier,
    color: Color?
) {
    CupertinoActivityIndicator(
        modifier = modifier,
        color = color ?: CupertinoColors.Gray,
    )
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier,
    color: Color?
) {
    CupertinoActivityIndicator(
        modifier = modifier,
        progress = progress.invoke(),
        color = color ?: CupertinoColors.Gray,
    )
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformCircularProgressIndicator(
    modifier: Modifier,
    color: Color?
) {
    CupertinoActivityIndicator(
        modifier = modifier,
        color = color ?: CupertinoColors.Gray,
    )
}