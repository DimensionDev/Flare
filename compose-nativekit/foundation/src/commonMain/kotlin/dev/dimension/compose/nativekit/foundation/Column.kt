@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.foundation

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitWidget

public interface ColumnWidget : NativeKitWidget {
    override val children: NativeKitChildren

    public fun setSpacing(value: Float)

    public fun setHorizontalAlignment(value: HorizontalAlignment)
}

@Composable
@NativeKitComposable
public fun Column(
    modifier: NativeKitModifier = NativeKitModifier.None,
    spacing: Float = 0f,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    content: NativeKitContent,
) {
    require(spacing.isFinite() && spacing >= 0f) {
        "Column spacing must be a finite, non-negative value."
    }
    EmitNativeKitWidget(
        componentType = ColumnWidget::class,
        modifier = modifier,
        update = {
            set(spacing, ColumnWidget::setSpacing)
            set(horizontalAlignment, ColumnWidget::setHorizontalAlignment)
        },
        content = content,
    )
}
