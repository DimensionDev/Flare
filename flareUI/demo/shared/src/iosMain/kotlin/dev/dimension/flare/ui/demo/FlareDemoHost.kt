@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.navigation.UIKitNavigationOwner
import dev.dimension.flare.ui.navigation.UIKitNavigationRendererPlugin
import dev.dimension.flare.ui.resources.moko.AppleMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources
import dev.dimension.flare.ui.resources.moko.UIKitMokoResourcesRendererPlugin
import dev.dimension.flare.ui.uikit.FlareUIKitHost
import dev.dimension.flare.ui.uikit.UIKitLazyLayoutRendererPlugin
import dev.dimension.flare.ui.uikit.createUIKitWidgetSystem
import platform.UIKit.UIView
import platform.UIKit.UIViewController

/** Swift-visible owner of the shared demo's native UIKit hierarchy. */
public class FlareDemoHost {
    private val controller = UIViewController()
    private val host =
        FlareUIKitHost(
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
                FlareDemoContent()
            }
        }
    }

    public fun dispose() {
        host.dispose()
    }
}
