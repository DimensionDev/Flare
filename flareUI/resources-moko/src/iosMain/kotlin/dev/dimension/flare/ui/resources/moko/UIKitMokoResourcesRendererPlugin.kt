@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.resources.moko

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.uikit.AbstractUIKitWidget
import dev.dimension.flare.ui.uikit.UIKitBackend
import platform.Foundation.setValue
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** Installs [ResourceImage] for the UIKit backend. */
public object UIKitMokoResourcesRendererPlugin : FlareRendererPlugin<UIKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
        registrar.register(ResourceImageWidget::class) { _ ->
            UIKitResourceImageWidget()
        }
    }
}

private class UIKitResourceImageWidget :
    AbstractUIKitWidget<UIImageView>(
        UIImageView().apply {
            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
        },
    ),
    ResourceImageWidget {
    override fun setImage(value: FlareImage) {
        view.image = value.uiImage
    }

    override fun setContentDescription(value: String?) {
        view.setValue(value, forKey = "accessibilityLabel")
        view.setValue(value != null, forKey = "isAccessibilityElement")
    }
}
