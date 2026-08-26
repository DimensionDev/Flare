@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.resources.moko

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.appkit.AbstractAppKitWidget
import dev.dimension.flare.ui.appkit.AppKitBackend
import platform.AppKit.NSImageScaleProportionallyUpOrDown
import platform.AppKit.NSImageView

/** Installs [ResourceImage] for the AppKit backend. */
public object AppKitMokoResourcesRendererPlugin : FlareRendererPlugin<AppKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AppKitBackend>) {
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
    override fun setImage(value: FlareImage) {
        view.image = value.nsImage
    }

    override fun setContentDescription(value: String?) {
        view.toolTip = value
    }
}
