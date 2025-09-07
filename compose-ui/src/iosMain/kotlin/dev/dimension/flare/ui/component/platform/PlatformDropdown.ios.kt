package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slapps.cupertino.CupertinoDropdownMenu
import com.slapps.cupertino.CupertinoMenuScope
import com.slapps.cupertino.ExperimentalCupertinoApi
import com.slapps.cupertino.MenuAction

internal actual interface PlatformDropdownMenuScope

private data class PlatformDropdownMenuScopeImpl(
    val delegate: CupertinoMenuScope
) : PlatformDropdownMenuScope

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable (PlatformDropdownMenuScope.() -> Unit)
) {
    CupertinoDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = {
            val scope = remember(this) { PlatformDropdownMenuScopeImpl(this) }
            content.invoke(scope)
        }
    )
}

@Composable
internal actual fun PlatformDropdownMenuScope.PlatformDropdownMenuItem(
    text: @Composable (() -> Unit),
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?
) {
    (this as PlatformDropdownMenuScopeImpl).delegate.MenuAction(
        modifier = modifier,
        title = text,
        onClick = onClick,
        icon = {
            leadingIcon?.invoke()
        },
    )
}