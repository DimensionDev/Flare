@file:OptIn(ExperimentalCupertinoApi::class)

package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slapps.cupertino.CupertinoButton
import com.slapps.cupertino.CupertinoButtonDefaults
import com.slapps.cupertino.CupertinoIconButton
import com.slapps.cupertino.ExperimentalCupertinoApi

@Composable
internal actual fun PlatformButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (RowScope.() -> Unit)
) {
    CupertinoButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}

@Composable
internal actual fun PlatformTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (RowScope.() -> Unit)
) {
    CupertinoButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
        colors = CupertinoButtonDefaults.plainButtonColors()
    )
}

@Composable
internal actual fun PlatformFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (RowScope.() -> Unit)
) {
    CupertinoButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
        colors = CupertinoButtonDefaults.tintedButtonColors()
    )
}

@Composable
internal actual fun PlatformIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (() -> Unit)
) {
    CupertinoIconButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}