@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.foundation

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitWidget

public interface NativeButtonWidget : NativeKitWidget {
    public fun setLabel(value: String)

    public fun setEnabled(value: Boolean)

    public fun setOnClick(value: () -> Unit)
}

@Composable
@NativeKitComposable
public fun NativeButton(
    label: String,
    modifier: NativeKitModifier = NativeKitModifier.None,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EmitNativeKitWidget(
        componentType = NativeButtonWidget::class,
        modifier = modifier,
        update = {
            set(label, NativeButtonWidget::setLabel)
            set(enabled, NativeButtonWidget::setEnabled)
            set(onClick, NativeButtonWidget::setOnClick)
        },
    )
}
