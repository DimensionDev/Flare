@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.resources.moko

import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.uikit.UIKitBackend
import dev.dimension.compose.nativekit.uikit.UIKitNativeWidget
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import kotlin.test.Test
import kotlin.test.assertEquals

public class UIKitMokoResourcesRendererTest {
    @Test
    public fun resourceImagesPreserveAspectRatioWhenTheirLayoutBoundsGrow() {
        val widget =
            NativeKitWidgetSystem<UIKitBackend>(UIKitMokoResourcesRendererPlugin)
                .create(
                    backend = UIKitBackend,
                    componentType = ResourceImageWidget::class,
                )
        val imageView = (widget as UIKitNativeWidget).view as UIImageView

        assertEquals(
            UIViewContentMode.UIViewContentModeScaleAspectFit,
            imageView.contentMode,
        )
    }
}
