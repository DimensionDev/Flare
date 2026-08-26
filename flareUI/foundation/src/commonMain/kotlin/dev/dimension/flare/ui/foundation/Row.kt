@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget

public interface RowWidget : FlareWidget {
    override val children: FlareChildren

    public fun setSpacing(value: Float)

    public fun setVerticalAlignment(value: VerticalAlignment)
}

@Composable
@FlareUiComposable
public fun Row(
    modifier: FlareModifier = FlareModifier.None,
    spacing: Float = 0f,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Center,
    content: FlareContent,
) {
    require(spacing.isFinite() && spacing >= 0f) {
        "Row spacing must be a finite, non-negative value."
    }
    EmitFlareWidget(
        componentType = RowWidget::class,
        modifier = modifier,
        update = {
            set(spacing, RowWidget::setSpacing)
            set(verticalAlignment, RowWidget::setVerticalAlignment)
        },
        content = content,
    )
}
