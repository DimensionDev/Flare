@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.appkit

import dev.dimension.compose.nativekit.AppleHostController
import dev.dimension.compose.nativekit.LowLevelNativeKitApi
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitNativeControllerOwner
import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.ProvideNativeKitNativeControllerOwner
import platform.AppKit.NSLayoutAttributeLeading
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.CoreGraphics.CGRectMake
import kotlin.native.HiddenFromObjC

/** Direct AppKit host supplied by NativeKit Runtime. */
public class NativeKitAppKitHost private constructor(
    widgetSystem: NativeKitWidgetSystem<AppKitBackend>,
    nativeControllerOwner: NativeControllerOwnerBox,
) {
    public constructor(widgetSystem: NativeKitWidgetSystem<AppKitBackend>) :
        this(widgetSystem, NativeControllerOwnerBox(null))

    @LowLevelNativeKitApi
    public constructor(
        widgetSystem: NativeKitWidgetSystem<AppKitBackend>,
        nativeControllerOwner: NativeKitNativeControllerOwner,
    ) : this(widgetSystem, NativeControllerOwnerBox(nativeControllerOwner))

    private val nativeControllerOwner = nativeControllerOwner.value
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
    public fun setContent(content: NativeKitContent) {
        controller.setContent(hostedContent(content))
    }

    public fun dispose() {
        controller.dispose()
        hostView.onAttachmentChanged = null
    }

    private fun hostedContent(value: NativeKitContent): NativeKitContent =
        {
            ProvideNativeKitNativeControllerOwner(
                owner = nativeControllerOwner,
                content = value,
            )
        }
}

private class NativeControllerOwnerBox(
    val value: NativeKitNativeControllerOwner?,
)

private class AppKitHostView : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onAttachmentChanged: ((Boolean) -> Unit)? = null

    override fun viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        onAttachmentChanged?.invoke(window != null)
    }
}

private const val HOST_NAME: String = "NativeKitAppKitHost"
