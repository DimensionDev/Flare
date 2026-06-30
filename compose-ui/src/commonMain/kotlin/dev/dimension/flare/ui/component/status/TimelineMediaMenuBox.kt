package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun TimelineMediaMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit,
    content: @Composable () -> Unit,
)
