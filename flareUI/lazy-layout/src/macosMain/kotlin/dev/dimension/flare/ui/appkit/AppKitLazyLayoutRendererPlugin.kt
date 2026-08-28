@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.lazy.LazyCollectionWidget

/** Adaptive NSScrollView renderer for Flare lazy collections. */
public object AppKitLazyLayoutRendererPlugin : FlareRendererPlugin<AppKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AppKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            AppKitAdaptiveLazyCollectionWidget()
        }
    }
}
