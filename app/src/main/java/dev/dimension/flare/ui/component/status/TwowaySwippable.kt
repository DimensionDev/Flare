package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalSwipeToDismissBox(
    state: SwipeToDismissBoxState,
    backgroundContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = backgroundContent,
            modifier = modifier,
            content = {
                content.invoke()
            },
            enableDismissFromStartToEnd = enableDismissFromStartToEnd,
            enableDismissFromEndToStart = enableDismissFromEndToStart,
        )
    } else {
        Box(
            modifier = modifier,
        ) {
            content.invoke()
        }
    }
}
