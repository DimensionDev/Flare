@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.appkit.AppKitLazyLayoutRendererPlugin
import dev.dimension.flare.ui.appkit.AppKitNavigationOwner
import dev.dimension.flare.ui.appkit.AppKitNavigationRendererPlugin
import dev.dimension.flare.ui.appkit.FlareAppKitHost
import dev.dimension.flare.ui.appkit.createAppKitWidgetSystem
import dev.dimension.flare.ui.resources.moko.AppKitMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.AppleMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources
import platform.AppKit.NSView
import platform.AppKit.NSViewController

/** Swift-visible owner of the shared demo's native AppKit hierarchy. */
public class FlareDemoHost {
    private val controller = NSViewController()
    private val host =
        FlareAppKitHost(
            widgetSystem =
                createAppKitWidgetSystem(
                    AppKitMokoResourcesRendererPlugin,
                    AppKitLazyLayoutRendererPlugin,
                    AppKitNavigationRendererPlugin,
                ),
            nativeControllerOwner = AppKitNavigationOwner(controller),
        )

    public val view: NSView
        get() = host.view

    public val viewController: NSViewController
        get() = controller

    init {
        controller.view = host.view
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
