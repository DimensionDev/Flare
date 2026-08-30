@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.AppleHostController
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem
import platform.AppKit.NSLayoutAttributeLeading
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.CoreGraphics.CGRectMake
import kotlin.native.HiddenFromObjC

/** Direct AppKit host supplied by Flare Runtime. */
public class FlareAppKitHost(
    widgetSystem: FlareWidgetSystem<AppKitBackend>,
) {
    private val hostView =
        AppKitHostView().apply {
            orientation = NSUserInterfaceLayoutOrientationVertical
            alignment = NSLayoutAttributeLeading
        }
    private val controller =
        AppleHostController(
            root = AppKitChildren(hostView),
            widgetSystem = widgetSystem,
            backend = AppKitBackend,
            hostName = HOST_NAME,
        )

    public val view: NSStackView
        get() = hostView

    init {
        hostView.onAttachmentChanged = controller::attachmentChanged
    }

    @HiddenFromObjC
    public fun setContent(content: FlareContent) {
        controller.setContent(content)
    }

    public fun dispose() {
        controller.dispose()
        hostView.onAttachmentChanged = null
    }
}

private class AppKitHostView : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onAttachmentChanged: ((Boolean) -> Unit)? = null

    override fun viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        onAttachmentChanged?.invoke(window != null)
    }
}

private const val HOST_NAME: String = "FlareAppKitHost"
