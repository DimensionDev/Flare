@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.demo

import dev.dimension.compose.nativekit.appkit.AppKitLazyLayoutRendererPlugin
import dev.dimension.compose.nativekit.appkit.AppKitNavigationOwner
import dev.dimension.compose.nativekit.appkit.AppKitNavigationRendererPlugin
import dev.dimension.compose.nativekit.appkit.NativeKitAppKitHost
import dev.dimension.compose.nativekit.appkit.createAppKitWidgetSystem
import dev.dimension.compose.nativekit.resources.moko.AppKitMokoResourcesRendererPlugin
import dev.dimension.compose.nativekit.resources.moko.AppleMokoResourceResolver
import dev.dimension.compose.nativekit.resources.moko.ProvideMokoResources
import platform.AppKit.NSView
import platform.AppKit.NSViewController

/** Swift-visible owner of the shared demo's native AppKit hierarchy. */
public class ComposeNativeKitDemoHost {
    private val controller = NSViewController()
    private val host =
        NativeKitAppKitHost(
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
                NativeKitDemoContent()
            }
        }
    }

    public fun dispose() {
        host.dispose()
    }
}
