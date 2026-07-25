package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable

@FlareComponent
public data object RowType : WidgetType<Unit>("Row")

@Composable
@FlareUiComposable
public fun Row(content: FlareUiContent) {
    EmitWidget(
        type = RowType,
        props = Unit,
        content = content,
    )
}
