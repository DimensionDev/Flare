@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.appkit

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.lazy.LazyCollectionWidget

/** Adaptive NSScrollView renderer for NativeKit lazy collections. */
public object AppKitLazyLayoutRendererPlugin : NativeKitRendererPlugin<AppKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AppKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            AppKitLazyCollectionWidget()
        }
    }
}
