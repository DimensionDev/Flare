@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.uikit

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.lazy.LazyCollectionWidget

/** Adaptive UIScrollView renderer for NativeKit lazy collections. */
public object UIKitLazyLayoutRendererPlugin : NativeKitRendererPlugin<UIKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<UIKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            UIKitLazyCollectionWidget()
        }
    }
}
