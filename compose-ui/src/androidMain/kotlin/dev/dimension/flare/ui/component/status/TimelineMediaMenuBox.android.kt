package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun TimelineMediaMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
    menu: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onExpandedChange(true)
                },
            ),
    ) {
        content()
        menu()
    }
}
