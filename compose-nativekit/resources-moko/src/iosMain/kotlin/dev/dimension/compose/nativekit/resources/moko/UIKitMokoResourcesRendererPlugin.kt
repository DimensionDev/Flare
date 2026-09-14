@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.resources.moko

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.uikit.AbstractUIKitWidget
import dev.dimension.compose.nativekit.uikit.UIKitBackend
import platform.Foundation.setValue
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** Installs [ResourceImage] for the UIKit backend. */
public object UIKitMokoResourcesRendererPlugin : NativeKitRendererPlugin<UIKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<UIKitBackend>) {
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
    override fun setImage(value: NativeKitImage) {
        view.image = value.uiImage
    }

    override fun setContentDescription(value: String?) {
        view.setValue(value, forKey = "accessibilityLabel")
        view.setValue(value != null, forKey = "isAccessibilityElement")
    }
}
