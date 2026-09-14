@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.resources.moko

import androidx.compose.runtime.Composable
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitWidget

/** Renderer contract for the optional resource image primitive. */
public interface ResourceImageWidget : NativeKitWidget {
    public fun setImage(value: NativeKitImage)

    public fun setContentDescription(value: String?)
}

/** Displays an image returned by [imageResource] without changing Foundation's API. */
@Composable
@NativeKitComposable
public fun ResourceImage(
    image: NativeKitImage,
    contentDescription: String?,
    modifier: NativeKitModifier = NativeKitModifier.None,
) {
    EmitNativeKitWidget(
        componentType = ResourceImageWidget::class,
        modifier = modifier,
        update = {
            set(image, ResourceImageWidget::setImage)
            set(contentDescription, ResourceImageWidget::setContentDescription)
        },
    )
}
