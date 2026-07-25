package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public data class ButtonProps(
    public val label: FlareText,
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
    EmitButton(
        label = FlareText.literal(label),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
@FlareUiComposable
public fun Button(
    label: FlareStringResource,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EmitButton(
        label = FlareText.resource(label),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
@FlareUiComposable
private fun EmitButton(
    label: FlareText,
    enabled: Boolean,
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
