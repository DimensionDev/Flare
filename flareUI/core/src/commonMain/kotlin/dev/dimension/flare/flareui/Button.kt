package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public data class ButtonProps(
    public val label: String,
    public val enabled: Boolean,
    public val onClick: () -> Unit,
)

@FlareComponent
public data object ButtonType : WidgetType<ButtonProps>("Button")

@Composable
@FlareUiComposable
public fun Button(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EmitWidget(
        type = ButtonType,
        props =
            ButtonProps(
                label = label,
                enabled = enabled,
                onClick = onClick,
            ),
        content = {},
    )
}
