package dev.dimension.flare.flareui

import androidx.compose.runtime.Composable

@FlareComponent
public data object ColumnType : WidgetType<Unit>("Column")

@Composable
@FlareUiComposable
public fun Column(content: FlareUiContent) {
    EmitWidget(
        type = ColumnType,
        props = Unit,
        content = content,
    )
}
