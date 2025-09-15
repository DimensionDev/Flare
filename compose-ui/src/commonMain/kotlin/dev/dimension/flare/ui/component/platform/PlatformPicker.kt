package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList

@Composable
internal expect fun PlatformPicker(
    options: ImmutableList<String>,
    onSelected: (Int) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
)
