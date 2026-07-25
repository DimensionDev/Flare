package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public data class IconProps(
    public val image: FlareImageResource,
    public val contentDescription: FlareText?,
)

@FlareComponent
public data object IconType : WidgetType<IconProps>("Icon")

@Composable
@FlareUiComposable
public fun Icon(
    image: FlareImageResource,
    contentDescription: String? = null,
) {
    EmitIcon(
        image = image,
        contentDescription = contentDescription?.let(FlareText::literal),
    )
}

@Composable
@FlareUiComposable
public fun Icon(
    image: FlareImageResource,
    contentDescription: FlareStringResource,
) {
    EmitIcon(
        image = image,
        contentDescription = FlareText.resource(contentDescription),
    )
}

@Composable
@FlareUiComposable
private fun EmitIcon(
    image: FlareImageResource,
    contentDescription: FlareText?,
) {
    EmitWidget(
        type = IconType,
        props =
            IconProps(
                image = image,
                contentDescription = contentDescription,
            ),
        content = {},
    )
}
