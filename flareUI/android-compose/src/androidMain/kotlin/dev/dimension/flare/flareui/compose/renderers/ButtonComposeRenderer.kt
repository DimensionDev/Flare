package dev.dimension.flare.flareui.compose

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import dev.dimension.flare.flareui.ButtonProps

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderButtonCompose(
    props: ButtonProps,
    children: List<ComposeTreeNode>,
) {
    Button(
        onClick = props.onClick,
        enabled = props.enabled,
    ) {
        Text(props.label)
    }
}
