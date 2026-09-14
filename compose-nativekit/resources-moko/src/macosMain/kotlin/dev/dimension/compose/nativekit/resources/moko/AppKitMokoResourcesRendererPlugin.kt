@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.resources.moko

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.appkit.AbstractAppKitWidget
import dev.dimension.compose.nativekit.appkit.AppKitBackend
import platform.AppKit.NSImageScaleProportionallyUpOrDown
import platform.AppKit.NSImageView

/** Installs [ResourceImage] for the AppKit backend. */
public object AppKitMokoResourcesRendererPlugin : NativeKitRendererPlugin<AppKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AppKitBackend>) {
        registrar.register(ResourceImageWidget::class) { _ ->
            AppKitResourceImageWidget()
        }
    }
}

private class AppKitResourceImageWidget :
    AbstractAppKitWidget<NSImageView>(
        NSImageView().apply {
            imageScaling = NSImageScaleProportionallyUpOrDown
        },
    ),
    ResourceImageWidget {
    override fun setImage(value: NativeKitImage) {
        view.image = value.nsImage
    }

    override fun setContentDescription(value: String?) {
        view.toolTip = value
    }
}
