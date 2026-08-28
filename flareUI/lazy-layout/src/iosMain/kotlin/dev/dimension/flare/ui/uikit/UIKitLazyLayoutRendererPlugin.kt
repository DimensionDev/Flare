@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.lazy.LazyCollectionWidget

/** Adaptive UIScrollView renderer for Flare lazy collections. */
public object UIKitLazyLayoutRendererPlugin : FlareRendererPlugin<UIKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            UIKitAdaptiveLazyCollectionWidget()
        }
    }
}
