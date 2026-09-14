@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.foundation

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitWidget

public interface TextWidget : NativeKitWidget {
    public fun setText(value: String)
}

@Composable
@NativeKitComposable
public fun Text(
    text: String,
    modifier: NativeKitModifier = NativeKitModifier.None,
) {
    EmitNativeKitWidget(
        componentType = TextWidget::class,
        modifier = modifier,
        update = {
            set(text, TextWidget::setText)
        },
    )
}
