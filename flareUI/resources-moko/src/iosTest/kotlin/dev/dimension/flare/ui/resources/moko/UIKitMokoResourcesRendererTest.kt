@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.resources.moko

import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.uikit.UIKitBackend
import dev.dimension.flare.ui.uikit.UIKitNativeWidget
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import kotlin.test.Test
import kotlin.test.assertEquals

public class UIKitMokoResourcesRendererTest {
    @Test
    public fun resourceImagesPreserveAspectRatioWhenTheirLayoutBoundsGrow() {
        val widget =
            FlareWidgetSystem<UIKitBackend>(UIKitMokoResourcesRendererPlugin)
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
