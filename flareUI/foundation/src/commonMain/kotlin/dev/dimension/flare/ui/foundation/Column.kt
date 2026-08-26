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

    public fun setSpacing(value: Float)

    public fun setHorizontalAlignment(value: HorizontalAlignment)
}

@Composable
@FlareUiComposable
public fun Column(
    modifier: FlareModifier = FlareModifier.None,
    spacing: Float = 0f,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    content: FlareContent,
) {
    require(spacing.isFinite() && spacing >= 0f) {
        "Column spacing must be a finite, non-negative value."
    }
    EmitFlareWidget(
        componentType = ColumnWidget::class,
        modifier = modifier,
        update = {
            set(spacing, ColumnWidget::setSpacing)
            set(horizontalAlignment, ColumnWidget::setHorizontalAlignment)
        },
        content = content,
    )
}
