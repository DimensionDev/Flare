@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.appkit.FlareAppKitHost
import dev.dimension.flare.ui.appkit.createAppKitWidgetSystem
import dev.dimension.flare.ui.resources.moko.AppKitMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.AppleMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources
import platform.AppKit.NSView

/** Swift-visible owner of the shared demo's native AppKit hierarchy. */
public class FlareDemoHost {
    private val host =
        FlareAppKitHost(
            widgetSystem = createAppKitWidgetSystem(AppKitMokoResourcesRendererPlugin),
        )

    public val view: NSView
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
