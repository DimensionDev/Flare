@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.plugin.badge.UIKitBadgeRendererPlugin
import dev.dimension.flare.ui.swiftui.FlareSwiftUIChildren
import dev.dimension.flare.ui.swiftui.FlareSwiftUIHost
import dev.dimension.flare.ui.swiftui.FlareSwiftUINodePlugin
import dev.dimension.flare.ui.swiftui.FlareSwiftUITree
import dev.dimension.flare.ui.swiftui.FlareSwiftUITreeObserver
import dev.dimension.flare.ui.swiftui.createSwiftUIWidgetSystem
import dev.dimension.flare.ui.uikit.FlareUIKitHost
import dev.dimension.flare.ui.uikit.createUIKitWidgetSystem
import platform.UIKit.UIView

/** Swift-visible owner of the shared demo's native UIKit hierarchy. */
public class FlareDemoHost {
    private val host =
        FlareUIKitHost(
            widgetSystem =
                createUIKitWidgetSystem(
                    UIKitBadgeRendererPlugin,
                ),
        )

    public val view: UIView
        get() = host.view

    init {
        host.setContent {
            FlareDemoContent()
        }
    }

    public fun dispose() {
        host.dispose()
    }
}

/** Swift-visible owner of the shared demo's live SwiftUI node tree. */
public class FlareDemoSwiftUIHost(
    plugins: List<FlareSwiftUINodePlugin>,
) {
    private val tree = FlareSwiftUITree()
    private val host =
        FlareSwiftUIHost(
            tree = tree,
            widgetSystem = createSwiftUIWidgetSystem(plugins),
        )

    public val content: FlareSwiftUIChildren
        get() = host.content

    init {
        host.setContent {
            FlareDemoContent()
        }
    }

    public fun setObserver(observer: FlareSwiftUITreeObserver?) {
        host.setObserver(observer)
    }

    public fun dispose() {
        host.dispose()
    }
}
