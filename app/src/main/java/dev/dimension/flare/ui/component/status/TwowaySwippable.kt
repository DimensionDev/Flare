package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalSwipeToDismissBox(
    state: DismissState,
    backgroundContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    directions: Set<DismissDirection> = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
    content: @Composable () -> Unit,
) {
    if (enabled) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = backgroundContent,
            modifier = modifier,
            directions = directions,
            content = {
                content.invoke()
            },
        )
    } else {
        Box(
            modifier = modifier,
        ) {
            content.invoke()
        }
    }
}
