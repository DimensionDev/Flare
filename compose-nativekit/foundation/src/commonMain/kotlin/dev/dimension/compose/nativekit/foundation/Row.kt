@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.foundation

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitWidget

public interface RowWidget : NativeKitWidget {
    override val children: NativeKitChildren

    public fun setSpacing(value: Float)

    public fun setVerticalAlignment(value: VerticalAlignment)
}

@Composable
@NativeKitComposable
public fun Row(
    modifier: NativeKitModifier = NativeKitModifier.None,
    spacing: Float = 0f,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Center,
    content: NativeKitContent,
) {
    require(spacing.isFinite() && spacing >= 0f) {
        "Row spacing must be a finite, non-negative value."
    }
    EmitNativeKitWidget(
        componentType = RowWidget::class,
        modifier = modifier,
        update = {
            set(spacing, RowWidget::setSpacing)
            set(verticalAlignment, RowWidget::setVerticalAlignment)
        },
        content = content,
    )
}
