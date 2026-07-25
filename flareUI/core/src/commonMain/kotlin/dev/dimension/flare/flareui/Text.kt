package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public data class TextProps(
    public val value: String,
)

@FlareComponent
public data object TextType : WidgetType<TextProps>("Text")

@Composable
@FlareUiComposable
public fun Text(value: String) {
    EmitWidget(
        type = TextType,
        props = TextProps(value),
        content = {},
    )
}
