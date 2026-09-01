@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.AppleHostController
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.LowLevelFlareApi
import dev.dimension.flare.ui.ProvideFlareNativeControllerOwner
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentLeading
import kotlin.native.HiddenFromObjC

/**
 * Direct UIKit host supplied by Flare Runtime.
 * Its [view] can be embedded directly in any UIKit view hierarchy.
 */
public class FlareUIKitHost private constructor(
    widgetSystem: FlareWidgetSystem<UIKitBackend>,
    nativeControllerOwner: NativeControllerOwnerBox,
) {
    public constructor(widgetSystem: FlareWidgetSystem<UIKitBackend>) :
        this(widgetSystem, NativeControllerOwnerBox(null))

    @LowLevelFlareApi
    public constructor(
        widgetSystem: FlareWidgetSystem<UIKitBackend>,
        nativeControllerOwner: FlareNativeControllerOwner,
    ) : this(widgetSystem, NativeControllerOwnerBox(nativeControllerOwner))

    private val nativeControllerOwner = nativeControllerOwner.value
    private val hostView =
        UIKitHostView().apply {
            axis = UILayoutConstraintAxisVertical
            alignment = UIStackViewAlignmentLeading
        }
    private val controller =
        AppleHostController(
            root = UIKitChildren(hostView),
            widgetSystem = widgetSystem,
            backend = UIKitBackend,
            hostName = HOST_NAME,
        )

    public val view: UIStackView
        get() = hostView

    init {
        hostView.onAttachmentChanged = controller::attachmentChanged
    }

    @HiddenFromObjC
    public fun setContent(content: FlareContent) {
        controller.setContent(hostedContent(content))
    }

    public fun dispose() {
        controller.dispose()
        hostView.onAttachmentChanged = null
    }

    private fun hostedContent(value: FlareContent): FlareContent =
        {
            ProvideFlareNativeControllerOwner(
                owner = nativeControllerOwner,
                content = value,
            )
        }
}

private class NativeControllerOwnerBox(
    val value: FlareNativeControllerOwner?,
)

private class UIKitHostView : UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onAttachmentChanged: ((Boolean) -> Unit)? = null

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        onAttachmentChanged?.invoke(window != null)
    }
}

private const val HOST_NAME: String = "FlareUIKitHost"
