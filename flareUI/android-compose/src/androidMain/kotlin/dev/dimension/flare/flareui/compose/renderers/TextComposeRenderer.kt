package dev.dimension.flare.flareui.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import dev.dimension.flare.flareui.TextProps

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderTextCompose(
    props: TextProps,
    children: List<ComposeTreeNode>,
) {
    Text(props.value)
}
