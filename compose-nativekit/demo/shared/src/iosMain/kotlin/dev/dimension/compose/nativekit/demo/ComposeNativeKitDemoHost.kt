@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.demo

import dev.dimension.compose.nativekit.navigation.UIKitNavigationOwner
import dev.dimension.compose.nativekit.navigation.UIKitNavigationRendererPlugin
import dev.dimension.compose.nativekit.resources.moko.AppleMokoResourceResolver
import dev.dimension.compose.nativekit.resources.moko.ProvideMokoResources
import dev.dimension.compose.nativekit.resources.moko.UIKitMokoResourcesRendererPlugin
import dev.dimension.compose.nativekit.uikit.NativeKitUIKitHost
import dev.dimension.compose.nativekit.uikit.UIKitLazyLayoutRendererPlugin
import dev.dimension.compose.nativekit.uikit.createUIKitWidgetSystem
import platform.UIKit.UIView
import platform.UIKit.UIViewController

/** Swift-visible owner of the shared demo's native UIKit hierarchy. */
public class ComposeNativeKitDemoHost {
    private val controller = UIViewController()
    private val host =
        NativeKitUIKitHost(
            widgetSystem =
                createUIKitWidgetSystem(
                    UIKitMokoResourcesRendererPlugin,
                    UIKitLazyLayoutRendererPlugin,
                    UIKitNavigationRendererPlugin,
                ),
            nativeControllerOwner = UIKitNavigationOwner(controller),
        )

    public val view: UIView
        get() = host.view

    public val viewController: UIViewController
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
