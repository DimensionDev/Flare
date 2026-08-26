@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget

public interface ColumnWidget : FlareWidget {
    override val children: FlareChildren
}

@Composable
@FlareUiComposable
public fun Column(
    modifier: FlareModifier = FlareModifier.None,
    content: FlareContent,
) {
    EmitFlareWidget(
        componentType = ColumnWidget::class,
        modifier = modifier,
        content = content,
    )
}
