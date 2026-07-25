package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public data class TextProps(
    public val value: FlareText,
)

@FlareComponent
public data object TextType : WidgetType<TextProps>("Text")

@Composable
@FlareUiComposable
public fun Text(value: String) {
    EmitText(FlareText.literal(value))
}

@Composable
@FlareUiComposable
public fun Text(value: FlareStringResource) {
    EmitText(FlareText.resource(value))
}

@Composable
@FlareUiComposable
private fun EmitText(value: FlareText) {
    EmitWidget(
        type = TextType,
        props = TextProps(value),
        content = {},
    )
}
