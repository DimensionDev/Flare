@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.resources.moko.AppleMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources
import dev.dimension.flare.ui.resources.moko.UIKitMokoResourcesRendererPlugin
import dev.dimension.flare.ui.uikit.FlareUIKitHost
import dev.dimension.flare.ui.uikit.createUIKitWidgetSystem
import platform.UIKit.UIView

/** Swift-visible owner of the shared demo's native UIKit hierarchy. */
public class FlareDemoHost {
    private val host =
        FlareUIKitHost(
            widgetSystem = createUIKitWidgetSystem(UIKitMokoResourcesRendererPlugin),
        )

    public val view: UIView
        get() = host.view

    init {
        host.setContent {
            ProvideMokoResources(AppleMokoResourceResolver) {
                FlareDemoContent()
            }
        }
    }

    public fun dispose() {
        host.dispose()
    }
}
